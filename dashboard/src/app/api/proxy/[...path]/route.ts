import { NextRequest, NextResponse } from 'next/server';
import { ACCESS_COOKIE, BACKEND, clearSession, REFRESH_COOKIE, refreshSession, setSession } from '@/lib/backend';

// Seules les routes d'administration (et /auth/me) sont atteignables via ce proxy.
const ALLOWED = /^(admin\/|auth\/me$)/;
const PASSTHROUGH_HEADERS = ['content-type', 'content-disposition', 'cache-control'];

async function forward(req: NextRequest, path: string[]): Promise<NextResponse> {
  const joined = path.join('/');
  if (!ALLOWED.test(joined) || joined.includes('..')) {
    return NextResponse.json({ code: 'FORBIDDEN', message: 'Route non autorisée.' }, { status: 403 });
  }
  const url = `${BACKEND}/api/v1/${joined}${req.nextUrl.search}`;
  const hasBody = !['GET', 'HEAD'].includes(req.method);
  const body = hasBody ? await req.arrayBuffer() : undefined;
  const contentType = req.headers.get('content-type');

  const call = (token: string) =>
    fetch(url, {
      method: req.method,
      headers: { Authorization: `Bearer ${token}`, ...(contentType ? { 'Content-Type': contentType } : {}) },
      body,
      cache: 'no-store',
    });

  let access = req.cookies.get(ACCESS_COOKIE)?.value;
  const refresh = req.cookies.get(REFRESH_COOKIE)?.value;
  let renewed = null as Awaited<ReturnType<typeof refreshSession>>;

  if (!access && refresh) {
    renewed = await refreshSession(refresh);
    access = renewed?.accessToken;
  }
  if (!access) {
    const res = NextResponse.json({ code: 'UNAUTHORIZED', message: 'Session expirée.' }, { status: 401 });
    clearSession(res);
    return res;
  }

  let upstream: Response;
  try {
    upstream = await call(access);
    if (upstream.status === 401 && refresh && !renewed) {
      renewed = await refreshSession(refresh);
      if (renewed) upstream = await call(renewed.accessToken);
    }
  } catch {
    return NextResponse.json({ code: 'BACKEND_UNREACHABLE', message: 'Serveur injoignable.' }, { status: 502 });
  }

  const headers = new Headers();
  for (const h of PASSTHROUGH_HEADERS) {
    const v = upstream.headers.get(h);
    if (v) headers.set(h, v);
  }
  const res = new NextResponse(upstream.status === 204 ? null : upstream.body, {
    status: upstream.status,
    headers,
  });
  if (renewed) setSession(res, renewed);
  if (upstream.status === 401) clearSession(res);
  return res;
}

// Next.js 15+ : `params` est une Promise.
type Ctx = { params: Promise<{ path: string[] }> };
export const GET = async (req: NextRequest, { params }: Ctx) => forward(req, (await params).path);
export const POST = async (req: NextRequest, { params }: Ctx) => forward(req, (await params).path);
export const PUT = async (req: NextRequest, { params }: Ctx) => forward(req, (await params).path);
export const PATCH = async (req: NextRequest, { params }: Ctx) => forward(req, (await params).path);
export const DELETE = async (req: NextRequest, { params }: Ctx) => forward(req, (await params).path);
