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
}

export interface ReadingConversationPage {
  turns: ReadingConversationTurn[];
  before: number | null;
}

const requestAi = async (path: string, method = 'GET', input?: object): Promise<unknown> => {
  const session = getReadingAssistantSession();
  if (!session) throw new ReadingAssistantError('Please sign in again', 401);
  const fetcher = isTauriAppPlatform() ? tauriFetch : globalThis.fetch.bind(globalThis);
  const response = await fetcher(`${session.serverUrl}/api/ai${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${session.token}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: input === undefined ? undefined : JSON.stringify(input),
  });
  if (response.status === 401) {
    clearReadingAssistantSession();
    throw new ReadingAssistantError('Please sign in again', 401);
  }
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

export const askReadingAssistant = async (input: {
  bookId: number;
  chapterIndex: number;
  question: string;
  selectedText?: string;
  conversationId?: string;
  mode?: ReadingAnswerMode;
}): Promise<ReadingAssistantAnswer> => {
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
