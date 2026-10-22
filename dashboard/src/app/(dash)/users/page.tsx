'use client';
import { FormEvent, useState } from 'react';
import { api } from '@/lib/api';
import { AppItem, City, Page, Role, UserItem } from '@/lib/types';
import { Badge, ErrorBox, errMsg, fmtDate, Field, Modal, PageHeader, Pager, Who, useLoad } from '@/components/ui';

export default function UsersPage() {
  const [q, setQ] = useState('');
  const [role, setRole] = useState('');
  const [active, setActive] = useState('');
  const [page, setPage] = useState(0);
  const list = useLoad(
    () => api<Page<UserItem>>('admin/users', { query: { q, role, active, page, size: 20 } }),
    [q, role, active, page],
  );
  const [creating, setCreating] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);

  return (
    <>
      <PageHeader title="Agents & appareils" subtitle="Comptes, appareils liés et affectations" />
      <ErrorBox error={list.error} />
      <div className="card">
        <div className="toolbar">
          <Field label="Recherche"><input value={q} placeholder="Nom ou identifiant"
            onChange={(e) => { setQ(e.target.value); setPage(0); }} /></Field>
          <Field label="Rôle">
            <select value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }}>
              <option value="">Tous</option><option value="AGENT">Agent</option><option value="ADMIN">Admin</option>
            </select>
          </Field>
          <Field label="Statut">
            <select value={active} onChange={(e) => { setActive(e.target.value); setPage(0); }}>
              <option value="">Tous</option><option value="true">Actifs</option><option value="false">Désactivés</option>
            </select>
          </Field>
          <span className="grow" />
          <button className="primary" onClick={() => setCreating(true)}>+ Nouvel utilisateur</button>
        </div>
        <div className="table-wrap"><table>
          <thead><tr><th>Nom</th><th>Identifiant</th><th>Rôle</th><th>Appareil</th><th>Dernière connexion</th><th>Statut</th></tr></thead>
          <tbody>
            {list.data?.content.map((u) => (
              <tr key={u.id} className="click" onClick={() => setOpenId(u.id)}>
                <td><Who name={u.fullName} sub={u.phone ?? undefined} /></td><td>{u.username}</td><td><Badge kind={u.role === 'ADMIN' ? 'warn' : undefined}>{u.role === 'ADMIN' ? 'Admin' : 'Agent'}</Badge></td>
                <td>{u.role === 'ADMIN' ? <span className="muted">—</span>
                  : u.deviceId ? <Badge kind="ok">{u.deviceModel ?? 'Lié'}</Badge> : <Badge>Non lié</Badge>}</td>
                <td>{fmtDate(u.lastLoginAt)}</td>
                <td>
                  {!u.active ? <Badge kind="off">Désactivé</Badge>
                    : u.lockedUntil && new Date(u.lockedUntil) > new Date() ? <Badge kind="warn">Verrouillé</Badge>
                    : <Badge kind="ok">Actif</Badge>}
                </td>
              </tr>
            ))}
            {list.data?.content.length === 0 && <tr><td colSpan={6} className="empty">Aucun utilisateur.</td></tr>}
          </tbody>
        </table></div>
        <Pager page={page} totalPages={list.data?.totalPages ?? 0} onChange={setPage} />
      </div>

      {creating && <CreateUser onClose={() => setCreating(false)} onDone={() => { setCreating(false); list.reload(); }} />}
      {openId !== null && <UserDetail id={openId} onClose={() => setOpenId(null)} onChanged={list.reload} />}
    </>
  );
}

function CreateUser({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [f, setF] = useState({ username: '', password: '', fullName: '', phone: '', role: 'AGENT' as Role });
  const [error, setError] = useState<string | null>(null);
  async function submit(e: FormEvent) {
    e.preventDefault();
    try {
      await api('admin/users', { method: 'POST', body: { ...f, phone: f.phone || null } });
      onDone();
    } catch (err) { setError(errMsg(err)); }
  }
  return (
    <Modal title="Nouvel utilisateur" onClose={onClose}>
      <ErrorBox error={error} />
      <form className="form" onSubmit={submit}>
        <Field label="Nom complet"><input required value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} /></Field>
        <div className="form2">
          <Field label="Identifiant"><input required minLength={3} value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} /></Field>
          <Field label="Téléphone"><input value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} /></Field>
        </div>
        <Field label="Mot de passe (8 caractères min.)">
          <input type="password" required minLength={8} autoComplete="new-password" value={f.password}
            onChange={(e) => setF({ ...f, password: e.target.value })} />
        </Field>
        <Field label="Rôle">
          <select value={f.role} onChange={(e) => setF({ ...f, role: e.target.value as Role })}>
            <option value="AGENT">Agent (application mobile)</option><option value="ADMIN">Administrateur</option>
          </select>
        </Field>
        <button className="primary">Créer</button>
      </form>
    </Modal>
  );
}

