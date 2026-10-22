'use client';
import { FormEvent, useState } from 'react';

export default function LoginPage() {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setBusy(true);
    setError(null);
    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: f.get('username'), password: f.get('password') }),
      });
      if (!res.ok) {
        const d = await res.json().catch(() => ({}));
        setError(d.message ?? 'Connexion impossible.');
        return;
      }
      window.location.href = '/';
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login">
      <form className="card form" onSubmit={submit}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img className="login-logo" src="/assets/logo.png" alt="Guen's Data" />
        <div>
          <h1>Bon retour 👋</h1>
          <p className="muted" style={{ margin: '2px 0 0' }}>Collecter · Organiser · Valoriser — connectez-vous pour continuer</p>
        </div>
        {error && <div className="error" role="alert">{error}</div>}
        <label className="f">Identifiant<input name="username" autoComplete="username" required autoFocus /></label>
        <label className="f">Mot de passe<input name="password" type="password" autoComplete="current-password" required /></label>
        <button className="primary" disabled={busy}>{busy ? 'Connexion…' : 'Se connecter'}</button>
      </form>
    </div>
  );
}
