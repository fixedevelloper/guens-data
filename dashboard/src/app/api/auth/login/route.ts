import { NextRequest, NextResponse } from 'next/server';
import { BACKEND, setSession, TokenResponse } from '@/lib/backend';

export async function POST(req: NextRequest) {
  const body = await req.json().catch(() => null);
  if (!body?.username || !body?.password) {
    return NextResponse.json({ code: 'VALIDATION_ERROR', message: 'Identifiants requis.' }, { status: 400 });
  }
  let upstream: Response;
  try {
    upstream = await fetch(`${BACKEND}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: body.username, password: body.password }),
      cache: 'no-store',
    });
  } catch {
    return NextResponse.json({ code: 'BACKEND_UNREACHABLE', message: 'Serveur injoignable.' }, { status: 502 });
  }
  const data = await upstream.json().catch(() => ({}));
  if (!upstream.ok) return NextResponse.json(data, { status: upstream.status });

  const t = data as TokenResponse;
  if (t.user.role !== 'ADMIN') {
    return NextResponse.json(
      { code: 'NOT_ADMIN', message: "Ce tableau de bord est réservé aux administrateurs." },
      { status: 403 },
    );
  }
  const res = NextResponse.json({ user: t.user });
  setSession(res, t);
  return res;
}
