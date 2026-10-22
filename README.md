# Plateforme de collecte terrain

Collecte d'informations sur des personnes par des agents de terrain, avec supervision par un administrateur.

| Composant | Dossier | Technologie |
|---|---|---|
| API | `field-collect-api/` | Spring Boot 3.3 (Java 21), MySQL 8, MinIO |
| Dashboard admin | `dashboard/` | Next.js 16 (React 19, TypeScript) |
| App agents | `mobile/` | Flutter 3.44 (Android / iOS) |

```
 App Flutter ──HTTPS──┐
 (agents)             ├──► API Spring Boot ──► MySQL
 Dashboard Next.js ───┘      │
 (admins, via serveur Next)  └──► MinIO : images de pièces et signatures (chiffrées)
```

## Règles métier

1. **L'administrateur crée les utilisateurs.** Il n'y a pas d'inscription.
2. **Un agent = un seul appareil.** L'appareil est lié à la première connexion ; tout autre appareil est refusé
   (`DEVICE_MISMATCH`). L'identifiant est revérifié à **chaque requête** : libérer l'appareil coupe
   immédiatement les sessions. L'administrateur peut le libérer (`reset-device`).
3. **Périmètre.** L'agent ne voit que les couples *application × ville* qui lui sont affectés.
   Une application doit être déployée dans une ville avant d'y être affectée.
4. **Saisie.** Accueil (compteurs personnels) → pays → ville → application, puis scan de pièce (caméra + OCR, lecture MRZ) ou saisie manuelle.
   Formulaire en 4 étapes ; **obligatoires** : nom, prénom(s), téléphone, email, signature numérique (PNG, stockée chiffrée dans MinIO).
5. **Confidentialité.** Après envoi, l'agent n'a plus aucun accès aux données : aucune route de lecture n'existe
   pour son rôle (garanti côté serveur, pas seulement dans l'interface).
6. **Statistiques** agrégées par pays, ville et application, avec évolution quotidienne.

## Démarrage rapide (développement)

Prérequis : Java 21, Maven, Node 20.9+, Docker, Flutter.

```bash
# 1. Base de données
docker run -d --name fc-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=field_collect \
  -p 3306:3306 mysql:8.4

# 2. API (http://localhost:8080) — Flyway crée le schéma ; admin initial : admin / Admin#12345
cd field-collect-api && mvn spring-boot:run

# 3. Dashboard (http://localhost:3000)
cd dashboard && cp .env.example .env.local && npm install && npm run dev

# 4. App mobile (émulateur Android -> backend local)
cd mobile && flutter pub get && flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Documentation interactive de l'API (hors production) : `http://localhost:8080/swagger-ui.html`.

### Premier scénario

1. Dashboard → **Pays & villes** : créer un pays et une ville.
2. **Applications** : créer une application, puis *Villes* pour la déployer.
3. **Agents & appareils** : créer un agent, ouvrir sa fiche, cocher ses affectations.
4. Sur le téléphone : connexion de l'agent (l'appareil est lié), choix du lieu, enregistrement.
5. Dashboard → **Enregistrements** et **Tableau de bord**.

## Configuration

Toutes les variables sont lues par l'API. En profil `prod`, **aucune n'a de valeur par défaut** et l'API refuse de
démarrer avec les valeurs de développement.

| Variable | Rôle | Remarque |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Connexion MySQL | |
| `JWT_SECRET` | Signature des jetons | 32 caractères minimum |
| `AES_KEY_BASE64` | Chiffrement AES-256 des numéros de pièce et des images | `openssl rand -base64 32` — **ne plus changer** |
| `HASH_PEPPER` | Empreinte des numéros de pièce (détection de doublons) | 16 caractères min. — **ne plus changer** |
| `ADMIN_USER`, `ADMIN_PASSWORD` | Premier administrateur (créé s'il n'existe pas) | mot de passe 12 caractères min. |
| `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` | Stockage MinIO des images de pièces et signatures | obligatoires en `prod` ; sans `MINIO_ACCESS_KEY` (dev/tests) repli disque `DOCS_DIR` |
| `MINIO_PUBLIC_URL` | URL publique MinIO si différente de l'endpoint | optionnel ; les fichiers sont servis via l'API, jamais directement |
| `CORS_ORIGINS` | Origines autorisées | |
| `SPRING_PROFILES_ACTIVE=prod` | Profil production | |

