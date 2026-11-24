const selections = new Map<string, string>();

export const setReadingAssistantSelection = (bookKey: string, text: string): void => {
  if (text) selections.set(bookKey, text);
  else selections.delete(bookKey);
  if (typeof window !== 'undefined') {
    window.dispatchEvent(
      new CustomEvent('reading-assistant-selection', { detail: { bookKey, text } }),
    );
  }
};

export const getReadingAssistantSelection = (bookKey: string): string =>
  selections.get(bookKey) ?? '';
