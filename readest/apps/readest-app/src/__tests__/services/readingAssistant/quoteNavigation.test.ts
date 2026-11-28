import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { FoliateView } from '@/types/view';
import {
  captureReadingQuote,
  navigateReadingPosition,
  openReadingQuote,
} from '@/services/readingAssistant/quoteNavigation';

const cfi = 'epubcfi(/6/6!/4/2,/1:0,/1:11)';

const makeView = () => {
  const doc = document.implementation.createHTMLDocument();
  doc.body.textContent = 'Saved quote';
  const range = doc.createRange();
  range.selectNodeContents(doc.body);
  let index = 0;
  const anchor = vi.fn(() => range);
  const renderer = {
    goTo: vi.fn(async (target: { index: number }) => {
      index = target.index;
    }),
    getContents: () => [{ index, doc }],
  };
  const view = {
    book: {
      sections: Array.from({ length: 4 }, () => ({ createDocument: vi.fn(async () => doc) })),
    },
    renderer,
    resolveCFI: vi.fn(() => ({ index: 2, anchor })),
    resolveNavigation: vi.fn((target: string | number) =>
      typeof target === 'number' ? { index: target } : { index: 2, anchor },
    ),
    getCFI: vi.fn(() => cfi),
  };
  return { view: view as unknown as FoliateView, renderer, anchor, doc, range };
};

const quote = {
  chapterIndex: 3,
  quoteCfi: cfi,
  bookFingerprint: 'same-file',
  selectedText: 'Saved quote',
};

describe('historical reading quote navigation', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('validates the original text and opens its exact CFI after a reload', async () => {
    const { view, renderer } = makeView();
    expect(await openReadingQuote(view, quote, 'same-file')).toBe('exact');
    expect(view.resolveNavigation).toHaveBeenCalledWith(cfi);
    expect(renderer.goTo).toHaveBeenCalledWith(
      expect.objectContaining({ index: 2, anchor: expect.any(Function) }),
    );
  });

  it.each([
    'different-file',
    undefined,
  ])('falls back to the chapter when the current fingerprint is %s', async (fingerprint) => {
    const { view, renderer } = makeView();
    expect(await openReadingQuote(view, quote, fingerprint)).toBe('chapter');
    expect(view.resolveCFI).not.toHaveBeenCalled();
    expect(renderer.goTo).toHaveBeenCalledWith({ index: 2 });
  });

  it('falls back for a stale CFI even if the file fingerprint matches', async () => {
    const { view, doc, renderer } = makeView();
    doc.body.textContent = 'Different text';
    expect(await openReadingQuote(view, quote, 'same-file')).toBe('chapter');
    expect(renderer.goTo).toHaveBeenCalledWith({ index: 2 });
  });

  it('opens older quotes without a CFI at chapter level', async () => {
    const { view, renderer } = makeView();
    expect(await openReadingQuote(view, { chapterIndex: 2, selectedText: 'Old snapshot' })).toBe(
      'chapter',
    );
    expect(renderer.goTo).toHaveBeenCalledWith({ index: 1 });
  });

  it('keeps the current location when neither the chapter nor file is available', async () => {
    const { view, renderer } = makeView();
    expect(await openReadingQuote(view, { chapterIndex: 99, selectedText: 'Snapshot' })).toBe(
      'unavailable',
    );
    expect(renderer.goTo).not.toHaveBeenCalled();
    expect(await openReadingQuote(null, quote)).toBe('unavailable');
  });

  it('does not report success when the renderer silently leaves another section open', async () => {
    const { view, renderer } = makeView();
    renderer.goTo.mockImplementation(async () => {});
    expect(await openReadingQuote(view, quote, 'same-file')).toBe('unavailable');
    await expect(navigateReadingPosition(view, 2)).rejects.toThrow('Section did not open');
  });

  it('keeps the current position when the chapter file cannot load', async () => {
    const { view, renderer } = makeView();
    vi.mocked(view.book.sections[2]!.createDocument).mockRejectedValue(new Error('Missing file'));
    expect(await openReadingQuote(view, quote, 'same-file')).toBe('unavailable');
    expect(renderer.goTo).not.toHaveBeenCalled();
  });

  it('captures the range and chapter of the live selection rather than the viewport', () => {
    const { view, doc, range } = makeView();
    vi.spyOn(doc, 'getSelection').mockReturnValue({
      rangeCount: 1,
      toString: () => 'Saved quote',
      getRangeAt: () => range,
    } as unknown as Selection);
    expect(captureReadingQuote(view)).toEqual({
      text: 'Saved quote',
      chapterIndex: 1,
      quoteCfi: cfi,
    });
    expect(view.getCFI).toHaveBeenCalledWith(0, range);
  });
});
