import { NextRequest, NextResponse } from 'next/server';
import { ACCESS_COOKIE, BACKEND, clearSession, REFRESH_COOKIE } from '@/lib/backend';

export async function POST(req: NextRequest) {
  const refresh = req.cookies.get(REFRESH_COOKIE)?.value;
  if (refresh) {
    // Révocation côté serveur ; on ignore l'échec : la session locale est supprimée dans tous les cas.
    await fetch(`${BACKEND}/api/v1/auth/logout`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: refresh }),
      cache: 'no-store',
    }).catch(() => undefined);
  }
  void ACCESS_COOKIE;
  const res = NextResponse.json({ ok: true });
  clearSession(res);
  return res;
}
