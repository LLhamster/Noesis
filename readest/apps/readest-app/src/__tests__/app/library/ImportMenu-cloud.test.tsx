import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import ImportMenu from '@/app/library/components/ImportMenu';

vi.mock('@/context/EnvContext', () => ({ useEnv: () => ({ appService: {} }) }));
vi.mock('@/hooks/useTranslation', () => ({ useTranslation: () => (key: string) => key }));

afterEach(cleanup);

it('opens the cloud import entry from the library import menu', () => {
  const onImportBookToCloud = vi.fn();
  const setIsDropdownOpen = vi.fn();
  render(
    <ImportMenu
      onImportBooksFromFiles={vi.fn()}
      onImportBookToCloud={onImportBookToCloud}
      onOpenCatalogManager={vi.fn()}
      onOpenFeeds={vi.fn()}
      setIsDropdownOpen={setIsDropdownOpen}
    />,
  );
  fireEvent.click(screen.getByText('Import to cloud'));
  expect(setIsDropdownOpen).toHaveBeenCalledWith(false);
  expect(onImportBookToCloud).toHaveBeenCalledOnce();
});