function UserDetail({ id, onClose, onChanged }: { id: number; onClose: () => void; onChanged: () => void }) {
  const user = useLoad(() => api<UserItem>(`admin/users/${id}`), [id]);
  const apps = useLoad(() => api<AppItem[]>('admin/applications'), []);
  const cities = useLoad(() => api<City[]>('admin/cities'), []);
  const [error, setError] = useState<string | null>(null);
  const [sel, setSel] = useState<Set<string> | null>(null);
  const u = user.data;

  // Initialise la sélection d'affectations une fois l'utilisateur chargé.
  if (u && sel === null) {
    setSel(new Set((u.assignments ?? []).map((a) => `${a.applicationId}:${a.cityId}`)));
  }

  async function act(fn: () => Promise<unknown>, reloadSel = false) {
    setError(null);
    try {
      await fn();
      if (reloadSel) setSel(null);
      user.reload();
      onChanged();
    } catch (e) { setError(errMsg(e)); }
  }
  function toggle(key: string) {
    const s = new Set(sel!);
    s.has(key) ? s.delete(key) : s.add(key);
    setSel(s);
  }
  function saveAssignments() {
    const assignments = [...sel!].map((k) => {
      const [applicationId, cityId] = k.split(':').map(Number);
      return { applicationId, cityId };
    });
    act(() => api(`admin/users/${id}/assignments`, { method: 'PUT', body: { assignments } }), true);
  }
  function resetPassword() {
    const pwd = window.prompt('Nouveau mot de passe (8 caractères minimum) :');
    if (!pwd) return;
    act(() => api(`admin/users/${id}/reset-password`, { method: 'POST', body: { newPassword: pwd } }));
  }

  const cityName = (cid: number) => cities.data?.find((c) => c.id === cid)?.name ?? `#${cid}`;
  const locked = u?.lockedUntil && new Date(u.lockedUntil) > new Date();

  return (
    <Modal title={u ? u.fullName : 'Utilisateur'} onClose={onClose} wide>
      <ErrorBox error={error ?? user.error} />
      {u && (
        <>
          <dl className="kv">
            <dt>Identifiant</dt><dd>{u.username} ({u.role})</dd>
            <dt>Téléphone</dt><dd>{u.phone ?? '—'}</dd>
            <dt>Statut</dt><dd>{u.active ? <Badge kind="ok">Actif</Badge> : <Badge kind="off">Désactivé</Badge>} {locked && <Badge kind="warn">Verrouillé jusqu’à {fmtDate(u.lockedUntil)}</Badge>}</dd>
            <dt>Dernière connexion</dt><dd>{fmtDate(u.lastLoginAt)}</dd>
            {u.role === 'AGENT' && (<>
              <dt>Appareil</dt>
              <dd>{u.deviceId ? <>{u.deviceModel ?? 'Appareil'} — lié le {fmtDate(u.deviceBoundAt)}<div className="muted" style={{ fontSize: 12 }}>{u.deviceId}</div></>
                : 'Aucun (sera lié à la prochaine connexion)'}</dd>
            </>)}
          </dl>

          <div className="row" style={{ margin: '16px 0' }}>
            <button onClick={() => act(() => api(`admin/users/${id}/status`, { method: 'PATCH', body: { active: !u.active } }))}>
              {u.active ? 'Désactiver' : 'Activer'}
            </button>
            {u.role === 'AGENT' && u.deviceId && (
              <button onClick={() => window.confirm("Libérer l'appareil ? L'agent sera déconnecté.") &&
                act(() => api(`admin/users/${id}/reset-device`, { method: 'POST' }))}>Libérer l’appareil</button>
            )}
            {locked && <button onClick={() => act(() => api(`admin/users/${id}/unlock`, { method: 'POST' }))}>Déverrouiller</button>}
            <button onClick={resetPassword}>Réinitialiser le mot de passe</button>
          </div>

          {u.role === 'AGENT' && sel && (
            <>
              <h2>Affectations (application × ville)</h2>
              <div className="checks">
                {apps.data?.filter((a) => a.cityIds.length > 0).map((a) => (
                  <div key={a.id}>
                    <strong>{a.name}</strong>{!a.active && <> <Badge kind="off">Inactive</Badge></>}
                    {a.cityIds.map((cid) => {
                      const key = `${a.id}:${cid}`;
                      return (
                        <label key={key}>
                          <input type="checkbox" checked={sel.has(key)} onChange={() => toggle(key)} /> {cityName(cid)}
                        </label>
                      );
                    })}
                  </div>
                ))}
                {apps.data?.every((a) => a.cityIds.length === 0) && (
                  <p className="muted">Aucune application déployée. Associez des villes à une application d’abord.</p>
                )}
              </div>
              <div className="row" style={{ marginTop: 12 }}>
                <button className="primary" onClick={saveAssignments}>Enregistrer les affectations ({sel.size})</button>
              </div>
            </>
          )}
        </>
      )}
    </Modal>
  );
}
