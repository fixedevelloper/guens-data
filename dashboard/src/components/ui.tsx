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
  const W = 720, H = 220, PX = 16, PT = 24, PB = 28;
  const max = Math.max(1, ...points.map((p) => p.count));
  const step = points.length > 1 ? (W - PX * 2) / (points.length - 1) : 0;
  const xy = points.map((p, i) => [PX + (points.length > 1 ? i * step : (W - PX * 2) / 2), PT + (1 - p.count / max) * (H - PT - PB)] as const);
  const line = xy.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
  const area = `${line} L${xy[xy.length - 1][0]},${H - PB} L${xy[0][0]},${H - PB} Z`;
  return (
    <svg className="chart" viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="Enregistrements par jour">
      <defs>
        <linearGradient id="tl-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#0b57d0" stopOpacity=".35" />
          <stop offset="100%" stopColor="#2f9e44" stopOpacity="0" />
        </linearGradient>
        <linearGradient id="tl-line" x1="0" y1="0" x2="1" y2="0">
          <stop offset="0%" stopColor="#0b57d0" /><stop offset="100%" stopColor="#2f9e44" />
        </linearGradient>
      </defs>
      {[0, 0.5, 1].map((t) => {
        const y = PT + t * (H - PT - PB);
        return <line key={t} x1={PX} x2={W - PX} y1={y} y2={y} stroke="var(--border)" strokeDasharray="4 4" />;
      })}
      <path d={area} fill="url(#tl-fill)" />
      <path d={line} fill="none" stroke="url(#tl-line)" strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />
      {xy.map(([x, y], i) => (
        <circle key={points[i].day} cx={x} cy={y} r={points.length > 40 ? 0 : 3.5} fill="var(--surface)" stroke="#0b57d0" strokeWidth={2}>
          <title>{`${points[i].day} : ${points[i].count}`}</title>
        </circle>
      ))}
      <text x={PX} y={H - 8} fontSize="11" fill="var(--muted)">{points[0].day}</text>
      <text x={W - PX} y={H - 8} fontSize="11" fill="var(--muted)" textAnchor="end">{points[points.length - 1].day}</text>
      <text x={W - PX} y={14} fontSize="11" fill="var(--muted)" textAnchor="end">max {max}</text>
    </svg>
  );
}

export function PageHeader({ title, subtitle, children }: { title: string; subtitle?: string; children?: ReactNode }) {
  return (
    <div className="page-head">
      <div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>
      {children && <div className="row">{children}</div>}
    </div>
  );
}

const HUES = ['#0b57d0', '#2f9e44', '#1a73e8', '#f59e0b', '#0f9d9a', '#7c3aed', '#e5484d', '#3b82f6'];

/** Avatar à initiales, couleur stable dérivée du nom. */
export function Avatar({ name }: { name: string }) {
  const parts = name.trim().split(/\s+/);
  const initials = ((parts[0]?.[0] ?? '?') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).slice(0, 2);
  let h = 0;
  for (const c of name) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  const a = HUES[h % HUES.length], b = HUES[(h >> 3) % HUES.length];
  return <span className="avatar" style={{ background: `linear-gradient(135deg, ${a}, ${b})` }} aria-hidden="true">{initials}</span>;
}

export function Who({ name, sub }: { name: string; sub?: ReactNode }) {
  return <div className="who"><Avatar name={name} /><div><b>{name}</b>{sub && <span>{sub}</span>}</div></div>;
}

export function Kpi({ label, value, icon, hero }: { label: string; value: ReactNode; icon: ReactNode; hero?: boolean }) {
  return (
    <div className={`card kpi${hero ? ' hero' : ''}`} style={{ marginBottom: 0 }}>
      <span className="kpi-ico">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{icon}</svg>
      </span>
      <div><div className="v">{value ?? <span className="skel" style={{ display: 'inline-block', width: 56, height: 26 }} />}</div><div className="l">{label}</div></div>
    </div>
  );
}

export const fmtDate = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—';
