import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/services/environment', () => ({ isTauriAppPlatform: () => false }));

import { loginReadingAssistant } from '@/services/readingAssistant/client';
import {
  createReadingAssistantAnnotation,
  deleteReadingAssistantAnnotation,
  fetchReadingAssistantProgress,
  listReadingAssistantAnnotations,
  saveReadingAssistantProgress,
  updateReadingAssistantAnnotation,
} from '@/services/readingAssistant/userState';

const jsonResponse = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });

describe('Reading Assistant account-bound progress and annotations', () => {
  const baseUrl = ['http:', '//localhost:8080'].join('');

  beforeEach(async () => {
    localStorage.clear();
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse({
        code: 0,
        message: 'ok',
        data: {
          token: 'user-jwt',
          id: 11,
          username: 'alice',
          expiresAt: '2099-01-01T00:00:00Z',
        },
      }),
    );
    await loginReadingAssistant(baseUrl, 'alice', 'secret');
  });

  it('fetches and saves account-isolated CFI reading progress', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        jsonResponse({
          code: 0,
          message: 'ok',
          data: {
            bookId: 46,
            chapterIndex: 3,
            offset: 0,
            cfi: 'epubcfi(/6/6!/4/2:0)',
            updatedAt: '2026-11-08T12:00:00',
          },
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          code: 0,
          message: 'ok',
          data: {
            bookId: 46,
            chapterIndex: 4,
            offset: 0,
            cfi: 'epubcfi(/6/8!/4/2:10)',
            updatedAt: '2026-11-08T12:05:00',
          },
        }),
      );

    const loaded = await fetchReadingAssistantProgress(46);
    expect(loaded).toEqual({
      bookId: 46,
      chapterIndex: 3,
      offset: 0,
      cfi: 'epubcfi(/6/6!/4/2:0)',
      updatedAt: '2026-11-08T12:00:00',
    });

    const saved = await saveReadingAssistantProgress({
      bookId: 46,
      chapterIndex: 4,
      cfi: 'epubcfi(/6/8!/4/2:10)',
    });
    expect(saved.cfi).toBe('epubcfi(/6/8!/4/2:10)');
    expect(fetchMock).toHaveBeenLastCalledWith(
      `${baseUrl}/api/user/books/46/progress`,
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer user-jwt' }),
      }),
    );
  });

  it('creates, lists, and deletes account-bound annotations with CFI', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        jsonResponse({
          code: 0,
          message: 'ok',
          data: {
            id: 21,
            bookId: 46,
            chapterIndex: 3,
            chapterTitle: '差序格局',
            selectedText: '以己为中心',
            color: 'yellow',
            markStyle: 'highlight',
            noteContent: '核心观点',
            cfi: 'epubcfi(/6/6!/4/2:0)',
            updatedAt: '2026-11-08T12:10:00',
          },
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          code: 0,
          message: 'ok',
          data: [
            {
              id: 21,
              bookId: 46,
              chapterIndex: 3,
              chapterTitle: '差序格局',
              selectedText: '以己为中心',
              color: 'yellow',
              markStyle: 'highlight',
              noteContent: '核心观点',
              cfi: 'epubcfi(/6/6!/4/2:0)',
              updatedAt: '2026-11-08T12:10:00',
            },
          ],
        }),
      )
      .mockResolvedValueOnce(jsonResponse({ code: 0, message: 'ok', data: null }));

    const created = await createReadingAssistantAnnotation({
      bookId: 46,
      chapterIndex: 3,
      selectedText: '以己为中心',
      noteContent: '核心观点',
      cfi: 'epubcfi(/6/6!/4/2:0)',
    });
    expect(created.id).toBe(21);
    expect(created.cfi).toBe('epubcfi(/6/6!/4/2:0)');

    const list = await listReadingAssistantAnnotations(46);
    expect(list).toHaveLength(1);
    expect(list[0]?.noteContent).toBe('核心观点');

    await deleteReadingAssistantAnnotation(21);
    expect(fetchMock).toHaveBeenLastCalledWith(
      `${baseUrl}/api/user/annotations/21`,
      expect.objectContaining({ method: 'DELETE' }),
    );
  });
  it('updates annotation text with an authenticated PUT and can clear it', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse({
        code: 0,
        message: 'ok',
        data: {
          id: 21,
          bookId: 46,
          chapterIndex: 3,
          selectedText: 'Passage',
          color: 'yellow',
          markStyle: 'highlight',
          noteContent: null,
        },
      }),
    );
    const result = await updateReadingAssistantAnnotation(21, '  ');
    expect(result.noteContent).toBeNull();
    expect(fetchMock).toHaveBeenLastCalledWith(
      `${baseUrl}/api/user/annotations/21`,
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ noteContent: '' }),
        headers: expect.objectContaining({ Authorization: 'Bearer user-jwt' }),
      }),
    );
  });
});
