import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

vi.mock('@/context/EnvContext', () => ({ useEnv: () => ({ appService: null }) }));
vi.mock('@/hooks/useTranslation', () => ({ useTranslation: () => (text: string) => text }));
vi.mock('@/store/settingsStore', () => ({
  useSettingsStore: () => ({ settings: { aiSettings: { enabled: false } } }),
}));
vi.mock('@/services/readingAssistant/library', () => ({
  getReadingAssistantBookId: (hash: string) => (hash === 'remote' ? 22 : null),
}));

import NotebookTabNavigation from '@/app/reader/components/notebook/NotebookTabNavigation';

describe('Notebook AI tab for remote books', () => {
  it('shows AI for a Reading Assistant book when local AI is disabled', () => {
    const { rerender } = render(
      <NotebookTabNavigation activeTab='notes' onTabChange={() => {}} bookKey='remote-1' />,
    );
    expect(screen.getByRole('button', { name: 'AI' })).toBeTruthy();

    rerender(<NotebookTabNavigation activeTab='notes' onTabChange={() => {}} bookKey='local-1' />);
    expect(screen.queryByRole('button', { name: 'AI' })).toBeNull();
  });
});
