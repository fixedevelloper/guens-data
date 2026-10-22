'use client';
import { FormEvent, useState } from 'react';
import { api } from '@/lib/api';
import { City, Country } from '@/lib/types';
import { Badge, ErrorBox, errMsg, Field, Modal, PageHeader, useLoad } from '@/components/ui';

type CountryForm = { id?: number; isoCode: string; name: string; active: boolean };
type CityForm = { id?: number; countryId: number; name: string; active: boolean };

export default function CountriesPage() {
  const countries = useLoad(() => api<Country[]>('admin/countries'), []);
  const [filter, setFilter] = useState('');
  const cities = useLoad(() => api<City[]>('admin/cities', { query: { countryId: filter } }), [filter]);
  const [countryForm, setCountryForm] = useState<CountryForm | null>(null);
  const [cityForm, setCityForm] = useState<CityForm | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function run(fn: () => Promise<unknown>, after: () => void) {
    setError(null);
    try { await fn(); after(); } catch (e) { setError(errMsg(e)); }
  }
  const reloadAll = () => { countries.reload(); cities.reload(); };

  function saveCountry(e: FormEvent) {
    e.preventDefault();
    const f = countryForm!;
    const body = { isoCode: f.isoCode, name: f.name, active: f.active };
    run(() => f.id ? api(`admin/countries/${f.id}`, { method: 'PUT', body }) : api('admin/countries', { method: 'POST', body }),
      () => { setCountryForm(null); reloadAll(); });
  }
  function saveCity(e: FormEvent) {
    e.preventDefault();
    const f = cityForm!;
    const body = { countryId: f.countryId, name: f.name, active: f.active };
    run(() => f.id ? api(`admin/cities/${f.id}`, { method: 'PUT', body }) : api('admin/cities', { method: 'POST', body }),
      () => { setCityForm(null); reloadAll(); });
  }
  function remove(kind: 'countries' | 'cities', id: number, label: string) {
    if (!window.confirm(`Supprimer « ${label} » ?`)) return;
    run(() => api(`admin/${kind}/${id}`, { method: 'DELETE' }), reloadAll);
  }

  return (
    <>
      <PageHeader title="Pays & villes" subtitle="Référentiel géographique de la collecte" />
      <ErrorBox error={error ?? countries.error ?? cities.error} />

      <div className="card">
        <div className="toolbar">
          <h2 className="grow" style={{ margin: 0 }}>Pays</h2>
          <button className="primary" onClick={() => setCountryForm({ isoCode: '', name: '', active: true })}>+ Nouveau pays</button>
        </div>
        <div className="table-wrap"><table>
          <thead><tr><th>Code</th><th>Nom</th><th>Statut</th><th /></tr></thead>
          <tbody>
            {countries.data?.map((c) => (
              <tr key={c.id}>
                <td>{c.isoCode}</td><td>{c.name}</td>
                <td><Badge kind={c.active ? 'ok' : 'off'}>{c.active ? 'Actif' : 'Inactif'}</Badge></td>
                <td style={{ textAlign: 'right' }}>
                  <button className="sm" onClick={() => setCountryForm({ ...c })}>Modifier</button>{' '}
                  <button className="danger sm" onClick={() => remove('countries', c.id, c.name)}>Supprimer</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table></div>
      </div>

      <div className="card">
        <div className="toolbar">
          <h2 className="grow" style={{ margin: 0 }}>Villes</h2>
          <Field label="Pays">
            <select value={filter} onChange={(e) => setFilter(e.target.value)}>
              <option value="">Tous</option>
              {countries.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <button className="primary" disabled={!countries.data?.length}
            onClick={() => setCityForm({ countryId: Number(filter) || countries.data![0].id, name: '', active: true })}>
            + Nouvelle ville
          </button>
        </div>
        <div className="table-wrap"><table>
          <thead><tr><th>Ville</th><th>Pays</th><th>Statut</th><th /></tr></thead>
          <tbody>
            {cities.data?.map((c) => (
              <tr key={c.id}>
                <td>{c.name}</td><td>{c.countryName}</td>
                <td><Badge kind={c.active ? 'ok' : 'off'}>{c.active ? 'Active' : 'Inactive'}</Badge></td>
                <td style={{ textAlign: 'right' }}>
                  <button className="sm" onClick={() => setCityForm({ id: c.id, countryId: c.countryId, name: c.name, active: c.active })}>Modifier</button>{' '}
                  <button className="danger sm" onClick={() => remove('cities', c.id, c.name)}>Supprimer</button>
                </td>
              </tr>
            ))}
            {cities.data?.length === 0 && <tr><td colSpan={4} className="empty">Aucune ville.</td></tr>}
          </tbody>
        </table></div>
      </div>

      {countryForm && (
        <Modal title={countryForm.id ? 'Modifier le pays' : 'Nouveau pays'} onClose={() => setCountryForm(null)}>
          <form className="form" onSubmit={saveCountry}>
            <Field label="Code ISO (2 lettres)">
              <input value={countryForm.isoCode} maxLength={2} required
                onChange={(e) => setCountryForm({ ...countryForm, isoCode: e.target.value.toUpperCase() })} />
            </Field>
            <Field label="Nom">
              <input value={countryForm.name} required onChange={(e) => setCountryForm({ ...countryForm, name: e.target.value })} />
            </Field>
            <label className="row"><input type="checkbox" checked={countryForm.active}
              onChange={(e) => setCountryForm({ ...countryForm, active: e.target.checked })} /> Actif</label>
            <button className="primary">Enregistrer</button>
          </form>
        </Modal>
      )}
      {cityForm && (
        <Modal title={cityForm.id ? 'Modifier la ville' : 'Nouvelle ville'} onClose={() => setCityForm(null)}>
          <form className="form" onSubmit={saveCity}>
            <Field label="Pays">
              <select value={cityForm.countryId} onChange={(e) => setCityForm({ ...cityForm, countryId: Number(e.target.value) })}>
                {countries.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
            <Field label="Nom">
              <input value={cityForm.name} required onChange={(e) => setCityForm({ ...cityForm, name: e.target.value })} />
            </Field>
            <label className="row"><input type="checkbox" checked={cityForm.active}
              onChange={(e) => setCityForm({ ...cityForm, active: e.target.checked })} /> Active</label>
            <button className="primary">Enregistrer</button>
          </form>
        </Modal>
      )}
    </>
  );
}
