import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import ReadingAssistantForm from '@/components/settings/integrations/ReadingAssistantForm';
import { importBookToCloud, listReadingAssistantBooks } from '@/services/readingAssistant/library';

const session = vi.hoisted(() => ({
  serverUrl: 'http://localhost:8080',
  token: 'token',
  username: 'reader',
  userId: 1,
  expiresAt: '2099-01-01T00:00:00Z',
}));

vi.mock('@/hooks/useTranslation', () => ({
  useTranslation: () => (key: string, values?: Record<string, number>) =>
    key.replace(/{{(\w+)}}/g, (match, name) => String(values?.[name] ?? match)),
}));
vi.mock('@/services/readingAssistant/client', () => ({
  getReadingAssistantSession: () => session,
  validateReadingAssistantSession: async () => session,
  ReadingAssistantError: Error,
}));
vi.mock('@/services/readingAssistant/library', () => ({
  getReadingAssistantStorageTarget: () => 'gdrive',
  listReadingAssistantBooks: vi.fn().mockResolvedValue({ books: [], hasNext: false }),
  importBookToCloud: vi.fn(),
}));

afterEach(cleanup);

it('imports the whole selection, reports failures, and refreshes the catalog once', async () => {
  vi.mocked(importBookToCloud)
    .mockRejectedValueOnce(new Error('Upload failed'))
    .mockResolvedValueOnce('exists')
    .mockResolvedValueOnce('imported');

  render(<ReadingAssistantForm onBack={() => {}} />);
  await waitFor(() => expect(listReadingAssistantBooks).toHaveBeenCalledTimes(1));

  const input = screen.getByLabelText<HTMLInputElement>('Import to cloud');
  expect(input.multiple).toBe(true);
  const files = ['failed.epub', 'existing.epub', 'new.epub'].map(
    (name) => new File(['epub'], name),
  );
  fireEvent.change(input, { target: { files } });

  expect(
    await screen.findByText('Cloud import complete: 1 imported, 1 already in library, 1 failed.'),
  ).toBeTruthy();
  expect(screen.getByText('failed.epub: Upload failed')).toBeTruthy();
  expect(vi.mocked(importBookToCloud).mock.calls.map(([file]) => file)).toEqual(files);
  expect(listReadingAssistantBooks).toHaveBeenCalledTimes(2);
  expect(input.disabled).toBe(false);
});
