export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message);
  }
}

type Query = Record<string, string | number | boolean | undefined | null>;

export function qs(query?: Query): string {
  if (!query) return '';
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  }
  const s = p.toString();
  return s ? `?${s}` : '';
}

export async function api<T>(
  path: string,
  opts: { method?: string; body?: unknown; query?: Query } = {},
): Promise<T> {
  const res = await fetch(`/api/proxy/${path}${qs(opts.query)}`, {
    method: opts.method ?? 'GET',
    headers: opts.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  if (res.status === 401 && typeof window !== 'undefined') {
    window.location.href = '/login';
    throw new ApiError(401, 'UNAUTHORIZED', 'Session expirée.');
  }
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new ApiError(res.status, data.code ?? 'ERROR', data.message ?? `Erreur ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export function exportUrl(query: Query): string {
  return `/api/proxy/admin/persons/export${qs(query)}`;
}

export function signatureUrl(id: number): string {
  return `/api/proxy/admin/persons/${id}/signature`;
}

export function documentUrl(id: number): string {
  return `/api/proxy/admin/persons/${id}/document`;
}
