import { useEffect, useRef, useState } from 'react';
import { useTranslation } from '@/hooks/useTranslation';
import { useNotebookStore } from '@/store/notebookStore';
import { useBookProgress } from '@/store/readerProgressStore';
import { useReaderStore } from '@/store/readerStore';
import { askReadingAssistant, type ReadingAssistantAnswer } from '@/services/readingAssistant/ai';
import {
  getReadingAssistantSession,
  ReadingAssistantError,
} from '@/services/readingAssistant/client';
import { getReadingAssistantSelection } from '@/services/readingAssistant/selection';
import {
  createReadingAssistantAnnotation,
  deleteReadingAssistantAnnotation,
  fetchReadingAssistantProgress,
  listReadingAssistantAnnotations,
  saveReadingAssistantProgress,
  updateReadingAssistantAnnotation,
  type AccountReadingAnnotation,
} from '@/services/readingAssistant/userState';

const ReadingAssistantChat = ({ bookKey, bookId }: { bookKey: string; bookId: number }) => {
  const _ = useTranslation();
  const progress = useBookProgress(bookKey);
  const getView = useReaderStore((state) => state.getView);
  const questionContext = useNotebookStore((state) =>
    state.notebookQuestionContext?.bookKey === bookKey ? state.notebookQuestionContext : null,
  );
  const [question, setQuestion] = useState('');
  const [selectedText, setSelectedText] = useState(() => getReadingAssistantSelection(bookKey));
  const [noteDraft, setNoteDraft] = useState('');
  const [editingNote, setEditingNote] = useState<{ id: number; text: string } | null>(null);
  const [annotations, setAnnotations] = useState<AccountReadingAnnotation[]>([]);
  const [result, setResult] = useState<ReadingAssistantAnswer | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [savingNote, setSavingNote] = useState(false);
  const lastSyncedCfiRef = useRef<string | null>(null);

  useEffect(() => {
    const onSelection = (event: CustomEvent) => {
      if (event.detail?.bookKey === bookKey && typeof event.detail.text === 'string') {
        setSelectedText(event.detail.text);
      }
    };
    window.addEventListener('reading-assistant-selection', onSelection as EventListener);
    return () =>
      window.removeEventListener('reading-assistant-selection', onSelection as EventListener);
  }, [bookKey]);

  useEffect(() => {
    setAnnotations([]);
    setEditingNote(null);
    if (!getReadingAssistantSession()) return;
    let active = true;
    void fetchReadingAssistantProgress(bookId)
      .then((remoteProgress) => {
        if (active && remoteProgress?.cfi) {
          lastSyncedCfiRef.current = remoteProgress.cfi;
          getView(bookKey)?.goTo?.(remoteProgress.cfi);
        }
      })
      .catch(() => {
        // Ignore initial progress fetch errors so the panel remains usable offline.
      });
    void listReadingAssistantAnnotations(bookId)
      .then((items) => {
        if (active) setAnnotations(items);
      })
      .catch(() => {
        // Ignore initial annotation list errors.
      });
    return () => {
      active = false;
    };
  }, [bookId, bookKey]);

  useEffect(() => {
    const cfi = progress?.location ?? null;
    if (!cfi || cfi === lastSyncedCfiRef.current || !getReadingAssistantSession()) return;
    lastSyncedCfiRef.current = cfi;
    const chapterIndex = Math.max(1, (progress?.index ?? 0) + 1);
    void saveReadingAssistantProgress({ bookId, chapterIndex, cfi }).catch(() => {
      // Best-effort background progress sync to the user's account.
    });
  }, [bookId, progress?.index, progress?.location]);

  const getLiveSelection = () =>
    getView(bookKey)
      ?.renderer?.getContents()
      .map((content) => content.doc?.getSelection()?.toString().trim() ?? '')
      .filter(Boolean)
      .join('\n') ||
    selectedText ||
    '';

  const handleSaveAnnotation = async () => {
    const passage = getLiveSelection().trim();
    if (!passage) return;
    setSavingNote(true);
    setError('');
    try {
      const created = await createReadingAssistantAnnotation({
        bookId,
        chapterIndex: Math.max(1, (progress?.index ?? 0) + 1),
        selectedText: passage,
        noteContent: noteDraft.trim() || undefined,
        cfi: progress?.location ?? undefined,
      });
      setAnnotations((current) => [created, ...current]);
      setNoteDraft('');
    } catch (cause) {
      setError(
        cause instanceof ReadingAssistantError
          ? _(cause.message)
          : _('Could not save annotation to your account.'),
      );
    } finally {
      setSavingNote(false);
    }
  };

  const handleUpdateAnnotation = async () => {
    if (!editingNote || savingNote) return;
    setSavingNote(true);
    setError('');
    try {
      const updated = await updateReadingAssistantAnnotation(editingNote.id, editingNote.text);
      setAnnotations((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setEditingNote(null);
    } catch (cause) {
      setError(
        cause instanceof ReadingAssistantError
          ? _(cause.message)
          : _('Could not save annotation to your account.'),
      );
    } finally {
      setSavingNote(false);
    }
  };

  const handleDeleteAnnotation = async (annotationId: number) => {
    try {
      await deleteReadingAssistantAnnotation(annotationId);
      setAnnotations((current) => current.filter((item) => item.id !== annotationId));
    } catch (cause) {
      setError(
        cause instanceof ReadingAssistantError
          ? _(cause.message)
          : _('Could not delete annotation.'),
      );
    }
  };

  const submit = async () => {
    if (!question.trim()) return;
    setBusy(true);
    setError('');
    try {
      const passage = questionContext?.text ?? getLiveSelection();
      const answer = await askReadingAssistant({
        bookId,
        chapterIndex: questionContext?.chapterIndex ?? Math.max(1, (progress?.index ?? 0) + 1),
        question: question.trim(),
        selectedText: passage || undefined,
      });
      setResult(answer);
    } catch (cause) {
      setError(
        cause instanceof ReadingAssistantError ? _(cause.message) : _('Could not get an answer.'),
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className='eink-bordered border-base-200 bg-base-100 space-y-3 rounded-lg border p-4'>
      <h3 className='font-semibold'>{_('Reading Assistant')}</h3>
      {(questionContext?.text || selectedText) && (
        <blockquote className='border-base-300 text-base-content/70 line-clamp-3 border-s-2 ps-3 text-sm'>
          {questionContext?.text || selectedText}
        </blockquote>
      )}
      {selectedText && !questionContext && (
        <div className='flex gap-2'>
          <input
            type='text'
            aria-label={_('Personal note')}
            className='input input-sm eink-bordered flex-1 text-sm'
            value={noteDraft}
            onChange={(event) => setNoteDraft(event.target.value)}
            placeholder={_('Add personal note (optional)')}
          />
          <button
            type='button'
            className='btn btn-outline btn-sm eink-bordered shrink-0'
            disabled={savingNote}
            onClick={() => void handleSaveAnnotation()}
          >
            {savingNote ? _('Saving...') : _('Save to account')}
          </button>
        </div>
      )}
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
        className='space-y-2'
      >
        <label className='sr-only' htmlFor='reading-assistant-question'>
          {_('Ask about this book')}
        </label>
        <textarea
          id='reading-assistant-question'
          className='textarea eink-bordered w-full'
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          placeholder={_('Ask about this book')}
        />
        <button type='submit' className='btn btn-contrast' disabled={busy || !question.trim()}>
          {busy ? _('Thinking...') : _('Ask Reading Assistant')}
        </button>
      </form>
      {error && (
        <p role='alert' className='text-error text-sm'>
          {error}
        </p>
      )}
      {result && (
        <div className='space-y-2 whitespace-pre-wrap text-sm'>
          <p>{result.answer}</p>
          {result.sources.length > 0 && (
            <div>
              <h4 className='font-medium'>{_('Sources')}</h4>
              <ul className='list-inside list-disc'>
                {result.sources.map((source, index) => (
                  <li key={`${index}-${source}`}>{source}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
      {annotations.length > 0 && (
        <div className='border-base-200 space-y-2 border-t pt-2 text-sm'>
          <h4 className='font-medium'>{_('My account notes')}</h4>
          <ul className='space-y-1.5'>
            {annotations.map((item) => (
              <li
                key={item.id}
                className='border-base-200 flex items-start justify-between gap-2 rounded border p-2'
              >
                {editingNote?.id === item.id ? (
                  <div className='min-w-0 flex-1 space-y-2'>
                    <textarea
                      aria-label={_('Edit personal note')}
                      className='textarea textarea-bordered w-full'
                      value={editingNote.text}
                      maxLength={10000}
                      disabled={savingNote}
                      onChange={(event) =>
                        setEditingNote({ id: item.id, text: event.target.value })
                      }
                    />
                    <button
                      type='button'
                      className='btn btn-sm'
                      disabled={savingNote}
                      onClick={() => void handleUpdateAnnotation()}
                    >
                      {_('Save changes')}
                    </button>
                    <button
                      type='button'
                      className='btn btn-ghost btn-sm'
                      disabled={savingNote}
                      onClick={() => setEditingNote(null)}
                    >
                      {_('Cancel')}
                    </button>
                  </div>
                ) : (
                  <>
                    <button
                      type='button'
                      className='flex-1 text-left'
                      onClick={() => {
                        if (item.cfi) getView(bookKey)?.goTo?.(item.cfi);
                      }}
                    >
                      <p className='line-clamp-2 font-medium'>{item.selectedText}</p>
                      {item.noteContent && (
                        <p className='text-base-content/70 mt-0.5 text-xs'>{item.noteContent}</p>
                      )}
                    </button>
                    <button
                      type='button'
                      className='btn btn-ghost btn-xs'
                      disabled={savingNote || editingNote !== null}
                      onClick={() => setEditingNote({ id: item.id, text: item.noteContent ?? '' })}
                    >
                      {_('Edit note')}
                    </button>
                  </>
                )}
                <button
                  type='button'
                  className='btn btn-ghost btn-xs text-error'
                  aria-label={_('Delete note')}
                  disabled={savingNote || editingNote?.id === item.id}
                  onClick={() => void handleDeleteAnnotation(item.id)}
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
};

export default ReadingAssistantChat;
