'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ReactNode, useEffect, useState } from 'react';

const icon = (d: ReactNode) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{d}</svg>
);

const NAV = [
  { href: '/', label: 'Tableau de bord', icon: icon(<><rect x="3" y="3" width="7" height="9" rx="1.5" /><rect x="14" y="3" width="7" height="5" rx="1.5" /><rect x="14" y="12" width="7" height="9" rx="1.5" /><rect x="3" y="16" width="7" height="5" rx="1.5" /></>) },
  { href: '/persons', label: 'Enregistrements', icon: icon(<><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8" /></>) },
  { href: '/users', label: 'Agents & appareils', icon: icon(<><rect x="6" y="2" width="12" height="20" rx="3" /><path d="M11 18h2" /></>) },
  { href: '/applications', label: 'Applications', icon: icon(<><path d="M12 2 2 7l10 5 10-5-10-5Z" /><path d="m2 17 10 5 10-5M2 12l10 5 10-5" /></>) },
  { href: '/countries', label: 'Pays & villes', icon: icon(<><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 0 1 16 0Z" /><circle cx="12" cy="10" r="3" /></>) },
];

export default function DashLayout({ children }: { children: ReactNode }) {
  const path = usePathname();
  const [open, setOpen] = useState(false);
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open]);
  const current = NAV.find((n) => (n.href === '/' ? path === '/' : path.startsWith(n.href)));
  async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    window.location.href = '/login';
  }
  return (
    <div className="shell">
      <nav id="side-nav" className={`side${open ? ' open' : ''}`} aria-label="Navigation">
        <div className="brand">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/assets/logo.png" alt="Guen's Data" />
        </div>
        <div className="nav-label">Navigation</div>
        {NAV.map((n) => (
          <Link key={n.href} href={n.href} onClick={() => setOpen(false)}
            className={(n.href === '/' ? path === '/' : path.startsWith(n.href)) ? 'active' : ''}>
            {n.icon}{n.label}
          </Link>
        ))}
        <div className="spacer" />
        <button className="logout" onClick={logout}>
          {icon(<><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" /></>)}
          Se déconnecter
        </button>
      </nav>
      {open && <div className="scrim" onClick={() => setOpen(false)} />}
      <div className="content">
        <header className="topbar">
          <button className="burger" aria-label="Menu" aria-expanded={open} aria-controls="side-nav" onClick={() => setOpen((o) => !o)}>
            {icon(<path d="M4 6h16M4 12h16M4 18h16" />)}
          </button>
          <span className="crumb">Administration / <b>{current?.label ?? ''}</b></span>
          <span className="live"><i />Système opérationnel</span>
        </header>
        <main className="main" key={path}>{children}</main>
      </div>
    </div>
  );
}
