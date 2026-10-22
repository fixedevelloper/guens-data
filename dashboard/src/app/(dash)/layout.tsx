'use client';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

const NAV = [
  { href: '/', label: 'Tableau de bord' },
  { href: '/persons', label: 'Enregistrements' },
  { href: '/users', label: 'Agents & appareils' },
  { href: '/applications', label: 'Applications' },
  { href: '/countries', label: 'Pays & villes' },
];

export default function DashLayout({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  async function logout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    window.location.href = '/login';
  }
  return (
    <div className="shell">
      <nav className="side" aria-label="Navigation">
        <div className="brand">Collecte terrain</div>
        {NAV.map((n) => (
          <Link key={n.href} href={n.href}
            className={(n.href === '/' ? path === '/' : path.startsWith(n.href)) ? 'active' : ''}>
            {n.label}
          </Link>
        ))}
        <div className="spacer" />
        <button onClick={logout}>Se déconnecter</button>
      </nav>
      <main className="main">{children}</main>
    </div>
  );
}
