import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: "Guen's Data — Administration",
  icons: { icon: '/assets/icon.png', apple: '/assets/icon.png' },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body>{children}</body>
    </html>
  );
}
