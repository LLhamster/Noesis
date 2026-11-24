import { fetch as tauriFetch } from '@tauri-apps/plugin-http';
import { isTauriAppPlatform } from '@/services/environment';
import {
  clearReadingAssistantSession,
  getReadingAssistantSession,
  ReadingAssistantError,
} from './client';

export interface AccountReadingProgress {
  bookId: number;
  chapterIndex: number;
  offset: number;
  cfi: string | null;
  updatedAt: string | null;
}

export interface AccountReadingAnnotation {
  id: number;
  bookId: number;
  chapterIndex: number;
  chapterTitle: string;
  selectedText: string;
  color: string;
  markStyle: string;
  noteContent: string | null;
  cfi: string | null;
  updatedAt: string | null;
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

const getFetch = (): typeof globalThis.fetch =>
  (isTauriAppPlatform()
    ? tauriFetch
    : globalThis.fetch.bind(globalThis)) as typeof globalThis.fetch;

const authorizedRequest = async (path: string, init: RequestInit = {}): Promise<unknown> => {
  const session = getReadingAssistantSession();
  if (!session) throw new ReadingAssistantError('Please sign in again', 401);
  const response = await getFetch()(`${session.serverUrl}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${session.token}`,
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(init.headers ?? {}),
    },
  });
  if (response.status === 401) {
    clearReadingAssistantSession();
    throw new ReadingAssistantError('Please sign in again', 401);
  }
  const envelope: unknown = await response.json();
  if (!isRecord(envelope) || typeof envelope['code'] !== 'number') {
    throw new ReadingAssistantError('The server returned an invalid response', response.status);
  }
  if (!response.ok || envelope['code'] !== 0) {
    throw new ReadingAssistantError(
      typeof envelope['message'] === 'string' ? envelope['message'] : 'Request failed',
      response.status,
    );
  }
  return envelope['data'];
};

export const fetchReadingAssistantProgress = async (
  bookId: number,
): Promise<AccountReadingProgress | null> => {
  const data = await authorizedRequest(`/api/user/books/${bookId}/progress`);
  if (!isRecord(data) || typeof data['chapterIndex'] !== 'number') {
    return null;
  }
  return {
    bookId: typeof data['bookId'] === 'number' ? data['bookId'] : bookId,
    chapterIndex: data['chapterIndex'],
    offset: typeof data['offset'] === 'number' ? data['offset'] : 0,
    cfi: typeof data['cfi'] === 'string' ? data['cfi'] : null,
    updatedAt: typeof data['updatedAt'] === 'string' ? data['updatedAt'] : null,
  };
};

export const saveReadingAssistantProgress = async (input: {
  bookId: number;
  chapterIndex: number;
  offset?: number;
  cfi?: string | null;
  anchorText?: string;
}): Promise<AccountReadingProgress> => {
  const data = await authorizedRequest(`/api/user/books/${input.bookId}/progress`, {
    method: 'POST',
    body: JSON.stringify({
      chapterIndex: input.chapterIndex,
      offset: input.offset ?? 0,
      ...(input.cfi ? { cfi: input.cfi } : {}),
      ...(input.anchorText ? { anchorText: input.anchorText } : {}),
    }),
  });
  if (!isRecord(data) || typeof data['chapterIndex'] !== 'number') {
    throw new ReadingAssistantError('The server returned an invalid progress record');
  }
  return {
    bookId: typeof data['bookId'] === 'number' ? data['bookId'] : input.bookId,
    chapterIndex: data['chapterIndex'],
    offset: typeof data['offset'] === 'number' ? data['offset'] : 0,
    cfi: typeof data['cfi'] === 'string' ? data['cfi'] : null,
    updatedAt: typeof data['updatedAt'] === 'string' ? data['updatedAt'] : null,
  };
};

const parseAnnotation = (item: unknown): AccountReadingAnnotation => {
  if (
    !isRecord(item) ||
    typeof item['id'] !== 'number' ||
    typeof item['bookId'] !== 'number' ||
    typeof item['chapterIndex'] !== 'number' ||
    typeof item['selectedText'] !== 'string'
  ) {
    throw new ReadingAssistantError('The server returned an invalid annotation record');
  }
  return {
    id: item['id'],
    bookId: item['bookId'],
    chapterIndex: item['chapterIndex'],
    chapterTitle: typeof item['chapterTitle'] === 'string' ? item['chapterTitle'] : '',
    selectedText: item['selectedText'],
    color: typeof item['color'] === 'string' ? item['color'] : 'yellow',
    markStyle: typeof item['markStyle'] === 'string' ? item['markStyle'] : 'highlight',
    noteContent: typeof item['noteContent'] === 'string' ? item['noteContent'] : null,
    cfi: typeof item['cfi'] === 'string' ? item['cfi'] : null,
    updatedAt: typeof item['updatedAt'] === 'string' ? item['updatedAt'] : null,
  };
};

export const listReadingAssistantAnnotations = async (
  bookId: number,
): Promise<AccountReadingAnnotation[]> => {
  const data = await authorizedRequest(`/api/user/books/${bookId}/annotations`);
  if (!Array.isArray(data)) {
    throw new ReadingAssistantError('The server returned an invalid annotation list');
  }
  return data.map(parseAnnotation);
};

export const createReadingAssistantAnnotation = async (input: {
  bookId: number;
  chapterIndex: number;
  selectedText: string;
  cfi?: string | null;
  color?: 'yellow' | 'green' | 'blue' | 'red';
  markStyle?: 'highlight' | 'underline' | 'wavy';
  noteContent?: string;
}): Promise<AccountReadingAnnotation> => {
  const text = input.selectedText.trim();
  const data = await authorizedRequest(
    `/api/user/books/${input.bookId}/chapters/${input.chapterIndex}/annotations`,
    {
      method: 'POST',
      body: JSON.stringify({
        selectedText: text,
        startOffset: 0,
        endOffset: Math.max(1, text.length),
        color: input.color ?? 'yellow',
        markStyle: input.markStyle ?? 'highlight',
        ...(input.noteContent?.trim() ? { noteContent: input.noteContent.trim() } : {}),
        ...(input.cfi ? { cfi: input.cfi } : {}),
      }),
    },
  );
  return parseAnnotation(data);
};

export const updateReadingAssistantAnnotation = async (
  annotationId: number,
  noteContent: string,
): Promise<AccountReadingAnnotation> => {
  const data = await authorizedRequest(`/api/user/annotations/${annotationId}`, {
    method: 'PUT',
    body: JSON.stringify({ noteContent: noteContent.trim() }),
  });
  return parseAnnotation(data);
};

export const deleteReadingAssistantAnnotation = async (annotationId: number): Promise<void> => {
  await authorizedRequest(`/api/user/annotations/${annotationId}`, {
    method: 'DELETE',
  });
};
