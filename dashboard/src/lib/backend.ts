import { NextResponse } from 'next/server';

export const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080';
export const ACCESS_COOKIE = 'fc_access';
export const REFRESH_COOKIE = 'fc_refresh';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: { id: number; username: string; fullName: string; role: string };
}

const cookieBase = {
  httpOnly: true,
  sameSite: 'strict' as const,
  secure: process.env.NODE_ENV === 'production',
  path: '/',
};

export function setSession(res: NextResponse, t: TokenResponse) {
  res.cookies.set(ACCESS_COOKIE, t.accessToken, { ...cookieBase, maxAge: Math.max(60, t.expiresInSeconds - 30) });
  res.cookies.set(REFRESH_COOKIE, t.refreshToken, { ...cookieBase, maxAge: 60 * 60 * 24 * 30 });
}

export function clearSession(res: NextResponse) {
  res.cookies.set(ACCESS_COOKIE, '', { ...cookieBase, maxAge: 0 });
  res.cookies.set(REFRESH_COOKIE, '', { ...cookieBase, maxAge: 0 });
}

export async function refreshSession(refreshToken: string): Promise<TokenResponse | null> {
  const r = await fetch(`${BACKEND}/api/v1/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
    cache: 'no-store',
  });
  if (!r.ok) return null;
  const t = (await r.json()) as TokenResponse;
  return t.user.role === 'ADMIN' ? t : null;
}
