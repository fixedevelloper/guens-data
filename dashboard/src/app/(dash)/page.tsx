'use client';
import { useState } from 'react';
import { api } from '@/lib/api';
import { AppItem, ApplicationCount, Breakdown, CityCount, CountryCount, DayCount, Overview } from '@/lib/types';
import { BarList, ErrorBox, Field, Timeline, useLoad } from '@/components/ui';

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
      <h1>Tableau de bord</h1>
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

      <div className="grid kpis" style={{ marginBottom: 16 }}>
        {[
          ['Total', o?.total], ["Aujourd'hui", o?.today], ['7 derniers jours', o?.last7Days],
          ['30 derniers jours', o?.last30Days], ['Agents actifs', o?.activeAgents],
          ['Par scan', o?.byCaptureMode.SCAN], ['Saisie manuelle', o?.byCaptureMode.MANUAL],
        ].map(([l, v]) => (
          <div className="card kpi" key={String(l)} style={{ marginBottom: 0 }}>
            <div className="v">{v ?? '…'}</div><div className="l">{l}</div>
          </div>
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
