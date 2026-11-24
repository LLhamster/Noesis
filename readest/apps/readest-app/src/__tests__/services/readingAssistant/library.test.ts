import { beforeEach, describe, expect, it, vi } from 'vitest';

const buildGoogleDriveProviderMock = vi.hoisted(() => vi.fn());

vi.mock('@/services/readingAssistant/bookMetadata', () => ({
  readCloudImportMetadata: async () => ({ title: 'Example', author: 'Writer' }),
}));

vi.mock('@/services/environment', () => ({ isTauriAppPlatform: () => false }));
vi.mock('@/services/sync/providers/gdrive/buildGoogleDriveProvider', () => ({
  buildGoogleDriveProvider: buildGoogleDriveProviderMock,
}));

import { loginReadingAssistant } from '@/services/readingAssistant/client';
import {
  listReadingAssistantBooks,
  importBookToCloud,
  downloadReadingAssistantBook,
  importReadingAssistantBook,
  getReadingAssistantBookId,
  getReadingAssistantStorageTarget,
  resolveReadingAssistantBookId,
  setReadingAssistantStorageTarget,
} from '@/services/readingAssistant/library';
import { eventDispatcher } from '@/utils/event';
import { partialMD5 } from '@/utils/md5';

describe('Reading Assistant remote library', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    buildGoogleDriveProviderMock.mockReset();
  });

  it('skips Drive writes when the work already exists', async () => {
    const login = { token: 'token', id: 1, username: 'reader', expiresAt: '2099-01-01T00:00:00Z' };
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ code: 0, message: 'ok', data: login }))
      .mockResolvedValueOnce(Response.json({ code: 0, data: { status: 'EXISTS', bookId: 3 } }));
    await loginReadingAssistant('http://localhost:8080', 'reader', 'secret');
    const writeBinary = vi.fn();
    buildGoogleDriveProviderMock.mockResolvedValueOnce({ writeBinary });
    await expect(importBookToCloud(new File(['epub'], 'renamed.epub'))).resolves.toBe('exists');
    expect(writeBinary).not.toHaveBeenCalled();
  });

  it('reuses the uploaded Drive file when completion needs to be retried', async () => {
    const session = {
      token: 'token',
      id: 1,
      username: 'reader',
      expiresAt: '2099-01-01T00:00:00Z',
    };
    const requests: string[] = [];
    const completedPaths: string[] = [];
    let completions = 0;
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ code: 0, message: 'ok', data: session }))
      .mockImplementation(async (path, init) => {
        if (String(path).endsWith('/prepare')) {
          const { requestId } = JSON.parse(String(init?.body)) as { requestId: string };
          requests.push(requestId);
          return Response.json({ code: 0, data: { status: 'READY', importId: requestId } });
        }
        const { cloudDrivePath } = JSON.parse(String(init?.body)) as { cloudDrivePath: string };
        completedPaths.push(cloudDrivePath);
        completions += 1;
        return completions === 1
          ? Response.json({}, { status: 503 })
          : Response.json({ code: 0, data: { bookId: 3 } });
      });
    await loginReadingAssistant('http://localhost:8080', 'reader', 'secret');
    const writeBinary = vi.fn();
    const provider = {
      rootPath: '/',
      head: vi.fn().mockResolvedValueOnce(null).mockResolvedValueOnce({ size: 4 }),
      ensureDir: vi.fn(),
      writeBinary,
    };
    buildGoogleDriveProviderMock.mockResolvedValue(provider);
    const file = new File(['epub'], 'test.epub');

    const handler = vi.fn((event: CustomEvent) => {
      event.detail.importedCount = 1;
    });
    eventDispatcher.on('import-book-files', handler);
    try {
      await expect(importBookToCloud(file)).rejects.toThrow();
      await expect(importBookToCloud(file)).resolves.toBe('imported');
      const hash = await partialMD5(file);
      const path = `/Readest/books/${hash}/Example.epub`;
      expect(requests).toHaveLength(2);
      expect(requests[0]).toBe(requests[1]);
      expect(completedPaths).toEqual([path, path]);
      expect(writeBinary).toHaveBeenCalledExactlyOnceWith(
        path,
        await file.arrayBuffer(),
        'application/epub+zip',
      );
      expect(handler).toHaveBeenCalled();
      expect(getReadingAssistantBookId(hash)).toBe(3);
    } finally {
      eventDispatcher.off('import-book-files', handler);
    }
  });

  it('adds a legacy cloud book to the regular shelf and updates its Drive reference', async () => {
    const session = {
      token: 'token',
      id: 1,
      username: 'reader',
      expiresAt: '2099-01-01T00:00:00Z',
    };
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(Response.json({ code: 0, message: 'ok', data: session }))
      .mockResolvedValueOnce(Response.json({ code: 0, data: { bookId: 3 } }));
    await loginReadingAssistant('http://localhost:8080', 'reader', 'secret');
    const id = 'd857e738-a9c8-4d14-9e10-98b7cefa18a9';
    const legacyPath = `/Readest/noesis/${id}.epub`;
    const source = new Uint8Array([80, 75]).buffer;
    const provider = {
      rootPath: '/',
      readBinary: vi.fn().mockResolvedValue(source),
      head: vi.fn().mockResolvedValue(null),
      ensureDir: vi.fn(),
      writeBinary: vi.fn(),
    };
    buildGoogleDriveProviderMock.mockResolvedValue(provider);
    const handler = vi.fn((event: CustomEvent) => {
      event.detail.importedCount = 1;
    });
    eventDispatcher.on('import-book-files', handler);
    try {
      await importReadingAssistantBook({
        id: 3,
        title: 'Example',
        author: 'Writer',
        format: 'epub',
        cloudDrivePath: legacyPath,
      });
      const hash = await partialMD5(new File([source], 'Example.epub'));
      const path = `/Readest/books/${hash}/Example.epub`;
      expect(provider.readBinary).toHaveBeenCalledWith(legacyPath);
      expect(provider.writeBinary).toHaveBeenCalledExactlyOnceWith(
        path,
        source,
        'application/epub+zip',
      );
      expect(fetchMock).toHaveBeenLastCalledWith(
        `http://localhost:8080/api/client/books/imports/${id}/complete`,
        expect.objectContaining({
          body: JSON.stringify({ title: 'Example', author: 'Writer', cloudDrivePath: path }),
        }),
      );
      expect(handler).toHaveBeenCalledOnce();
      expect(getReadingAssistantBookId(hash)).toBe(3);
    } finally {
      eventDispatcher.off('import-book-files', handler);
    }
  });

  it('lists books and downloads an authenticated EPUB for Readest import', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            message: 'ok',
            data: {
              token: 'token',
              id: 1,
              username: 'reader',
              expiresAt: '2099-01-01T00:00:00Z',
            },
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            message: 'ok',
            data: {
              books: [{ id: 3, title: 'Example', author: 'Writer', format: 'epub' }],
              hasNext: false,
            },
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(new Response(new Uint8Array([80, 75]), { status: 200 }));
    await loginReadingAssistant('http://localhost:8080', 'reader', 'secret');

    const page = await listReadingAssistantBooks(0);
    const file = await downloadReadingAssistantBook(page.books[0]!);

    expect(file.name).toBe('Example.epub');
    expect(await file.arrayBuffer()).toEqual(new Uint8Array([80, 75]).buffer);
    expect(fetchMock).toHaveBeenLastCalledWith(
      'http://localhost:8080/api/client/books/3/file',
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: 'Bearer token' }),
      }),
    );
  });

  it('records the remote ID only after the local import succeeds', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            message: 'ok',
            data: { token: 'token', id: 1, username: 'reader', expiresAt: '2099-01-01T00:00:00Z' },
          }),
        ),
      )
      .mockImplementation(async () => new Response(new Uint8Array([80, 75]), { status: 200 }));
    await loginReadingAssistant('http://localhost:8080', 'reader', 'secret');
    const book = { id: 3, title: 'Example', author: null, format: 'epub' as const };
    const file = new File([new Uint8Array([80, 75])], 'Example.epub');
    const hash = await partialMD5(file);
    const handler = (event: CustomEvent) => {
      event.detail.importedCount = 0;
    };
    eventDispatcher.on('import-book-files', handler);
    try {
      await expect(importReadingAssistantBook(book)).rejects.toThrow('Could not import this book.');
      expect(getReadingAssistantBookId(hash)).toBeNull();
      eventDispatcher.off('import-book-files', handler);
      const successHandler = (event: CustomEvent) => {
        event.detail.importedCount = 1;
      };
      eventDispatcher.on('import-book-files', successHandler);
      try {
        await importReadingAssistantBook(book, 'local');
        expect(getReadingAssistantBookId(hash)).toBe(3);
        expect(buildGoogleDriveProviderMock).not.toHaveBeenCalled();
      } finally {
        eventDispatcher.off('import-book-files', successHandler);
      }
    } finally {
      eventDispatcher.off('import-book-files', handler);
    }
  });

  it('persists storage target selection and uploads book file to Google Drive when target is gdrive', async () => {
    const baseUrl = ['http:', '//localhost:8080'].join('');
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            message: 'ok',
            data: { token: 'token', id: 1, username: 'reader', expiresAt: '2099-01-01T00:00:00Z' },
          }),
        ),
      )
      .mockImplementation(async () => new Response(new Uint8Array([80, 75]), { status: 200 }));
    await loginReadingAssistant(baseUrl, 'reader', 'secret');

    expect(getReadingAssistantStorageTarget()).toBe('local');
    setReadingAssistantStorageTarget('gdrive');
    expect(getReadingAssistantStorageTarget()).toBe('gdrive');

    const ensureDir = vi.fn().mockResolvedValue(undefined);
    const writeBinary = vi.fn().mockResolvedValue(undefined);
    buildGoogleDriveProviderMock.mockResolvedValue({
      rootPath: '/',
      ensureDir,
      writeBinary,
    });

    const book = { id: 5, title: 'SharedBook', author: 'Author', format: 'epub' as const };
    const file = new File([new Uint8Array([80, 75])], 'SharedBook.epub');
    const hash = await partialMD5(file);
    const successHandler = (event: CustomEvent) => {
      event.detail.importedCount = 1;
    };
    eventDispatcher.on('import-book-files', successHandler);
    try {
      await importReadingAssistantBook(book);
      expect(getReadingAssistantBookId(hash)).toBe(5);
      expect(ensureDir).toHaveBeenCalledWith([
        '/Readest',
        '/Readest/books',
        `/Readest/books/${hash}`,
      ]);
      expect(writeBinary).toHaveBeenCalledWith(
        `/Readest/books/${hash}/SharedBook.epub`,
        new Uint8Array([80, 75]).buffer,
      );
    } finally {
      eventDispatcher.off('import-book-files', successHandler);
    }
  });

  it('auto-matches a Google Drive synced book to its backend bookId by title and author', async () => {
    const baseUrl = ['http:', '//localhost:8080'].join('');
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            message: 'ok',
            data: { token: 'token', id: 2, username: 'bob', expiresAt: '2099-01-01T00:00:00Z' },
          }),
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: 0,
            message: 'ok',
            data: {
              books: [
                { id: 44, title: '乡土中国', author: '费孝通', format: 'epub' },
                { id: 51, title: '学会提问', author: '尼尔·布朗', format: 'epub' },
              ],
              hasNext: false,
            },
          }),
        ),
      );
    await loginReadingAssistant(baseUrl, 'bob', 'secret');

    expect(getReadingAssistantBookId('gdrive-synced-hash')).toBeNull();
    const matchedId = await resolveReadingAssistantBookId(
      'gdrive-synced-hash',
      '乡土中国',
      '费孝通',
    );
    expect(matchedId).toBe(44);
    expect(getReadingAssistantBookId('gdrive-synced-hash')).toBe(44);
  });
});
