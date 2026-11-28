import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const goToMock = vi.fn();
const navigateMock = vi.fn();
let renderedIndex = 1;
const chapterDocument = document.implementation.createHTMLDocument();

vi.mock('@/hooks/useTranslation', () => ({ useTranslation: () => (text: string) => text }));
vi.mock('@/store/readerProgressStore', () => ({
  useBookProgress: () => ({ index: 1, location: 'epubcfi(/6/4!/4/2:0)' }),
}));
vi.mock('@/store/readerStore', () => ({
  useReaderStore: (selector: (state: { getView: () => unknown }) => unknown) =>
    selector({
      getView: () => ({
        goTo: goToMock,
        resolveNavigation: (target: string | number) => ({
          index: typeof target === 'number' ? target : 1,
        }),
        book: {
          sections: Array.from({ length: 5 }, () => ({
            createDocument: async () => chapterDocument,
          })),
        },
        renderer: {
          goTo: navigateMock,
          getContents: () => [{ index: renderedIndex, doc: chapterDocument }],
        },
      }),
    }),
}));
vi.mock('@/store/bookDataStore', () => ({
  useBookDataStore: (selector: (state: { getBookData: () => unknown }) => unknown) =>
    selector({ getBookData: () => ({ book: { hash: 'local-version' } }) }),
}));
vi.mock('@/services/readingAssistant/selection', () => ({
  getReadingAssistantSelection: () => 'selected passage',
}));
vi.mock('@/services/readingAssistant/ai', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/services/readingAssistant/ai')>()),
  askReadingAssistant: vi.fn(),
  stopReadingAnswer: vi.fn(),
  listReadingConversations: vi.fn(),
  getReadingConversation: vi.fn(),
  createReadingConversation: vi.fn(),
}));
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

