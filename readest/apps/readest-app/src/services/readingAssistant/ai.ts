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
}

export const askReadingAssistant = async (input: {
  bookId: number;
  chapterIndex: number;
  question: string;
  selectedText?: string;
}): Promise<ReadingAssistantAnswer> => {
  const session = getReadingAssistantSession();
  if (!session) throw new ReadingAssistantError('Please sign in again', 401);
  const fetcher = isTauriAppPlatform() ? tauriFetch : globalThis.fetch.bind(globalThis);
  const response = await fetcher(`${session.serverUrl}/api/ai/chat`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${session.token}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify(input),
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
  const data = result['data'];
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
  return { answer: answer['answer'], sources };
};
