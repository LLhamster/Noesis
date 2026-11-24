import { fetch as tauriFetch } from '@tauri-apps/plugin-http';
import { isTauriAppPlatform } from '@/services/environment';
import { buildGoogleDriveProvider } from '@/services/sync/providers/gdrive/buildGoogleDriveProvider';
import { ancestorsOf, buildBookFilePath } from '@/services/sync/file/layout';
import type { FileSyncProvider } from '@/services/sync/file/provider';
import { partialMD5 } from '@/utils/md5';
import { eventDispatcher } from '@/utils/event';
import { extractCloudBookSections, readCloudImportMetadata } from './bookMetadata';
import {
  clearReadingAssistantSession,
  getReadingAssistantSession,
  ReadingAssistantError,
} from './client';

export type BookStorageTarget = 'local' | 'gdrive';

export interface RemoteBook {
  id: number;
  title: string;
  author: string | null;
  format: 'epub' | 'pdf';
  cloudDrivePath?: string;
  indexReady?: boolean;
}

export interface RemoteBookPage {
  books: RemoteBook[];
  hasNext: boolean;
}

const BOOK_MAP_KEY = 'readest-reading-assistant-books';
const STORAGE_TARGET_KEY = 'readest-reading-assistant-storage-target';

export const getReadingAssistantStorageTarget = (): BookStorageTarget => {
  try {
    const raw = localStorage.getItem(STORAGE_TARGET_KEY);
    return raw === 'gdrive' ? 'gdrive' : 'local';
  } catch {
    return 'local';
  }
};

export const setReadingAssistantStorageTarget = (target: BookStorageTarget): void => {
  try {
    localStorage.setItem(STORAGE_TARGET_KEY, target);
  } catch {
    // Ignore storage write errors in restricted web views.
  }
};

export const saveReadingAssistantBookId = (hash: string, bookId: number): void => {
  const session = getReadingAssistantSession();
  if (!session || !hash) return;
  try {
    const raw = localStorage.getItem(BOOK_MAP_KEY);
    const current: unknown = raw ? JSON.parse(raw) : {};
    const map = isRecord(current) ? current : {};
    localStorage.setItem(
      BOOK_MAP_KEY,
      JSON.stringify({
        ...map,
        [hash]: { id: bookId, serverUrl: session.serverUrl, userId: session.userId },
      }),
    );
  } catch {
    // Ignore storage write errors in restricted web views.
  }
};

export const rememberReadingAssistantBook = async (book: RemoteBook, file: File): Promise<void> => {
  const hash = await partialMD5(file);
  saveReadingAssistantBookId(hash, book.id);
};

export const getReadingAssistantBookId = (hash: string): number | null => {
  const session = getReadingAssistantSession();
  if (!session) return null;
  try {
    const raw = localStorage.getItem(BOOK_MAP_KEY);
    const map: unknown = raw ? JSON.parse(raw) : null;
    const mapped = isRecord(map) ? map[hash] : null;
    return isRecord(mapped) &&
      mapped['serverUrl'] === session.serverUrl &&
      mapped['userId'] === session.userId &&
      typeof mapped['id'] === 'number'
      ? mapped['id']
      : null;
  } catch {
    return null;
  }
};

const normalizeBookMeta = (value: string | null | undefined): string =>
  (value ?? '').trim().toLowerCase().replace(/\s+/g, ' ');

