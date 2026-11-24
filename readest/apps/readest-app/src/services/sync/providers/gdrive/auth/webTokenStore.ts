/**
 * Browser-side Google Drive token store for the web build.
 *
 * Unlike native (a refresh token in the OS keychain), the web OAuth model yields
 * a short-lived access token and no refresh token. We persist it in both
 * `sessionStorage` and `localStorage` so it survives the OAuth redirect
 * round-trip, in-tab reloads, and new tabs / reopened browser windows while the
 * token is still within its validity window (`expiresAt`).
 */
import type { TokenSet } from '@/services/sync/providers/oauth/tokenEndpoint';

/** Storage key holding the serialised {@link TokenSet}. */
const WEB_TOKEN_KEY = 'gdrive_web_token';

const getSessionStorage = (): Storage | null => {
  try {
    return typeof window !== 'undefined' ? window.sessionStorage : null;
  } catch {
    return null;
  }
};

const getLocalStorage = (): Storage | null => {
  try {
    return typeof window !== 'undefined' ? window.localStorage : null;
  } catch {
    return null;
  }
};

/** Persist the access token (+ expiry) across current tab and browser storage. */
export const saveWebDriveToken = (tokens: TokenSet): void => {
  const serialized = JSON.stringify(tokens);
  getSessionStorage()?.setItem(WEB_TOKEN_KEY, serialized);
  getLocalStorage()?.setItem(WEB_TOKEN_KEY, serialized);
};

/** Load the stored token from sessionStorage or localStorage, or null when absent/unparseable. */
export const loadWebDriveToken = (): TokenSet | null => {
  const raw =
    getSessionStorage()?.getItem(WEB_TOKEN_KEY) ?? getLocalStorage()?.getItem(WEB_TOKEN_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as TokenSet;
    if (!parsed || typeof parsed.accessToken !== 'string' || typeof parsed.expiresAt !== 'number') {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
};

/** Forget the stored token (Disconnect). */
export const clearWebDriveToken = (): void => {
  getSessionStorage()?.removeItem(WEB_TOKEN_KEY);
  getLocalStorage()?.removeItem(WEB_TOKEN_KEY);
};

/**
 * Whether a usable (non-expired) access token is stored. Lets the settings panel
 * show a "session expired" hint + Reconnect when the web token is gone/expired
 * (the connection stays `enabled`, but the short-lived token must be re-minted).
 */
export const hasValidWebDriveToken = (now: number = Date.now()): boolean => {
  const tokens = loadWebDriveToken();
  return !!tokens && now < tokens.expiresAt;
};
