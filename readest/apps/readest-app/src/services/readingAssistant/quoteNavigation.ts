import type { FoliateView } from '@/types/view';

export interface ReadingQuoteLocation {
  chapterIndex: number;
  quoteCfi?: string | null;
  bookFingerprint?: string | null;
}

const normalizeText = (text: string) => text.replace(/\s+/g, ' ').trim();

export const captureReadingQuote = (view: FoliateView | null) => {
  if (!view?.renderer) return null;
  for (const content of view.renderer.getContents()) {
    const selection = content.doc?.getSelection();
    if (!selection?.rangeCount || !selection.toString().trim() || content.index === undefined) {
      continue;
    }
    const text = selection.toString().trim();
    const chapterIndex = content.index + 1;
    try {
      return { text, chapterIndex, quoteCfi: view.getCFI(content.index, selection.getRangeAt(0)) };
    } catch {
      return { text, chapterIndex };
    }
  }
  return null;
};

// Foliate's view.goTo swallows navigation errors. Use the renderer and verify
// that the requested section actually opened before reporting success.
export const navigateReadingPosition = async (
  view: FoliateView,
  target: string | number,
): Promise<void> => {
  const resolved = view.resolveNavigation(target);
  if (!resolved || !view.book.sections[resolved.index]) throw new Error('Unavailable section');
  await view.renderer.goTo(resolved);
  if (!view.renderer.getContents().some((content) => content.index === resolved.index)) {
    throw new Error('Section did not open');
  }
};

export const openReadingQuote = async (
  view: FoliateView | null,
  quote: ReadingQuoteLocation & { selectedText: string | null },
  fingerprint?: string,
): Promise<'exact' | 'chapter' | 'unavailable'> => {
  if (!view) return 'unavailable';
  if (quote.quoteCfi && (!quote.bookFingerprint || quote.bookFingerprint === fingerprint)) {
    try {
      const { index, anchor } = view.resolveCFI(quote.quoteCfi);
      const section = view.book.sections[index];
      if (!section) throw new Error('Unavailable section');
      const range = anchor(await section.createDocument());
      const text = normalizeText(range.toString());
      const snapshot = normalizeText(quote.selectedText ?? '');
      // A cross-section selection can store more text than its first CFI range.
      if (!text || !snapshot.startsWith(text)) throw new Error('Quote no longer matches');
      await navigateReadingPosition(view, quote.quoteCfi);
      return 'exact';
    } catch {
      // Older files and stale anchors can still be opened at chapter level.
    }
  }
  const index = quote.chapterIndex - 1;
  if (!Number.isInteger(index) || index < 0 || !view.book.sections[index]) return 'unavailable';
  try {
    await view.book.sections[index]!.createDocument();
    await navigateReadingPosition(view, index);
    return 'chapter';
  } catch {
    return 'unavailable';
  }
};
