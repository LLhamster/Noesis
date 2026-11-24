import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/services/environment', () => ({ isTauriAppPlatform: () => false }));

import {
  getReadingAssistantSession,
  loginReadingAssistant,
  registerReadingAssistant,
  validateReadingAssistantSession,
} from '@/services/readingAssistant/client';

const jsonResponse = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

describe('Reading Assistant client session', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('stores a successful login without storing the password', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        code: 0,
        message: 'ok',
        data: {
          token: 'signed-token',
          id: 7,
          username: 'reader',
          expiresAt: '2099-01-01T00:00:00Z',
        },
      }),
    );

    await loginReadingAssistant('http://localhost:8080/', 'reader', 'private-password');

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/auth/login',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(getReadingAssistantSession()).toMatchObject({
      token: 'signed-token',
      username: 'reader',
      serverUrl: 'http://localhost:8080',
    });
    const storedValues = Array.from({ length: localStorage.length }, (_, index) =>
      localStorage.getItem(localStorage.key(index) ?? ''),
    ).join('');
    expect(storedValues).not.toContain('private-password');
  });

  it('clears an expired local session', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        code: 0,
        message: 'ok',
        data: {
          token: 'expired-token',
          id: 7,
          username: 'reader',
          expiresAt: '2000-01-01T00:00:00Z',
        },
      }),
    );

    await loginReadingAssistant('http://localhost:8080', 'reader', 'secret');

    expect(getReadingAssistantSession()).toBeNull();
  });

  it('clears a session rejected by the server', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        jsonResponse({
          code: 0,
          message: 'ok',
          data: {
            token: 'signed-token',
            id: 7,
            username: 'reader',
            expiresAt: '2099-01-01T00:00:00Z',
          },
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ code: 40101, message: 'unauthorized', data: null }, 401),
      );
    await loginReadingAssistant('http://localhost:8080', 'reader', 'secret');

    await expect(validateReadingAssistantSession()).resolves.toBeNull();
    expect(getReadingAssistantSession()).toBeNull();
  });

  it('registers a new account and automatically signs in to persist the session', async () => {
    const baseUrl = ['http:', '//localhost:8080'].join('');
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        jsonResponse({
          code: 0,
          message: 'ok',
          data: { id: 9, username: 'new_reader' },
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          code: 0,
          message: 'ok',
          data: {
            token: 'new-user-token',
            id: 9,
            username: 'new_reader',
            expiresAt: '2099-01-01T00:00:00Z',
          },
        }),
      );

    const session = await registerReadingAssistant(baseUrl, 'new_reader', 'secret-pass');

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      `${baseUrl}/api/auth/register`,
      expect.objectContaining({ method: 'POST' }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      `${baseUrl}/api/auth/login`,
      expect.objectContaining({ method: 'POST' }),
    );
    expect(session.username).toBe('new_reader');
    expect(getReadingAssistantSession()?.token).toBe('new-user-token');
  });
});
