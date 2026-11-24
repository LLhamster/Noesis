import { afterEach, describe, expect, test } from 'vitest';
import {
  clearWebDriveToken,
  hasValidWebDriveToken,
  loadWebDriveToken,
  saveWebDriveToken,
} from '@/services/sync/providers/gdrive/auth/webTokenStore';

afterEach(() => {
  window.sessionStorage.clear();
  window.localStorage.clear();
});

describe('webTokenStore', () => {
  test('round-trips a token set through sessionStorage and localStorage', () => {
    expect(loadWebDriveToken()).toBeNull();
    saveWebDriveToken({ accessToken: 'AT', expiresAt: 123 });
    expect(loadWebDriveToken()).toEqual({ accessToken: 'AT', expiresAt: 123 });
  });

  test('recovers token from localStorage in a new tab when sessionStorage is empty', () => {
    saveWebDriveToken({ accessToken: 'AT_PERSISTED', expiresAt: 5_000 });
    window.sessionStorage.clear();
    expect(loadWebDriveToken()).toEqual({ accessToken: 'AT_PERSISTED', expiresAt: 5_000 });
    expect(hasValidWebDriveToken(1_000)).toBe(true);
  });

  test('clear removes the stored token from both sessionStorage and localStorage', () => {
    saveWebDriveToken({ accessToken: 'AT', expiresAt: 123 });
    clearWebDriveToken();
    expect(loadWebDriveToken()).toBeNull();
    expect(window.localStorage.getItem('gdrive_web_token')).toBeNull();
  });

  test('returns null for an unparseable or incomplete stored value', () => {
    window.sessionStorage.setItem('gdrive_web_token', 'not json');
    expect(loadWebDriveToken()).toBeNull();
    window.sessionStorage.setItem('gdrive_web_token', JSON.stringify({ accessToken: 123 }));
    expect(loadWebDriveToken()).toBeNull();
  });

  test('hasValidWebDriveToken reflects presence + expiry', () => {
    expect(hasValidWebDriveToken(1_000)).toBe(false); // none stored
    saveWebDriveToken({ accessToken: 'AT', expiresAt: 5_000 });
    expect(hasValidWebDriveToken(1_000)).toBe(true); // not yet expired
    expect(hasValidWebDriveToken(9_000)).toBe(false); // expired
  });
});
