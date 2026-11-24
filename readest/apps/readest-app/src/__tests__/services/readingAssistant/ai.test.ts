import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/services/environment', () => ({ isTauriAppPlatform: () => false }));

import { loginReadingAssistant } from '@/services/readingAssistant/client';
import { askReadingAssistant } from '@/services/readingAssistant/ai';
import {
  getReadingAssistantBookId,
  rememberReadingAssistantBook,
} from '@/services/readingAssistant/library';
import { partialMD5 } from '@/utils/md5';

describe('Reading Assistant AI bridge', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('uses the authenticated remote book and returns answer with citations', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            message: 'ok',
            data: {
              token: 'signed-token',
              id: 7,
              username: 'reader',
              expiresAt: '2099-01-01T00:00:00Z',
            },
          }),
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            message: 'ok',
            data: {
              answer: 'Answer',
              sources: ['Chapter 1'],
            },
          }),
        ),
      );
    await loginReadingAssistant('http://localhost:8080', 'reader', 'secret');
    const file = new File(['book contents'], 'Example.epub');
    await rememberReadingAssistantBook(
      { id: 3, title: 'Example', author: null, format: 'epub' },
      file,
    );
    const hash = await partialMD5(file);

    const result = await askReadingAssistant({
      bookId: getReadingAssistantBookId(hash)!,
      chapterIndex: 2,
      question: 'Explain this',
      selectedText: 'selected words',
    });

    expect(result).toEqual({ answer: 'Answer', sources: ['Chapter 1'] });
    expect(fetchMock).toHaveBeenLastCalledWith(
      'http://localhost:8080/api/ai/chat',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer signed-token' }),
        body: JSON.stringify({
          bookId: 3,
          chapterIndex: 2,
          question: 'Explain this',
          selectedText: 'selected words',
        }),
      }),
    );
  });
});
