import { fetch as tauriFetch } from '@tauri-apps/plugin-http';
import { isTauriAppPlatform } from '@/services/environment';

const SESSION_KEY = 'readest-reading-assistant-session';

export interface ReadingAssistantSession {
  serverUrl: string;
  token: string;
  userId: number;
  username: string;
  expiresAt: string;
}

interface ApiEnvelope {
  code: number;
  message: string;
  data: unknown;
}

export class ReadingAssistantError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
    this.name = 'ReadingAssistantError';
  }
}

const getFetch = (): typeof globalThis.fetch =>
  (isTauriAppPlatform()
    ? tauriFetch
    : globalThis.fetch.bind(globalThis)) as typeof globalThis.fetch;

export const normalizeReadingAssistantUrl = (value: string): string => {
  const normalized = value.trim().replace(/\/+$/, '');
  let url: URL;
  try {
    url = new URL(normalized);
  } catch {
    throw new ReadingAssistantError('Server URL must start with http:// or https://');
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new ReadingAssistantError('Server URL must start with http:// or https://');
  }
  return normalized;
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

const parseEnvelope = (value: unknown): ApiEnvelope => {
  if (
    !isRecord(value) ||
    typeof value['code'] !== 'number' ||
    typeof value['message'] !== 'string' ||
    !('data' in value)
  ) {
    throw new ReadingAssistantError('The server returned an invalid response');
  }
  return { code: value['code'], message: value['message'], data: value['data'] };
};

const readEnvelope = async (response: Response): Promise<ApiEnvelope> => {
  let envelope: ApiEnvelope;
  try {
    envelope = parseEnvelope(await response.json());
  } catch (error) {
    if (error instanceof ReadingAssistantError) throw error;
    throw new ReadingAssistantError('The server returned an invalid response', response.status);
  }
  if (!response.ok || envelope.code !== 0) {
    throw new ReadingAssistantError(envelope.message || 'Request failed', response.status);
  }
  return envelope;
};

const isSession = (value: unknown): value is ReadingAssistantSession =>
  isRecord(value) &&
  typeof value['serverUrl'] === 'string' &&
  typeof value['token'] === 'string' &&
  typeof value['userId'] === 'number' &&
  typeof value['username'] === 'string' &&
  typeof value['expiresAt'] === 'string';

export const clearReadingAssistantSession = (): void => {
  try {
    globalThis.localStorage?.removeItem(SESSION_KEY);
  } catch {
    // Storage can be unavailable in privacy-restricted web views.
  }
};

const saveSession = (session: ReadingAssistantSession): void => {
  try {
    globalThis.localStorage?.setItem(SESSION_KEY, JSON.stringify(session));
  } catch {
    throw new ReadingAssistantError('Could not save the session on this device');
  }
};

export const getReadingAssistantSession = (): ReadingAssistantSession | null => {
  try {
    const raw = globalThis.localStorage?.getItem(SESSION_KEY);
    if (!raw) return null;
    const session: unknown = JSON.parse(raw);
    if (!isSession(session) || Date.parse(session.expiresAt) <= Date.now()) {
      clearReadingAssistantSession();
      return null;
    }
    return session;
  } catch {
    clearReadingAssistantSession();
    return null;
  }
};

export const loginReadingAssistant = async (
  serverUrl: string,
  username: string,
  password: string,
): Promise<ReadingAssistantSession> => {
  const normalizedUrl = normalizeReadingAssistantUrl(serverUrl);
  const response = await getFetch()(`${normalizedUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const { data } = await readEnvelope(response);
  if (
    !isRecord(data) ||
    typeof data['token'] !== 'string' ||
    typeof data['id'] !== 'number' ||
    typeof data['username'] !== 'string' ||
    typeof data['expiresAt'] !== 'string' ||
    Number.isNaN(Date.parse(data['expiresAt']))
  ) {
    throw new ReadingAssistantError('The server returned an invalid login session');
  }
  const session: ReadingAssistantSession = {
    serverUrl: normalizedUrl,
    token: data['token'],
    userId: data['id'],
    username: data['username'],
    expiresAt: data['expiresAt'],
  };
  saveSession(session);
  return session;
};

export const registerReadingAssistant = async (
  serverUrl: string,
  username: string,
  password: string,
): Promise<ReadingAssistantSession> => {
  const normalizedUrl = normalizeReadingAssistantUrl(serverUrl);
  const response = await getFetch()(`${normalizedUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const { data } = await readEnvelope(response);
  if (!isRecord(data) || typeof data['id'] !== 'number' || typeof data['username'] !== 'string') {
    throw new ReadingAssistantError('The server returned an invalid registration response');
  }
  return loginReadingAssistant(normalizedUrl, username, password);
};

export const validateReadingAssistantSession =
  async (): Promise<ReadingAssistantSession | null> => {
    const session = getReadingAssistantSession();
    if (!session) return null;
    const response = await getFetch()(`${session.serverUrl}/api/auth/session`, {
      headers: { Authorization: `Bearer ${session.token}`, Accept: 'application/json' },
    });
    if (response.status === 401) {
      clearReadingAssistantSession();
      return null;
    }
    const { data } = await readEnvelope(response);
    if (!isRecord(data) || typeof data['id'] !== 'number' || typeof data['username'] !== 'string') {
      throw new ReadingAssistantError('The server returned an invalid session');
    }
    const refreshed = { ...session, userId: data['id'], username: data['username'] };
    saveSession(refreshed);
    return refreshed;
  };
