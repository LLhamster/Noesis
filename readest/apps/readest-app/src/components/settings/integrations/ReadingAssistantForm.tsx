import React, { useEffect, useState } from 'react';
import { useTranslation } from '@/hooks/useTranslation';
import {
  getReadingAssistantStorageTarget,
  importReadingAssistantBook,
  importBookToCloud,
  listReadingAssistantBooks,
  prepareCloudBookIndex,
  setReadingAssistantStorageTarget,
  type BookStorageTarget,
  type RemoteBook,
} from '@/services/readingAssistant/library';
import {
  clearReadingAssistantSession,
  getReadingAssistantSession,
  loginReadingAssistant,
  ReadingAssistantError,
  registerReadingAssistant,
  validateReadingAssistantSession,
  type ReadingAssistantSession,
} from '@/services/readingAssistant/client';
import SubPageHeader from '../SubPageHeader';
import { BoxedList, SectionTitle, SettingsRow, SettingsSelect, Tips } from '../primitives';

interface ReadingAssistantFormProps {
  onBack: () => void;
}

const ReadingAssistantForm: React.FC<ReadingAssistantFormProps> = ({ onBack }) => {
  const _ = useTranslation();
  const [session, setSession] = useState<ReadingAssistantSession | null>(
    getReadingAssistantSession,
  );
  const [serverUrl, setServerUrl] = useState(session?.serverUrl ?? 'http://localhost:8080');
  const [username, setUsername] = useState(session?.username ?? '');
  const [password, setPassword] = useState('');
  const [isConnecting, setIsConnecting] = useState(false);
  const [error, setError] = useState('');
  const [importStatus, setImportStatus] = useState('');
  const [failedImports, setFailedImports] = useState<string[]>([]);
  const [importingCloud, setImportingCloud] = useState(false);
  const [books, setBooks] = useState<RemoteBook[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [busyBook, setBusyBook] = useState<number | null>(null);
  const [storageTarget, setStorageTarget] = useState<BookStorageTarget>(
    getReadingAssistantStorageTarget,
  );

  const loadBooks = async (nextPage: number) => {
    setError('');
    try {
      const result = await listReadingAssistantBooks(nextPage);
      setBooks(nextPage === 0 ? result.books : (current) => [...current, ...result.books]);
      setPage(nextPage);
      setHasNext(result.hasNext);
    } catch (loadError) {
      setError(
        loadError instanceof ReadingAssistantError
          ? _(loadError.message)
          : _('Could not load the remote library.'),
      );
    }
  };

  useEffect(() => {
    if (!session) return;
    void validateReadingAssistantSession()
      .then((validated) => setSession(validated))
      .catch(() => setError(_('Could not verify the server. Your saved session is unchanged.')));
  }, []); // Validate the device-local session once when this sub-page opens.

  useEffect(() => {
    if (session) void loadBooks(0);
  }, [session?.serverUrl, session?.token]);

  const handleStorageTargetChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    const nextTarget: BookStorageTarget = event.target.value === 'gdrive' ? 'gdrive' : 'local';
    setStorageTarget(nextTarget);
    setReadingAssistantStorageTarget(nextTarget);
  };

  const handleImport = async (book: RemoteBook) => {
    setBusyBook(book.id);
    setError('');
    try {
      await importReadingAssistantBook(book, storageTarget);
    } catch (importError) {
      setError(
        importError instanceof ReadingAssistantError
          ? _(importError.message)
          : _('Could not import this book.'),
      );
    } finally {
      setBusyBook(null);
    }
  };

  const handlePrepareIndex = async (book: RemoteBook) => {
    setBusyBook(book.id);
    setError('');
    try {
      await prepareCloudBookIndex(book);
      setBooks((current) =>
        current.map((item) => (item.id === book.id ? { ...item, indexReady: true } : item)),
      );
    } catch (cause) {
      setError(
        cause instanceof ReadingAssistantError
          ? _(cause.message)
          : _('Could not prepare the AI reading index.'),
      );
    } finally {
      setBusyBook(null);
    }
  };

  const handleCloudImport = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.currentTarget.files ?? []);
    event.currentTarget.value = '';
    if (!files.length || importingCloud) return;
    setImportingCloud(true);
    setFailedImports([]);
    setError('');
    let imported = 0;
    let skipped = 0;
    const failures: string[] = [];
    try {
      // Serialize uploads so each book can finish deduplication and library registration.
      for (const [index, file] of files.entries()) {
        setImportStatus(
          _('Importing book {{current}} of {{total}}', { current: index + 1, total: files.length }),
        );
        try {
          if ((await importBookToCloud(file)) === 'exists') {
            skipped += 1;
          } else {
            imported += 1;
          }
        } catch (cause) {
          const message =
            cause instanceof ReadingAssistantError
              ? _(cause.message)
              : _('Could not import this book.');
          failures.push(`${file.name}: ${message}`);
        }
      }
      if (imported) await loadBooks(0);
      setImportStatus(
        _(
          'Cloud import complete: {{imported}} imported, {{skipped}} already in library, {{failed}} failed.',
          {
            imported,
            skipped,
            failed: failures.length,
          },
        ),
      );
      setFailedImports(failures);
    } finally {
      setImportingCloud(false);
    }
  };

  const submitAuth = async (mode: 'login' | 'register') => {
    if (!username.trim() || !password) return;
    setIsConnecting(true);
    setError('');
    try {
      const action = mode === 'register' ? registerReadingAssistant : loginReadingAssistant;
      const connected = await action(serverUrl, username.trim(), password);
      setSession(connected);
      setServerUrl(connected.serverUrl);
      setUsername(connected.username);
    } catch (connectError) {
      setError(
        connectError instanceof ReadingAssistantError
          ? _(connectError.message)
          : _('Failed to connect. Check the server URL and credentials.'),
      );
    } finally {
      setPassword('');
      setIsConnecting(false);
    }
  };

  const handleConnect = async () => submitAuth('login');
  const handleRegister = async () => submitAuth('register');

  const handleDisconnect = () => {
    clearReadingAssistantSession();
    setSession(null);
    setError('');
  };

  return (
    <div className='w-full'>
      <SubPageHeader
        parentLabel={_('Integrations')}
        currentLabel={_('Reading Assistant')}
        description={_('Connect this device to your self-hosted Reading Assistant server.')}
        onBack={onBack}
      />

      {session ? (
        <div className='space-y-5'>
          <BoxedList title={_('Connection')}>
            <SettingsRow label={_('Server')}>
              <span className='text-base-content/65 max-w-[60%] truncate text-sm'>
                {session.serverUrl}
              </span>
            </SettingsRow>
            <SettingsRow label={_('Account')}>
              <span className='text-base-content/65 text-sm'>{session.username}</span>
            </SettingsRow>
            <SettingsRow label={_('Session expires')}>
              <span className='text-base-content/65 text-sm'>
                {new Date(session.expiresAt).toLocaleString()}
              </span>
            </SettingsRow>
          </BoxedList>
          <BoxedList title={_('Remote library')}>
            <SettingsRow label={_('Import to cloud')}>
              <label className='btn btn-contrast btn-sm'>
                {importingCloud ? _('Importing...') : _('Choose EPUB files')}
                <input
                  aria-label={_('Import to cloud')}
                  type='file'
                  multiple
                  accept='.epub,application/epub+zip'
                  className='sr-only'
                  disabled={importingCloud}
                  onChange={(event) => void handleCloudImport(event)}
                />
              </label>
            </SettingsRow>
            {importStatus && (
              <p role='status' className='px-4 py-2 text-sm'>
                {importStatus}
              </p>
            )}

            {failedImports.length > 0 && (
              <ul className='text-warning list-inside list-disc px-4 py-2 text-sm break-words'>
                {failedImports.map((failure, index) => (
                  <li key={`${index}-${failure}`}>{failure}</li>
                ))}
              </ul>
            )}

            <SettingsRow
              label={_('Storage target')}
              description={_('Choose whether imported books stay local or upload to Google Drive')}
            >
              <SettingsSelect
                value={storageTarget}
                onChange={handleStorageTargetChange}
                ariaLabel={_('Storage target')}
                options={[
                  { value: 'local', label: _('Local device') },
                  { value: 'gdrive', label: _('Google Drive') },
                ]}
              />
            </SettingsRow>
            {books.length === 0 && <SettingsRow label={_('No downloadable books on this page')} />}
            {books.map((book) => (
              <SettingsRow
                key={book.id}
                label={book.title}
                description={`${book.author ?? ''} · ${book.format.toUpperCase()}`}
              >
                <button
                  type='button'
                  className='btn btn-contrast btn-sm shrink-0'
                  disabled={busyBook !== null}
                  onClick={() => void handleImport(book)}
                >
                  {busyBook === book.id ? _('Importing...') : _('Import to Readest')}
                </button>
                {book.cloudDrivePath && (
                  <button
                    type='button'
                    className='btn btn-outline btn-sm shrink-0'
                    disabled={busyBook !== null || book.indexReady}
                    onClick={() => void handlePrepareIndex(book)}
                  >
                    {book.indexReady ? _('AI ready') : _('Prepare AI index')}
                  </button>
                )}
              </SettingsRow>
            ))}
          </BoxedList>
          {hasNext && (
            <button
              type='button'
              className='btn btn-outline eink-bordered w-full'
              onClick={() => void loadBooks(page + 1)}
            >
              {_('Load more')}
            </button>
          )}
          <button
            type='button'
            className='btn btn-outline eink-bordered w-full'
            onClick={() => void loadBooks(0)}
          >
            {_('Refresh library')}
          </button>
          {error && <p className='text-warning px-1 text-sm'>{error}</p>}
          <button
            type='button'
            className='btn btn-outline w-full'
            disabled={importingCloud}
            onClick={handleDisconnect}
          >
            {_('Disconnect')}
          </button>
          <Tips>
            <li>{_('The server address and access token are stored only on this device.')}</li>
            <li>{_('Your Readest Cloud account is not changed by this connection.')}</li>
          </Tips>
        </div>
      ) : (
        <form
          className='space-y-4'
          onSubmit={(event) => {
            event.preventDefault();
            void handleConnect();
          }}
        >
          <div className='space-y-1.5'>
            <SectionTitle as='label' htmlFor='reading-assistant-url' className='block'>
              {_('Server URL')}
            </SectionTitle>
            <input
              id='reading-assistant-url'
              type='url'
              className='input eink-bordered h-11 w-full text-sm focus:outline-hidden'
              placeholder='http://localhost:8080'
              spellCheck='false'
              value={serverUrl}
              onChange={(event) => {
                setServerUrl(event.target.value);
                setError('');
              }}
              required
            />
          </div>
          <div className='space-y-1.5'>
            <SectionTitle as='label' htmlFor='reading-assistant-username' className='block'>
              {_('Username')}
            </SectionTitle>
            <input
              id='reading-assistant-username'
              type='text'
              className='input eink-bordered h-11 w-full text-sm focus:outline-hidden'
              autoComplete='username'
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              required
            />
          </div>
          <div className='space-y-1.5'>
            <SectionTitle as='label' htmlFor='reading-assistant-password' className='block'>
              {_('Password')}
            </SectionTitle>
            <input
              id='reading-assistant-password'
              type='password'
              className='input eink-bordered h-11 w-full text-sm focus:outline-hidden'
              autoComplete='current-password'
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </div>
          {error && <p className='text-error px-1 text-sm'>{error}</p>}
          <div className='space-y-2'>
            <button
              type='submit'
              className='btn btn-primary w-full'
              disabled={isConnecting || !serverUrl.trim() || !username.trim() || !password}
            >
              {isConnecting ? _('Connecting...') : _('Connect')}
            </button>
            <button
              type='button'
              className='btn btn-outline eink-bordered w-full'
              disabled={isConnecting || !serverUrl.trim() || !username.trim() || !password}
              onClick={() => void handleRegister()}
            >
              {_('Register')}
            </button>
          </div>
          <Tips>
            <li>{_('Start the Reading Assistant server before connecting.')}</li>
            <li>{_('Use an HTTPS address when connecting over the internet.')}</li>
          </Tips>
        </form>
      )}
    </div>
  );
};

export default ReadingAssistantForm;