export const resolveReadingAssistantBookId = async (
  hash: string,
  title?: string | null,
  author?: string | null,
): Promise<number | null> => {
  const existing = getReadingAssistantBookId(hash);
  if (existing !== null) return existing;
  if (!getReadingAssistantSession()) return null;
  const targetTitle = normalizeBookMeta(title);
  if (!targetTitle) return null;
  const targetAuthor = normalizeBookMeta(author);

  for (let page = 0; page < 5; page += 1) {
    const result = await listReadingAssistantBooks(page);
    const match =
      result.books.find(
        (item) =>
          normalizeBookMeta(item.title) === targetTitle &&
          (!targetAuthor || !item.author || normalizeBookMeta(item.author) === targetAuthor),
      ) ?? result.books.find((item) => normalizeBookMeta(item.title) === targetTitle);
    if (match) {
      saveReadingAssistantBookId(hash, match.id);
      return match.id;
    }
    if (!result.hasNext) break;
  }
  return null;
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null;

const getFetch = (): typeof globalThis.fetch =>
  (isTauriAppPlatform()
    ? tauriFetch
    : globalThis.fetch.bind(globalThis)) as typeof globalThis.fetch;

const request = async (path: string, body?: object): Promise<Response> => {
  const session = getReadingAssistantSession();
  if (!session) throw new ReadingAssistantError('Please sign in again', 401);
  const response = await getFetch()(`${session.serverUrl}${path}`, {
    method: body ? 'POST' : 'GET',
    body: body ? JSON.stringify(body) : undefined,
    headers: {
      Authorization: `Bearer ${session.token}`,
      Accept: 'application/json',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
  });
  if (response.status === 401) {
    clearReadingAssistantSession();
    throw new ReadingAssistantError('Please sign in again', 401);
  }
  if (!response.ok)
    throw new ReadingAssistantError('Could not load the remote book', response.status);
  return response;
};

export const listReadingAssistantBooks = async (page: number): Promise<RemoteBookPage> => {
  const response = await request(`/api/client/books?page=${page}`);
  const envelope: unknown = await response.json();
  if (!isRecord(envelope) || envelope['code'] !== 0 || !isRecord(envelope['data'])) {
    throw new ReadingAssistantError('The server returned an invalid book list');
  }
  const data = envelope['data'];
  if (!Array.isArray(data['books']) || typeof data['hasNext'] !== 'boolean') {
    throw new ReadingAssistantError('The server returned an invalid book list');
  }
  const books: RemoteBook[] = data['books'].map((book: unknown) => {
    if (
      !isRecord(book) ||
      typeof book['id'] !== 'number' ||
      typeof book['title'] !== 'string' ||
      (book['author'] !== null && typeof book['author'] !== 'string') ||
      (book['format'] !== 'epub' && book['format'] !== 'pdf')
    ) {
      throw new ReadingAssistantError('The server returned an invalid book list');
    }
    return {
      id: book['id'],
      title: book['title'],
      author: book['author'],
      format: book['format'],
      ...(typeof book['cloudDrivePath'] === 'string'
        ? { cloudDrivePath: book['cloudDrivePath'], indexReady: book['indexReady'] === true }
        : {}),
    };
  });
  return { books, hasNext: data['hasNext'] };
};

export const downloadReadingAssistantBook = async (book: RemoteBook): Promise<File> => {
  const name = `${book.title.replace(/[\\/:*?"<>|\x00-\x1f]/g, '_') || `book-${book.id}`}.${book.format}`;
  if (book.cloudDrivePath) {
    const provider = await buildGoogleDriveProvider();
    const bytes = await provider?.readBinary(book.cloudDrivePath);
    if (!bytes) throw new ReadingAssistantError('Could not find this book in Google Drive.');
    return new File([bytes], name, { type: 'application/epub+zip' });
  }
  const response = await request(`/api/client/books/${book.id}/file`);
  return new File([await response.blob()], name, {
    type: book.format === 'epub' ? 'application/epub+zip' : 'application/pdf',
  });
};

export const uploadBookFileToGoogleDrive = async (file: File): Promise<string> => {
  const provider = await buildGoogleDriveProvider();
  if (!provider) {
    throw new ReadingAssistantError(
      'Connect Google Drive in Cloud Sync settings before uploading to cloud.',
    );
  }
  const hash = await partialMD5(file);
  const dirPath = `/Readest/books/${hash}`;
  await provider.ensureDir(['/Readest', '/Readest/books', dirPath]);
  const remotePath = `${dirPath}/${file.name}`;
  const bytes = await file.arrayBuffer();
  await provider.writeBinary(remotePath, bytes);
  return remotePath;
};

const publishToReadestLibrary = async (file: File): Promise<void> => {
  if (!eventDispatcher.hasListeners('import-book-files')) {
    throw new ReadingAssistantError('Open this integration from the library to import books.');
  }
  const request: { files: { file: File }[]; importedCount?: number } = { files: [{ file }] };
  await eventDispatcher.dispatch('import-book-files', request);
  if (!request.importedCount) throw new ReadingAssistantError('Could not import this book.');
};

const ensureSyncedEpub = async (
  provider: FileSyncProvider,
  file: File,
  title: string,
  bytes: ArrayBuffer,
) => {
  const hash = await partialMD5(file);
  const book = { hash, format: 'EPUB' as const, title, author: '', createdAt: 0, updatedAt: 0 };
  const path = buildBookFilePath(provider.rootPath, book);
  if (!(await provider.head(path))) {
    await provider.ensureDir(ancestorsOf(path));
    await provider.writeBinary(path, bytes, 'application/epub+zip');
  }
  return { path, hash };
};

export const importReadingAssistantBook = async (
  book: RemoteBook,
  target: BookStorageTarget = getReadingAssistantStorageTarget(),
): Promise<void> => {
  const file = await downloadReadingAssistantBook(book);
  const legacyImportId = book.cloudDrivePath?.match(
    /^\/Readest\/noesis\/([\da-f-]{36})\.epub$/i,
  )?.[1];
  if (legacyImportId && book.author) {
    const provider = await buildGoogleDriveProvider();
    if (!provider) throw new ReadingAssistantError('Connect Google Drive before importing.');
    const metadata = await readCloudImportMetadata(file);
    const { path } = await ensureSyncedEpub(
      provider,
      file,
      metadata.title,
      await file.arrayBuffer(),
    );
    await importResponse(`/api/client/books/imports/${legacyImportId}/complete`, {
      title: book.title,
      author: book.author,
      cloudDrivePath: path,
    });
  }
  await publishToReadestLibrary(file);
  await rememberReadingAssistantBook(book, file);
  if (target === 'gdrive' && !book.cloudDrivePath) {
    await uploadBookFileToGoogleDrive(file);
  }
};

const importResponse = async (path: string, body: object) => {
  const response = await request(path, body);
  const envelope: unknown = await response.json();
  if (!isRecord(envelope) || envelope['code'] !== 0 || !isRecord(envelope['data'])) {
    throw new ReadingAssistantError('Could not complete the cloud import.');
  }
  return envelope['data'];
};

export const importBookToCloud = async (file: File) => {
  const session = getReadingAssistantSession();
  if (!session) throw new ReadingAssistantError('Please sign in again', 401);
  const provider = await buildGoogleDriveProvider();
  if (!provider) throw new ReadingAssistantError('Connect Google Drive before importing.');
  const metadata = await readCloudImportMetadata(file);
  const bytes = await file.arrayBuffer();
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes));
  const sha256 = Array.from(digest, (byte) => byte.toString(16).padStart(2, '0')).join('');
  const retryKey = `noesis-cloud-import:${session.serverUrl}:${session.userId}:${sha256}`;
  const requestId = localStorage.getItem(retryKey) ?? crypto.randomUUID();
  localStorage.setItem(retryKey, requestId);

  const result = await importResponse('/api/client/books/imports/prepare', {
    ...metadata,
    sha256,
    requestId,
  });
  if (result['status'] === 'EXISTS') {
    localStorage.removeItem(retryKey);
    return 'exists';
  }
  if (result['status'] === 'NEEDS_CONFIRMATION') {
    throw new ReadingAssistantError('Check the EPUB title and author before importing.');
  }
  if (result['status'] !== 'READY' || result['importId'] !== requestId) {
    throw new ReadingAssistantError('A cloud import for this book is already in progress.');
  }

  const { path, hash } = await ensureSyncedEpub(provider, file, metadata.title, bytes);
  await publishToReadestLibrary(file);
  const completed = await importResponse(`/api/client/books/imports/${requestId}/complete`, {
    ...metadata,
    cloudDrivePath: path,
  });
  if (typeof completed['bookId'] !== 'number') {
    throw new ReadingAssistantError('Could not complete the cloud import.');
  }
  saveReadingAssistantBookId(hash, completed['bookId']);
  localStorage.removeItem(retryKey);
  return 'imported';
};

export const prepareCloudBookIndex = async (book: RemoteBook): Promise<void> => {
  if (!book.cloudDrivePath || book.indexReady) return;
  const file = await downloadReadingAssistantBook(book);
  const sections = await extractCloudBookSections(file);
  if (sections.length === 0)
    throw new ReadingAssistantError('This EPUB contains no searchable text.');
  const result = await importResponse(`/api/client/books/${book.id}/index`, { sections });
  if (result['indexReady'] !== true) {
    throw new ReadingAssistantError('Could not prepare the AI reading index.');
  }
};
