'use client';
import { useState } from 'react';
import { api, documentUrl, exportUrl, signatureUrl } from '@/lib/api';
import { AppItem, City, Country, Page, PersonDetail, PersonSummary } from '@/lib/types';
import { Badge, ErrorBox, fmtDate, Field, Modal, Pager, useLoad } from '@/components/ui';

export default function PersonsPage() {
  const [f, setF] = useState({ q: '', countryId: '', cityId: '', applicationId: '', captureMode: '', from: '', to: '' });
  const [page, setPage] = useState(0);
  const set = (patch: Partial<typeof f>) => { setF({ ...f, ...patch }); setPage(0); };

  const countries = useLoad(() => api<Country[]>('admin/countries'), []);
  const apps = useLoad(() => api<AppItem[]>('admin/applications'), []);
  const cities = useLoad(() => api<City[]>('admin/cities', { query: { countryId: f.countryId } }), [f.countryId]);
  const list = useLoad(
    () => api<Page<PersonSummary>>('admin/persons', { query: { ...f, page, size: 20 } }),
    [f.q, f.countryId, f.cityId, f.applicationId, f.captureMode, f.from, f.to, page],
  );
  const [openId, setOpenId] = useState<number | null>(null);

  return (
    <>
      <h1>Enregistrements</h1>
      <ErrorBox error={list.error} />
      <div className="card">
        <div className="toolbar">
          <Field label="Nom / prénom"><input value={f.q} onChange={(e) => set({ q: e.target.value })} /></Field>
          <Field label="Pays">
            <select value={f.countryId} onChange={(e) => set({ countryId: e.target.value, cityId: '' })}>
              <option value="">Tous</option>
              {countries.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="Ville">
            <select value={f.cityId} onChange={(e) => set({ cityId: e.target.value })}>
              <option value="">Toutes</option>
              {cities.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="Application">
            <select value={f.applicationId} onChange={(e) => set({ applicationId: e.target.value })}>
              <option value="">Toutes</option>
              {apps.data?.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </Field>
          <Field label="Mode">
            <select value={f.captureMode} onChange={(e) => set({ captureMode: e.target.value })}>
              <option value="">Tous</option><option value="SCAN">Scan</option><option value="MANUAL">Manuel</option>
            </select>
          </Field>
          <Field label="Du"><input type="date" value={f.from} onChange={(e) => set({ from: e.target.value })} /></Field>
          <Field label="Au"><input type="date" value={f.to} onChange={(e) => set({ to: e.target.value })} /></Field>
          <span className="grow" />
          <a href={exportUrl(f)}><button type="button">Exporter CSV</button></a>
        </div>

        <p className="muted" style={{ marginTop: 0 }}>
          {list.data ? `${list.data.totalElements} enregistrement(s)` : 'Chargement…'} — les numéros de pièce sont masqués ;
          l’ouverture d’une fiche est tracée.
        </p>
        <div className="table-wrap"><table>
          <thead><tr><th>Personne</th><th>Pièce</th><th>Application</th><th>Lieu</th><th>Agent</th><th>Mode</th><th>Reçu le</th></tr></thead>
          <tbody>
            {list.data?.content.map((p) => (
              <tr key={p.id} className="click" onClick={() => setOpenId(p.id)}>
                <td>{p.lastName} {p.firstName}</td>
                <td>{p.documentType ? `${p.documentType} ${p.documentNumberMasked ?? ''}` : <span className="muted">—</span>}
                  {p.hasDocumentImage && <> <Badge>image</Badge></>}</td>
                <td>{p.applicationName}</td><td>{p.cityName}, {p.countryName}</td><td>{p.agentName}</td>
                <td><Badge>{p.captureMode === 'SCAN' ? 'Scan' : 'Manuel'}</Badge></td>
                <td>{fmtDate(p.createdAt)}</td>
              </tr>
            ))}
            {list.data?.content.length === 0 && <tr><td colSpan={7} className="muted">Aucun résultat.</td></tr>}
          </tbody>
        </table></div>
        <Pager page={page} totalPages={list.data?.totalPages ?? 0} onChange={setPage} />
      </div>
      {openId !== null && <PersonModal id={openId} onClose={() => setOpenId(null)} />}
    </>
  );
}

function PersonModal({ id, onClose }: { id: number; onClose: () => void }) {
  const { data: p, error } = useLoad(() => api<PersonDetail>(`admin/persons/${id}`), [id]);
  const rows: [string, React.ReactNode][] = p ? [
    ['Nom', `${p.lastName} ${p.firstName}`],
    ['Sexe', p.gender ?? '—'],
    ['Naissance', `${p.birthDate ?? '—'}${p.birthPlace ? ' à ' + p.birthPlace : ''}`],
    ['Nationalité', p.nationality ?? '—'],
    ['Téléphone', p.phone ?? '—'], ['Email', p.email ?? '—'], ['Adresse', p.address ?? '—'],
    ['Pièce', p.documentType ? `${p.documentType} — ${p.documentNumber}` : '—'],
    ['Délivrance / expiration', `${p.documentIssueDate ?? '—'} / ${p.documentExpiryDate ?? '—'}`],
    ['Application', p.applicationName], ['Lieu', `${p.cityName}, ${p.countryName}`],
    ['Agent', `${p.agentName} (appareil ${p.deviceId})`],
    ['Mode', p.captureMode === 'SCAN' ? `Scan (confiance OCR ${p.ocrConfidence ?? '—'} %)` : 'Saisie manuelle'],
    ['Saisi le', fmtDate(p.capturedAt)], ['Reçu le', fmtDate(p.createdAt)],
    ['Données spécifiques', p.extraData ? <code>{JSON.stringify(p.extraData)}</code> : '—'],
  ] : [];
  return (
    <Modal title="Fiche" onClose={onClose} wide>
      <ErrorBox error={error} />
      {p && (
        <div className="grid cols2">
          <dl className="kv" style={{ gridTemplateColumns: '150px 1fr', alignContent: 'start' }}>
            {rows.map(([k, v]) => (<><dt key={`k${k}`}>{k}</dt><dd key={`v${k}`}>{v}</dd></>))}
          </dl>
          <div>
            {p.hasDocumentImage
              // eslint-disable-next-line @next/next/no-img-element
              ? <img className="doc" src={documentUrl(p.id)} alt="Pièce d'identité" />
              : <p className="muted">Aucune image de pièce.</p>}
            <h4 style={{ margin: '16px 0 6px' }}>Signature</h4>
            {p.hasSignature
              // eslint-disable-next-line @next/next/no-img-element
              ? <img className="doc" src={signatureUrl(p.id)} alt="Signature" style={{ background: '#fff' }} />
              : <p className="muted">Aucune signature.</p>}
          </div>
        </div>
      )}
    </Modal>
  );
}