import { useNotebookStore } from '@/store/notebookStore';
import ReadingAssistantChat from '@/app/reader/components/notebook/ReadingAssistantChat';
import {
  askReadingAssistant,
  createReadingConversation,
  getReadingConversation,
  listReadingConversations,
  stopReadingAnswer,
} from '@/services/readingAssistant/ai';
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
    renderedIndex = 1;
    navigateMock.mockImplementation(async ({ index }: { index: number }) => {
      renderedIndex = index;
    });
    localStorage.removeItem('reading-assistant-answer-mode');
    vi.mocked(listReadingConversations).mockResolvedValue([
      { id: 'default', title: null, defaultConversation: true },
    ]);
    vi.mocked(getReadingConversation).mockResolvedValue({ turns: [], before: null });
  });
  afterEach(() => {
    cleanup();
  });

  it('sends the current book, chapter and selection, then shows answer and sources', async () => {
    vi.mocked(askReadingAssistant).mockResolvedValue({
      answer: 'This passage describes the setting.',
      sources: ['Chapter 2'],
      conversationTurnId: 1,
    });
    render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);

    fireEvent.change(screen.getByLabelText('Ask about this book'), {
      target: { value: 'What does this mean?' },
    });
    const ask = screen.getByRole<HTMLButtonElement>('button', { name: 'Ask Reading Assistant' });
    await waitFor(() => expect(ask.disabled).toBe(false));
    fireEvent.click(ask);

    await waitFor(() =>
      expect(askReadingAssistant).toHaveBeenCalledWith(
        {
          conversationId: 'default',
          mode: 'deep',
          bookId: 22,
          chapterIndex: 2,
          question: 'What does this mean?',
          selectedText: 'selected passage',
          quoteCfi: undefined,
          bookFingerprint: 'local-version',
        },
        expect.any(Object),
      ),
    );
    expect(await screen.findByText('This passage describes the setting.')).toBeTruthy();
    expect(screen.getByText('Chapter 2')).toBeTruthy();
  });

  it('uses the toolbar passage and its original chapter after the selection disappears', async () => {
    try {
      useNotebookStore.getState().openNotebookForQuestion({
        bookKey: 'local-book',
        text: 'passage from toolbar',
        chapterIndex: 4,
        quoteCfi: 'epubcfi(/6/8!/4/2:0)',
        bookFingerprint: 'original-version',
      });
      vi.mocked(askReadingAssistant).mockResolvedValue({
        answer: 'Answer',
        sources: [],
        conversationTurnId: 1,
      });
      render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);

      expect(screen.getByText('passage from toolbar')).toBeTruthy();
      fireEvent.change(screen.getByLabelText('Ask about this book'), {
        target: { value: 'Explain this' },
      });
      const ask = screen.getByRole<HTMLButtonElement>('button', { name: 'Ask Reading Assistant' });
      await waitFor(() => expect(ask.disabled).toBe(false));
      fireEvent.click(ask);

      await waitFor(() =>
        expect(askReadingAssistant).toHaveBeenCalledWith(
          {
            conversationId: 'default',
            mode: 'deep',
            bookId: 22,
            chapterIndex: 4,
            question: 'Explain this',
            selectedText: 'passage from toolbar',
            quoteCfi: 'epubcfi(/6/8!/4/2:0)',
            bookFingerprint: 'original-version',
          },
          expect.any(Object),
        ),
      );
    } finally {
      useNotebookStore.getState().setNotebookVisible(false);
    }
  });

  it('opens a historical quote and returns to the previous reading position', async () => {
    vi.mocked(getReadingConversation).mockResolvedValueOnce({
      before: null,
      turns: [
        {
          id: 31,
          chapterIndex: 3,
          question: 'Earlier question',
          selectedText: 'Saved quote',
          answer: 'Earlier answer',
          sources: [],
          status: 'completed',
        },
      ],
    });
    render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Open quoted passage' }));
    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith({ index: 2 }));
    expect(
      screen.getByText('Opened the chapter. The exact passage could not be located.'),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Return to reading position' }));
    await waitFor(() => expect(navigateMock).toHaveBeenLastCalledWith({ index: 1 }));
  });

  it('keeps the saved quote visible and reports a missing chapter without offering a return jump', async () => {
    vi.mocked(getReadingConversation).mockResolvedValueOnce({
      before: null,
      turns: [
        {
          id: 32,
          chapterIndex: 99,
          question: 'Earlier question',
          selectedText: 'Unavailable quote',
          answer: 'Earlier answer',
          sources: [],
          status: 'completed',
        },
      ],
    });
    render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Open quoted passage' }));
    expect(
      await screen.findByText(
        'Could not open the quoted passage. The saved text is still available.',
      ),
    ).toBeTruthy();
    expect(screen.getByText('Unavailable quote')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Return to reading position' })).toBeNull();
    expect(navigateMock).not.toHaveBeenCalledWith({ index: 98 });
  });

  it('restores the latest conversation and can create and switch conversations', async () => {
    vi.mocked(getReadingConversation).mockResolvedValueOnce({
      before: null,
      turns: [
        {
          id: 2,
          chapterIndex: 1,
          question: 'Previous question',
          selectedText: null,
          answer: 'Previous answer',
          sources: [],
          status: 'completed',
        },
      ],
    });
    vi.mocked(createReadingConversation).mockResolvedValue({
      id: 'new',
      title: null,
      defaultConversation: false,
    });
    render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);
    expect(await screen.findByText('Previous answer')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'New conversation' }));
    await waitFor(() =>
      expect(screen.getByLabelText<HTMLSelectElement>('Conversation').value).toBe('new'),
    );
    expect(screen.queryByText('Previous answer')).toBeNull();
    fireEvent.change(screen.getByLabelText('Conversation'), { target: { value: 'default' } });
    await waitFor(() => expect(getReadingConversation).toHaveBeenLastCalledWith(22, 'default'));
  });

  it('replaces the pinned quote without changing the draft or the sent passage', async () => {
    const store = useNotebookStore.getState();
    store.openNotebookForQuestion({ bookKey: 'local-book', text: 'First quote', chapterIndex: 2 });
    store.setNotebookPin(true);
    let resolveAnswer!: (answer: {
      answer: string;
      sources: string[];
      conversationTurnId: number;
    }) => void;
    vi.mocked(askReadingAssistant).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveAnswer = resolve;
      }),
    );

    try {
      render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);
      const input = screen.getByLabelText<HTMLTextAreaElement>('Ask about this book');
      fireEvent.change(input, { target: { value: 'Explain the selected text' } });
      act(() =>
        store.setPinnedNotebookQuestionContext({
          bookKey: 'local-book',
          text: 'Second quote',
          chapterIndex: 3,
        }),
      );
      expect(input.value).toBe('Explain the selected text');
      const ask = screen.getByRole<HTMLButtonElement>('button', { name: 'Ask Reading Assistant' });
      await waitFor(() => expect(ask.disabled).toBe(false));
      fireEvent.click(ask);
      act(() =>
        store.setPinnedNotebookQuestionContext({
          bookKey: 'local-book',
          text: 'Next quote',
          chapterIndex: 4,
        }),
      );
      expect(askReadingAssistant).toHaveBeenCalledWith(
        expect.objectContaining({
          selectedText: 'Second quote',
          chapterIndex: 3,
        }),
        expect.any(Object),
      );
      await act(async () =>
        resolveAnswer({ answer: 'Answer', sources: [], conversationTurnId: 7 }),
      );
      expect(screen.getByText('Second quote')).toBeTruthy();
      expect(screen.getByText('Next quote')).toBeTruthy();
      fireEvent.click(screen.getByRole('button', { name: 'Clear quotation' }));
      expect(screen.queryByText('Next quote')).toBeNull();
      expect(screen.getByText('Second quote')).toBeTruthy();
    } finally {
      cleanup();
      store.setNotebookPin(false);
      store.setNotebookVisible(false);
    }
  });

  it('switches to quick mode in the same conversation and remembers it after reopening', async () => {
    vi.mocked(askReadingAssistant).mockResolvedValue({
      answer: 'Quick answer',
      sources: [],
      conversationTurnId: 8,
    });
    const panel = <ReadingAssistantChat bookKey='local-book' bookId={22} />;
    const { unmount } = render(panel);
    fireEvent.change(screen.getByLabelText('Answer mode'), { target: { value: 'quick' } });
    fireEvent.change(screen.getByLabelText('Ask about this book'), {
      target: { value: 'Explain' },
    });
    const ask = screen.getByRole<HTMLButtonElement>('button', { name: 'Ask Reading Assistant' });
    await waitFor(() => expect(ask.disabled).toBe(false));
    fireEvent.click(ask);
    expect(await screen.findByText('Quick answer')).toBeTruthy();
    expect(askReadingAssistant).toHaveBeenCalledWith(
      expect.objectContaining({
        conversationId: 'default',
        mode: 'quick',
      }),
      expect.any(Object),
    );
    unmount();
    render(panel);
    expect(screen.getByLabelText<HTMLSelectElement>('Answer mode').value).toBe('quick');
    await waitFor(() => expect(getReadingConversation).toHaveBeenLastCalledWith(22, 'default'));
  });

  it('shows streamed text and stops the current generation without dropping the partial answer', async () => {
    let complete!: (answer: {
      answer: string;
      sources: string[];
      conversationTurnId: number;
      status: string;
    }) => void;
    vi.mocked(askReadingAssistant).mockImplementationOnce((_input, stream) => {
      stream?.onStage('answering');
      stream?.onDelta('Partial answer');
      return new Promise((resolve) => {
        complete = resolve;
      });
    });
    vi.mocked(stopReadingAnswer).mockImplementationOnce(async () => {
      complete({ answer: 'Partial answer', sources: [], conversationTurnId: 9, status: 'stopped' });
    });
    render(<ReadingAssistantChat bookKey='local-book' bookId={22} />);
    fireEvent.change(screen.getByLabelText('Ask about this book'), {
      target: { value: 'Explain' },
    });
    const ask = screen.getByRole<HTMLButtonElement>('button', { name: 'Ask Reading Assistant' });
    await waitFor(() => expect(ask.disabled).toBe(false));
    fireEvent.click(ask);
    expect(await screen.findByText('Partial answer')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Stop generating' }));
    expect(await screen.findByText('Generation stopped')).toBeTruthy();
    expect(screen.getByText('Partial answer')).toBeTruthy();
    expect(stopReadingAnswer).toHaveBeenCalledWith(
      vi.mocked(askReadingAssistant).mock.calls[0]?.[1]?.id,
    );
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
