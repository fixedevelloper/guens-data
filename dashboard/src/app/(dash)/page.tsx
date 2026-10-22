'use client';
import { useState } from 'react';
import { api } from '@/lib/api';
import { AppItem, ApplicationCount, Breakdown, CityCount, CountryCount, DayCount, Overview } from '@/lib/types';
import { BarList, ErrorBox, Field, Kpi, PageHeader, Timeline, useLoad } from '@/components/ui';

export default function OverviewPage() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [applicationId, setApplicationId] = useState('');
  const q = { from, to, applicationId };

  const apps = useLoad(() => api<AppItem[]>('admin/applications'), []);
  const ov = useLoad(() => api<Overview>('admin/stats/overview', { query: q }), [from, to, applicationId]);
  const country = useLoad(() => api<CountryCount[]>('admin/stats/by-country', { query: q }), [from, to, applicationId]);
  const city = useLoad(() => api<CityCount[]>('admin/stats/by-city', { query: q }), [from, to, applicationId]);
  const app = useLoad(() => api<ApplicationCount[]>('admin/stats/by-application', { query: { from, to } }), [from, to]);
  const line = useLoad(() => api<DayCount[]>('admin/stats/timeline', { query: q }), [from, to, applicationId]);
  const cube = useLoad(() => api<Breakdown[]>('admin/stats/breakdown', { query: { from, to } }), [from, to]);

  const error = ov.error ?? country.error ?? city.error ?? app.error ?? line.error ?? cube.error;
  const o = ov.data;

  return (
    <>
      <PageHeader title="Tableau de bord" subtitle="Vue d’ensemble de la collecte terrain" />
      <ErrorBox error={error} />
      <div className="toolbar">
        <Field label="Du"><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
        <Field label="Au"><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
        <Field label="Application">
          <select value={applicationId} onChange={(e) => setApplicationId(e.target.value)}>
            <option value="">Toutes</option>
            {apps.data?.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        </Field>
      </div>

      <div className="grid kpis" style={{ marginBottom: 18 }}>
        {[
          ['Total', o?.total, <><path d="M3 3v18h18" /><path d="m7 15 4-4 3 3 5-6" /></>, true],
          ["Aujourd'hui", o?.today, <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>],
          ['7 derniers jours', o?.last7Days, <><rect x="3" y="4" width="18" height="17" rx="3" /><path d="M8 2v4M16 2v4M3 10h18" /></>],
          ['30 derniers jours', o?.last30Days, <><rect x="3" y="4" width="18" height="17" rx="3" /><path d="M8 2v4M16 2v4M3 10h18M8 15h3" /></>],
          ['Agents actifs', o?.activeAgents, <><circle cx="9" cy="8" r="4" /><path d="M2 21v-1a6 6 0 0 1 6-6h2a6 6 0 0 1 6 6v1M17 11l2 2 3-4" /></>],
          ['Par scan', o?.byCaptureMode.SCAN, <><path d="M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M4 12h16" /></>],
          ['Saisie manuelle', o?.byCaptureMode.MANUAL, <><path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5Z" /></>],
        ].map(([l, v, ic, hero]) => (
          <Kpi key={String(l)} label={String(l)} value={v as number | undefined} icon={ic as React.ReactNode} hero={Boolean(hero)} />
        ))}
      </div>

      <div className="card"><h2>Évolution quotidienne (UTC)</h2><Timeline points={line.data ?? []} /></div>

      <div className="grid cols2">
        <div className="card"><h2>Par pays</h2>
          <BarList rows={(country.data ?? []).map((c) => ({ label: c.country, value: c.count }))} /></div>
        <div className="card"><h2>Par application</h2>
          <BarList rows={(app.data ?? []).map((c) => ({ label: c.application, value: c.count }))} /></div>
      </div>
      <div className="card"><h2>Par ville</h2>
        <BarList rows={(city.data ?? []).map((c) => ({ label: `${c.city} (${c.country})`, value: c.count }))} /></div>

      <div className="card"><h2>Détail pays / ville / application</h2>
        <div className="table-wrap"><table>
          <thead><tr><th>Pays</th><th>Ville</th><th>Application</th><th style={{ textAlign: 'right' }}>Total</th></tr></thead>
          <tbody>
            {(cube.data ?? []).map((r) => (
              <tr key={`${r.cityId}-${r.applicationId}`}>
                <td>{r.country}</td><td>{r.city}</td><td>{r.application}</td>
                <td style={{ textAlign: 'right' }}>{r.count}</td>
              </tr>
            ))}
            {cube.data?.length === 0 && <tr><td colSpan={4} className="muted">Aucune donnée.</td></tr>}
          </tbody>
        </table></div>
      </div>
    </>
  );
}
