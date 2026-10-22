import { NextRequest, NextResponse } from 'next/server';

// Garde de navigation (UX) : la vraie sécurité est portée par le backend, qui valide chaque appel.
// Next.js 16 : le fichier « middleware » est devenu « proxy ».
export function proxy(req: NextRequest) {
  const hasSession = req.cookies.has('fc_refresh') || req.cookies.has('fc_access');
  const isLogin = req.nextUrl.pathname === '/login';
  if (!hasSession && !isLogin) return NextResponse.redirect(new URL('/login', req.url));
  if (hasSession && isLogin) return NextResponse.redirect(new URL('/', req.url));
  return NextResponse.next();
}

export const config = { matcher: ['/((?!api|_next|favicon.ico).*)'] };
