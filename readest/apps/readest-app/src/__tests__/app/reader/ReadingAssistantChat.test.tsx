import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const goToMock = vi.fn();

vi.mock('@/hooks/useTranslation', () => ({ useTranslation: () => (text: string) => text }));
vi.mock('@/store/readerProgressStore', () => ({
  useBookProgress: () => ({ index: 1, location: 'epubcfi(/6/4!/4/2:0)' }),
}));
vi.mock('@/store/readerStore', () => ({
  useReaderStore: (
    selector: (state: { getView: () => { goTo: typeof goToMock } | null }) => unknown,
  ) => selector({ getView: () => ({ goTo: goToMock }) }),
}));
vi.mock('@/services/readingAssistant/selection', () => ({
  getReadingAssistantSelection: () => 'selected passage',
}));
vi.mock('@/services/readingAssistant/ai', () => ({ askReadingAssistant: vi.fn() }));
vi.mock('@/services/readingAssistant/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/services/readingAssistant/client')>();
  return {
    ...actual,
    getReadingAssistantSession: vi.fn(() => ({
      serverUrl: ['http:', '//localhost:8080'].join(''),
      token: 'jwt-token',
      userId: 7,
      username: 'reader',
      expiresAt: '2099-01-01T00:00:00Z',
    })),
  };
});
vi.mock('@/services/readingAssistant/userState', () => ({
  fetchReadingAssistantProgress: vi.fn().mockResolvedValue(null),
  saveReadingAssistantProgress: vi.fn().mockResolvedValue({
    bookId: 22,
    chapterIndex: 2,
    offset: 0,
    cfi: 'epubcfi(/6/4!/4/2:0)',
    updatedAt: null,
  }),
  listReadingAssistantAnnotations: vi.fn().mockResolvedValue([]),
  createReadingAssistantAnnotation: vi.fn(),
  deleteReadingAssistantAnnotation: vi.fn(),
  updateReadingAssistantAnnotation: vi.fn(),
}));

import ReadingAssistantChat from '@/app/reader/components/notebook/ReadingAssistantChat';
import { askReadingAssistant } from '@/services/readingAssistant/ai';
import {
  createReadingAssistantAnnotation,
  deleteReadingAssistantAnnotation,
  fetchReadingAssistantProgress,
  listReadingAssistantAnnotations,
  updateReadingAssistantAnnotation,
  saveReadingAssistantProgress,
} from '@/services/readingAssistant/userState';

describe('Reading Assistant reader panel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(() => {
    cleanup();
  });

  it('sends the current book, chapter and selection, then shows answer and sources', async () => {
    vi.mocked(askReadingAssistant).mockResolvedValue({
      answer: 'This passage describes the setting.',
      sources: ['Chapter 2'],
    });
    render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);

    fireEvent.change(screen.getByLabelText('Ask about this book'), {
      target: { value: 'What does this mean?' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Ask Reading Assistant' }));

    await waitFor(() =>
      expect(askReadingAssistant).toHaveBeenCalledWith({
        bookId: 22,
        chapterIndex: 2,
        question: 'What does this mean?',
        selectedText: 'selected passage',
      }),
    );
    expect(await screen.findByText('This passage describes the setting.')).toBeTruthy();
    expect(screen.getByText('Chapter 2')).toBeTruthy();
  });

  it('syncs CFI reading progress and saves highlighted notes to the user account', async () => {
    vi.mocked(fetchReadingAssistantProgress).mockResolvedValueOnce({
      bookId: 22,
      chapterIndex: 2,
      offset: 0,
      cfi: 'epubcfi(/6/4!/4/2:0)',
      updatedAt: '2026-11-08T12:00:00',
    });
    vi.mocked(createReadingAssistantAnnotation).mockResolvedValueOnce({
      id: 99,
      bookId: 22,
      chapterIndex: 2,
      chapterTitle: 'Chapter 2',
      selectedText: 'selected passage',
      color: 'yellow',
      markStyle: 'highlight',
      noteContent: 'My personal thought',
      cfi: 'epubcfi(/6/4!/4/2:0)',
      updatedAt: '2026-11-08T12:01:00',
    });

    render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);

    await waitFor(() => expect(fetchReadingAssistantProgress).toHaveBeenCalledWith(22));
    await waitFor(() => expect(goToMock).toHaveBeenCalledWith('epubcfi(/6/4!/4/2:0)'));
    expect(saveReadingAssistantProgress).toHaveBeenCalledWith({
      bookId: 22,
      chapterIndex: 2,
      cfi: 'epubcfi(/6/4!/4/2:0)',
    });

    fireEvent.change(screen.getByLabelText('Personal note'), {
      target: { value: 'My personal thought' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save to account' }));

    await waitFor(() =>
      expect(createReadingAssistantAnnotation).toHaveBeenCalledWith({
        bookId: 22,
        chapterIndex: 2,
        selectedText: 'selected passage',
        noteContent: 'My personal thought',
        cfi: 'epubcfi(/6/4!/4/2:0)',
      }),
    );
    expect(await screen.findByText('My personal thought')).toBeTruthy();
  });
  it('edits an account note and reloads its updated value when reopening', async () => {
    const note = {
      id: 99,
      bookId: 22,
      chapterIndex: 2,
      chapterTitle: 'Chapter 2',
      selectedText: 'selected passage',
      color: 'yellow',
      markStyle: 'highlight',
      noteContent: 'Initial thought',
      cfi: null,
      updatedAt: null,
    };
    const updated = { ...note, noteContent: 'Updated thought' };
    vi.mocked(listReadingAssistantAnnotations)
      .mockResolvedValueOnce([note])
      .mockResolvedValueOnce([updated])
      .mockResolvedValueOnce([]);
    vi.mocked(updateReadingAssistantAnnotation).mockResolvedValueOnce(updated);
    vi.mocked(deleteReadingAssistantAnnotation).mockResolvedValueOnce(undefined);
    const panel = render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Edit note' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Edit personal note' }), {
      target: { value: 'Updated thought' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Updated thought')).toBeTruthy();
    expect(updateReadingAssistantAnnotation).toHaveBeenCalledWith(99, 'Updated thought');
    panel.unmount();

    const reopened = render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);
    expect(await screen.findByText('Updated thought')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Delete note' }));
    await waitFor(() => expect(screen.queryByText('Updated thought')).toBeNull());
    expect(deleteReadingAssistantAnnotation).toHaveBeenCalledWith(99);
    reopened.unmount();
    render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);
    await waitFor(() => expect(listReadingAssistantAnnotations).toHaveBeenCalledTimes(3));
    expect(screen.queryByText('Updated thought')).toBeNull();
  });
});
