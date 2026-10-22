'use client';
import { FormEvent, useState } from 'react';
import { api } from '@/lib/api';
import { AppItem, City } from '@/lib/types';
import { Badge, ErrorBox, errMsg, Field, Modal, PageHeader, useLoad } from '@/components/ui';

type AppForm = { id?: number; code: string; name: string; description: string; active: boolean };

export default function ApplicationsPage() {
  const apps = useLoad(() => api<AppItem[]>('admin/applications'), []);
  const cities = useLoad(() => api<City[]>('admin/cities'), []);
  const [form, setForm] = useState<AppForm | null>(null);
  const [deploy, setDeploy] = useState<{ app: AppItem; selected: Set<number> } | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function run(fn: () => Promise<unknown>, after: () => void) {
    setError(null);
    try { await fn(); after(); } catch (e) { setError(errMsg(e)); }
  }

  function save(e: FormEvent) {
    e.preventDefault();
    const f = form!;
    const body = { code: f.code, name: f.name, description: f.description || null, active: f.active };
    run(() => f.id ? api(`admin/applications/${f.id}`, { method: 'PUT', body }) : api('admin/applications', { method: 'POST', body }),
      () => { setForm(null); apps.reload(); });
  }
  function saveCities() {
    const d = deploy!;
    run(() => api(`admin/applications/${d.app.id}/cities`, { method: 'PUT', body: { cityIds: [...d.selected] } }),
      () => { setDeploy(null); apps.reload(); });
  }
  function remove(a: AppItem) {
    if (!window.confirm(`Supprimer l'application « ${a.name} » ?`)) return;
    run(() => api(`admin/applications/${a.id}`, { method: 'DELETE' }), apps.reload);
  }
  function toggle(id: number) {
    const s = new Set(deploy!.selected);
    s.has(id) ? s.delete(id) : s.add(id);
    setDeploy({ ...deploy!, selected: s });
  }

  const cityName = (id: number) => cities.data?.find((c) => c.id === id)?.name ?? `#${id}`;
  const byCountry = new Map<string, City[]>();
  cities.data?.forEach((c) => byCountry.set(c.countryName, [...(byCountry.get(c.countryName) ?? []), c]));

  return (
    <>
      <PageHeader title="Applications" subtitle="Formulaires de collecte et villes de déploiement" />
      <ErrorBox error={error ?? apps.error ?? cities.error} />
      <div className="card">
        <div className="toolbar">
          <span className="grow muted">Une application doit être déployée dans une ville avant d’y être affectée à un agent.</span>
          <button className="primary" onClick={() => setForm({ code: '', name: '', description: '', active: true })}>+ Nouvelle application</button>
        </div>
        <div className="table-wrap"><table>
          <thead><tr><th>Code</th><th>Nom</th><th>Villes</th><th>Statut</th><th /></tr></thead>
          <tbody>
            {apps.data?.map((a) => (
              <tr key={a.id}>
                <td>{a.code}</td>
                <td>{a.name}<div className="muted">{a.description}</div></td>
                <td>{a.cityIds.length ? a.cityIds.map(cityName).join(', ') : <span className="muted">Aucune</span>}</td>
                <td><Badge kind={a.active ? 'ok' : 'off'}>{a.active ? 'Active' : 'Inactive'}</Badge></td>
                <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                  <button className="sm" onClick={() => setDeploy({ app: a, selected: new Set(a.cityIds) })}>Villes</button>{' '}
                  <button className="sm" onClick={() => setForm({ id: a.id, code: a.code, name: a.name, description: a.description ?? '', active: a.active })}>Modifier</button>{' '}
                  <button className="danger sm" onClick={() => remove(a)}>Supprimer</button>
                </td>
              </tr>
            ))}
            {apps.data?.length === 0 && <tr><td colSpan={5} className="empty">Aucune application.</td></tr>}
          </tbody>
        </table></div>
      </div>

      {form && (
        <Modal title={form.id ? "Modifier l'application" : 'Nouvelle application'} onClose={() => setForm(null)}>
          <form className="form" onSubmit={save}>
            <Field label="Code (lettres, chiffres, _ -)">
              <input value={form.code} required pattern="[A-Za-z0-9_-]+" onChange={(e) => setForm({ ...form, code: e.target.value })} />
            </Field>
            <Field label="Nom"><input value={form.name} required onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
            <Field label="Description">
              <textarea rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </Field>
            <label className="row"><input type="checkbox" checked={form.active}
              onChange={(e) => setForm({ ...form, active: e.target.checked })} /> Active</label>
            <button className="primary">Enregistrer</button>
          </form>
        </Modal>
      )}

      {deploy && (
        <Modal title={`Villes — ${deploy.app.name}`} onClose={() => setDeploy(null)}>
          <div className="checks">
            {[...byCountry.entries()].map(([country, list]) => (
              <div key={country}>
                <strong>{country}</strong>
                {list.map((c) => (
                  <label key={c.id}><input type="checkbox" checked={deploy.selected.has(c.id)} onChange={() => toggle(c.id)} /> {c.name}</label>
                ))}
              </div>
            ))}
            {byCountry.size === 0 && <p className="muted">Créez d’abord des villes.</p>}
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <button className="primary" onClick={saveCities}>Enregistrer ({deploy.selected.size})</button>
          </div>
        </Modal>
      )}
    </>
  );
}
