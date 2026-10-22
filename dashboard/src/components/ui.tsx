'use client';
import { ReactNode, useCallback, useEffect, useState } from 'react';

export function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : 'Erreur inattendue';
}

/** Charge des données ; `deps` déclenche un rechargement. */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fn()
      .then((d) => { if (!cancelled) { setData(d); setError(null); } })
      .catch((e) => { if (!cancelled) setError(errMsg(e)); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, error, loading, reload };
}

export function ErrorBox({ error }: { error: string | null }) {
  return error ? <div className="error" role="alert">{error}</div> : null;
}

export function Modal({ title, onClose, children, wide }: {
  title: string; onClose: () => void; children: ReactNode; wide?: boolean;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal${wide ? ' wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h2 style={{ margin: 0 }}>{title}</h2>
          <button onClick={onClose} aria-label="Fermer">✕</button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="f">{label}{children}</label>;
}

export function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="pager">
      <button disabled={page <= 0} onClick={() => onChange(page - 1)}>← Précédent</button>
      <span className="muted">Page {page + 1} / {totalPages}</span>
      <button disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Suivant →</button>
    </div>
  );
}

export function Badge({ kind, children }: { kind?: 'ok' | 'off' | 'warn'; children: ReactNode }) {
  return <span className={`badge${kind ? ' ' + kind : ''}`}>{children}</span>;
}

export function BarList({ rows }: { rows: { label: string; value: number }[] }) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  if (rows.length === 0) return <p className="muted">Aucune donnée.</p>;
  return (
    <div>
      {rows.map((r, i) => (
        <div className="bar-row" key={`${r.label}-${i}`}>
          <span title={r.label} style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{r.label}</span>
          <div className="bar-track"><div className="bar-fill" style={{ width: `${(r.value / max) * 100}%` }} /></div>
          <span className="n">{r.value}</span>
        </div>
      ))}
    </div>
  );
}

export function Timeline({ points }: { points: { day: string; count: number }[] }) {
  if (points.length === 0) return <p className="muted">Aucune donnée.</p>;
  const W = 640, H = 160, P = 24;
  const max = Math.max(1, ...points.map((p) => p.count));
  const bw = Math.max(2, (W - P * 2) / points.length - 3);
  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="Enregistrements par jour">
      {points.map((p, i) => {
        const h = (p.count / max) * (H - P * 2);
        const x = P + i * ((W - P * 2) / points.length);
        return (
          <g key={p.day}>
            <rect x={x} y={H - P - h} width={bw} height={h} rx={2} fill="var(--bar-fill)">
              <title>{`${p.day} : ${p.count}`}</title>
            </rect>
          </g>
        );
      })}
      <text x={P} y={H - 6} fontSize="10" fill="var(--muted)">{points[0].day}</text>
      <text x={W - P} y={H - 6} fontSize="10" fill="var(--muted)" textAnchor="end">{points[points.length - 1].day}</text>
      <text x={P} y={12} fontSize="10" fill="var(--muted)">max {max}</text>
    </svg>
  );
}

export const fmtDate = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—';
