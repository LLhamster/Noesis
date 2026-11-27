import { fetch as tauriFetch } from '@tauri-apps/plugin-http';
import { isTauriAppPlatform } from '@/services/environment';
import {
  clearReadingAssistantSession,
  getReadingAssistantSession,
  ReadingAssistantError,
} from './client';

export interface ReadingAssistantAnswer {
  answer: string;
  sources: string[];
  conversationTurnId?: number;
  status?: string;
}

export interface ReadingConversation {
  id: string;
  title: string | null;
  defaultConversation: boolean;
}

export type ReadingAnswerMode = 'quick' | 'deep';

const ANSWER_MODE_KEY = 'reading-assistant-answer-mode';

export const getReadingAnswerMode = (): ReadingAnswerMode => {
  try {
    return localStorage.getItem(ANSWER_MODE_KEY) === 'quick' ? 'quick' : 'deep';
  } catch {
    return 'deep';
  }
};

export const saveReadingAnswerMode = (mode: ReadingAnswerMode) => {
  try {
    localStorage.setItem(ANSWER_MODE_KEY, mode);
  } catch {
    // The mode is still usable in this panel when storage is unavailable.
  }
};

export interface ReadingConversationTurn extends ReadingAssistantAnswer {
  id: number;
  chapterIndex: number;
  question: string;
  selectedText: string | null;
  status: string;
  mode?: ReadingAnswerMode;
  requestId?: string | null;
}

export interface ReadingConversationPage {
  turns: ReadingConversationTurn[];
  before: number | null;
}

const fetchAi = async (
  path: string,
  method: string,
  input?: object,
  accept = 'application/json',
) => {
  const session = getReadingAssistantSession();
  if (!session) throw new ReadingAssistantError('Please sign in again', 401);
  const fetcher = isTauriAppPlatform() ? tauriFetch : globalThis.fetch.bind(globalThis);
  const response = await fetcher(`${session.serverUrl}/api/ai${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${session.token}`,
      'Content-Type': 'application/json',
      Accept: accept,
    },
    body: input === undefined ? undefined : JSON.stringify(input),
  });
  if (response.status === 401) {
    clearReadingAssistantSession();
    throw new ReadingAssistantError('Please sign in again', 401);
  }
  return response;
};

const requestAi = async (path: string, method = 'GET', input?: object): Promise<unknown> => {
  const response = await fetchAi(path, method, input);
  const envelope: unknown = await response.json();
  if (typeof envelope !== 'object' || envelope === null) {
    throw new ReadingAssistantError('The server returned an invalid answer');
  }
  const result = envelope as Record<string, unknown>;
  if (!response.ok || result['code'] !== 0) {
    throw new ReadingAssistantError(
      typeof result['message'] === 'string' ? result['message'] : 'Could not get an answer',
      response.status,
    );
  }
  return result['data'];
};

const conversationPath = (bookId: number) => `/books/${bookId}/conversations`;

export const listReadingConversations = async (bookId: number): Promise<ReadingConversation[]> =>
  (await requestAi(conversationPath(bookId))) as ReadingConversation[];

export const createReadingConversation = async (bookId: number): Promise<ReadingConversation> =>
  (await requestAi(conversationPath(bookId), 'POST')) as ReadingConversation;

export const getReadingConversation = async (
  bookId: number,
  id: string,
  before?: number,
): Promise<ReadingConversationPage> =>
  (await requestAi(
    `${conversationPath(bookId)}/${encodeURIComponent(id)}/turns${before === undefined ? '' : `?before=${before}`}`,
  )) as ReadingConversationPage;

export const askReadingAssistant = async (
  input: {
    bookId: number;
    chapterIndex: number;
    question: string;
    selectedText?: string;
    conversationId?: string;
    mode?: ReadingAnswerMode;
  },
  stream?: ReadingChatStream,
): Promise<ReadingAssistantAnswer> => {
  if (stream) return streamAnswer(input, stream);
  const data = await requestAi('/chat', 'POST', input);
  if (typeof data !== 'object' || data === null) {
    throw new ReadingAssistantError('The server returned an invalid answer');
  }
  const answer = data as Record<string, unknown>;
  const sources = answer['sources'] ?? [];
  if (
    typeof answer['answer'] !== 'string' ||
    !Array.isArray(sources) ||
    !sources.every((source) => typeof source === 'string')
  ) {
    throw new ReadingAssistantError('The server returned an invalid answer');
  }
  return {
    answer: answer['answer'],
    sources,
    ...(typeof answer['conversationTurnId'] === 'number'
      ? {
          conversationTurnId: answer['conversationTurnId'],
          status: String(answer['status'] ?? 'completed'),
        }
      : {}),
  };
};

type ReadingChatStream = {
  id: string;
  onStage: (stage: string) => void;
  onDelta: (text: string) => void;
  onReset: () => void;
};

const streamPath = (id: string) => `/chat/streams/${encodeURIComponent(id)}`;

export const stopReadingAnswer = async (id: string) => {
  const response = await fetchAi(streamPath(id), 'DELETE');
  if (!response.ok) throw new ReadingAssistantError('Could not stop the answer', response.status);
};

const readStreamEvent = (block: string) => {
  const data = block
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).replace(/^ /, ''))
    .join('\n');
  return data
    ? (JSON.parse(data) as {
        requestId: string;
        type: string;
        value?: string | null;
        result?: ReadingAssistantAnswer | null;
      })
    : null;
};

const streamAnswer = async (
  input: object,
  stream: ReadingChatStream,
): Promise<ReadingAssistantAnswer> => {
  const response = await fetchAi(streamPath(stream.id), 'POST', input, 'text/event-stream');
  if (
    !response.ok ||
    !response.body ||
    !response.headers.get('content-type')?.startsWith('text/event-stream')
  ) {
    await response.body?.cancel().catch(() => {});
    throw new ReadingAssistantError('Could not get an answer', response.status);
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let pending = '';
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) throw new ReadingAssistantError('The answer connection was interrupted.');
      pending += decoder.decode(chunk.value, { stream: true });
      while (true) {
        const boundary = /\r?\n\r?\n/.exec(pending);
        if (!boundary) break;
        const event = readStreamEvent(pending.slice(0, boundary.index));
        pending = pending.slice(boundary.index + boundary[0].length);
        if (event?.requestId !== stream.id) continue;
        if (event.type === 'stage' && typeof event.value === 'string') stream.onStage(event.value);
        if (event.type === 'delta' && typeof event.value === 'string') stream.onDelta(event.value);
        if (event.type === 'reset') stream.onReset();
        if (event.type === 'terminal' && event.result) return event.result;
      }
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
};