Dashboard : `BACKEND_URL` (URL de l'API, appelée uniquement côté serveur Next.js).
Mobile : `--dart-define=API_BASE_URL=...` (HTTPS obligatoire hors build debug).

## API (résumé)

Base `/api/v1`. Détail complet : Swagger en développement.

| Zone | Routes |
|---|---|
| Auth (public) | `POST /auth/login`, `/auth/refresh`, `/auth/logout` ; `GET /auth/me` |
| Mobile (AGENT) | `GET /mobile/countries`, `/mobile/countries/{id}/cities`, `/mobile/cities/{id}/applications` ; `POST /mobile/persons`, `/mobile/persons/{uuid}/document` ; compteurs de l'agent : `GET /mobile/stats`, `/mobile/stats/daily?days=`, `/mobile/stats/day?date=` |
| Admin (ADMIN) | `/admin/countries`, `/cities`, `/applications` (+ `/{id}/cities`), `/users` (+ `status`, `assignments`, `reset-device`, `reset-password`, `unlock`) |
| Données (ADMIN) | `GET /admin/persons`, `/{id}`, `/{id}/document`, `/export` ; `GET /admin/stats/overview`, `by-country`, `by-city`, `by-application`, `breakdown`, `timeline` |

Les agents s'authentifient avec le header `X-Device-Id`, vérifié à chaque appel.
Erreurs : `{ "code": "...", "message": "..." }`.

## Sécurité

- Mots de passe BCrypt ; JWT d'accès courts (30 min) ; refresh tokens stockés **hachés** et à rotation.
- Verrouillage du compte après 5 échecs (15 min) ; limite de 20 appels/min/IP sur login et refresh.
- Numéros de pièce chiffrés AES-GCM ; images de pièces chiffrées sur disque, nom aléatoire, type vérifié par signature.
- Doublons détectés par empreinte HMAC (normalisée : `AB-123 456` = `ab123456`).
- Journal d'audit : actions d'administration, consultations de fiches et d'images, exports, connexions échouées.
- Export CSV : numéro de pièce masqué, protection contre l'injection de formules Excel.
- Dashboard : jetons dans des cookies `httpOnly` (jamais accessibles au JavaScript), proxy limité à `admin/**`.
- App mobile : stockage sécurisé, `FLAG_SECURE` sur Android, aucune copie locale après envoi réussi.

## Tests

```bash
cd field-collect-api && mvn test        # 30 tests (H2 en mémoire)
cd mobile && flutter test               # 17 tests
cd dashboard && npx tsc --noEmit && npm run build
```

Le schéma Flyway et le profil `prod` ont aussi été exécutés contre un MySQL 8.4 réel.

## Déploiement

`docker-compose.yml` (racine) lance MySQL et l'API ; copier `.env.example` en `.env` et renseigner **toutes** les valeurs.

> Le `Dockerfile` de l'API n'a pas encore été validé de bout en bout, et le dashboard n'a pas de `Dockerfile`.
> L'API écoute sur `127.0.0.1:8080` : placer un reverse proxy **TLS** devant (nginx, Traefik…).

### Exploitation

- **Sauvegardes** : base MySQL **et** volume `DOCS_DIR`, ainsi que les secrets `AES_KEY_BASE64` / `HASH_PEPPER`
  (les perdre rend numéros et images illisibles).
- **Agent bloqué** : fiche utilisateur → *Déverrouiller* (trop d'échecs) ou *Libérer l'appareil* (nouveau téléphone).
- **Rotation des secrets** : `JWT_SECRET` peut changer (reconnexion de tous) ; `AES_KEY_BASE64` et `HASH_PEPPER`
  nécessitent une migration des données.

## Limites connues

- Limiteur de débit en mémoire : valable pour une seule instance de l'API.
- Un identifiant d'appareil Android change après réinitialisation d'usine : prévoir la libération par l'admin.
- Export CSV sans plafond de lignes.
- Dates et statistiques journalières en UTC.
- iOS configuré mais non compilé ; qualité de l'OCR à valider sur de vraies pièces.
- Le compte `admin` peut être verrouillé à distance par des essais répétés (15 min).
