# UniTrade — Student-to-Student Marketplace

UniTrade is a full-stack marketplace where students buy and sell second-hand textbooks, calculators,
electronics, lab equipment, stationery and project components.

- **Frontend:** HTML5, CSS3, vanilla JavaScript (ES modules, Fetch API) — served by Spring Boot
- **Backend:** Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA / Hibernate, Spring Security, Bean Validation
- **Database:** MySQL 8 with Flyway migrations
- **Build:** Maven

One process serves both the pages and the REST API: start Spring Boot, open <http://localhost:8080>.

**Deploy it live:** see [DEPLOY.md](DEPLOY.md). It covers GitHub, Railway, MySQL, the photo volume, a public URL and a smoke test.
The repository includes a `Dockerfile`, `railway.json`, a `prod` profile, the Maven Wrapper and a GitHub Actions
workflow that builds and tests the app against MySQL on every push.

---

## Contents

1. [Project structure](#1-project-structure)
2. [Windows setup (step by step)](#2-windows-setup-step-by-step)
3. [Frontend pages](#3-frontend-pages)
4. [REST API](#4-rest-api)
5. [Database schema](#5-database-schema)
6. [Security design](#6-security-design)
7. [Tests](#7-tests)
8. [Manual testing checklist](#8-manual-testing-checklist)
9. [Troubleshooting](#9-troubleshooting)
10. [Production deployment notes](#10-production-deployment-notes)
11. [Known limitations](#11-known-limitations)

---

## 1. Project structure

```
unitrade/
├── pom.xml
├── README.md
├── .gitignore
├── .env.example                      ← copy to .env and fill in
├── src/main/java/com/unitrade/
│   ├── UniTradeApplication.java
│   ├── config/        SecurityConfig, DevDataSeeder
│   ├── controller/    Auth, User, Product, Image, Wishlist, Inquiry, Admin, Meta controllers
│   ├── dto/           Request/response records (entities are never serialized directly)
│   ├── entity/        JPA entities + enums (User, Product, ProductImage, WishlistItem,
│   │                  Inquiry, InquiryMessage, ListingReport, …)
│   ├── exception/     ApiException, ApiError, GlobalExceptionHandler
│   ├── repository/    Spring Data JPA repositories
│   ├── security/      UserPrincipal, user details service, CurrentUserService,
│   │                  login throttling, JSON 401/403 handlers
│   └── service/       Business logic + ownership checks, image storage, search criteria
├── src/main/resources/
│   ├── application.properties
│   ├── db/migration/V1__create_schema.sql
│   └── static/
│       ├── index.html  marketplace.html  product.html  login.html  register.html
│       ├── sell.html   dashboard.html    wishlist.html inquiries.html admin.html
│       ├── css/styles.css
│       ├── js/api.js   js/ui.js   js/pages/*.js   (one script per page)
│       └── images/     logo, hero illustration, category placeholders
├── src/test/
│   ├── java/com/unitrade/   Integration tests (MockMvc + real MySQL)
│   └── resources/application-test.properties
└── uploads/            ← created at runtime for product photos (git-ignored)
```

---

## 2. Windows setup (step by step)

All commands are for **PowerShell** opened in the project folder (in VS Code: *Terminal → New Terminal*).

### 2.1 Install or verify Java 21, Maven and MySQL

```powershell
java -version      # must show version 21 (or newer)
mvn -version       # Maven 3.9+ ; "Java version" in its output must also be 21+
mysql --version    # MySQL 8.0+
```

If something is missing:

| Tool | How to install on Windows |
|------|---------------------------|
| Java 21 (LTS) | `winget install EclipseAdoptium.Temurin.21.JDK` — or download "Temurin 21 JDK (.msi)" from adoptium.net and tick *Set JAVA_HOME*. |
| Maven | Download the *Binary zip archive* from maven.apache.org/download.cgi, extract it to e.g. `C:\tools\apache-maven-3.9.x`, then add `C:\tools\apache-maven-3.9.x\bin` to **Path** (Start → "Edit the system environment variables" → Environment Variables). |
| MySQL 8 | Download **MySQL Installer for Windows** from dev.mysql.com/downloads/installer, choose *Server only* (or *Developer Default*), set a root password, keep port 3306 and "Start MySQL Server at System Startup". |

Open a **new** terminal after changing Path. If `mysql` is not recognised, add
`C:\Program Files\MySQL\MySQL Server 8.0\bin` to Path or use the *MySQL 8.0 Command Line Client* from the Start menu.

*Maven Wrapper:* the project ships the official Maven Wrapper, so you can skip installing Maven and use
`.\mvnw.cmd` (Windows) or `./mvnw` instead of `mvn` in every command below. It downloads Maven on first use.

VS Code extensions that help: *Extension Pack for Java* and *Spring Boot Extension Pack*.

### 2.2 Create the database and a dedicated MySQL user

```powershell
mysql -u root -p
```

Then, at the `mysql>` prompt (choose your own strong password):

```sql
CREATE DATABASE unitrade CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'unitrade'@'localhost' IDENTIFIED BY 'Choose-A-Strong-Password-1';
GRANT ALL PRIVILEGES ON unitrade.* TO 'unitrade'@'localhost';
GRANT ALL PRIVILEGES ON unitrade_test.* TO 'unitrade'@'localhost';   -- for automated tests
FLUSH PRIVILEGES;
EXIT;
```

You do **not** create tables yourself: Flyway runs `V1__create_schema.sql` automatically on first start.

### 2.3 Configure credentials safely

```powershell
Copy-Item .env.example .env
code .env
```

Set at least `DB_PASSWORD` to the password from step 2.2. `.env` is listed in `.gitignore`, so it is never committed.
For local demo data keep `SEED_DEMO_DATA=true`. To get an admin account, set
`SEED_ADMIN_EMAIL` and `SEED_ADMIN_PASSWORD` (12+ characters) — there is no built-in default admin.

You can use real environment variables instead of `.env` (they take priority):

```powershell
$env:DB_PASSWORD = "Choose-A-Strong-Password-1"
```

> Values in `.env` are read as Java properties: don't wrap them in quotes and avoid backslashes.

### 2.4 Build the backend

```powershell
mvn clean package -DskipTests
```

The first build downloads dependencies (a few minutes). The result is `target\unitrade-1.0.0.jar`.

### 2.5 Start Spring Boot

Either run directly from source:

```powershell
mvn spring-boot:run
```

or run the built jar (from the project folder, so `.env` is found):

```powershell
java -jar target\unitrade-1.0.0.jar
```

Wait for `Started UniTradeApplication`. On the first start you should also see Flyway applying
`V1 - create schema` and, with seeding enabled, `Inserted 13 demo listings`.

### 2.6 Open the frontend

Browse to **<http://localhost:8080>**. All pages are served by the same Spring Boot process.

### 2.7 Register a user and create a listing

1. Click **Sign up**, fill in the form, choose a campus, submit → you're sent to the login page.
2. **Log in** with the same email and password → you land on your dashboard.
3. Click **+ Sell an item**, fill in the form, optionally add photos (JPEG/PNG/WebP ≤ 5 MB), click **Publish listing**.
4. You are taken to the new listing page. Open **Marketplace** — your listing is first under *Newest first*.

### 2.8 Verify the listing is stored in MySQL

```powershell
mysql -u unitrade -p unitrade
```

```sql
SELECT id, title, price, status, seller_id, created_at FROM products ORDER BY id DESC LIMIT 5;
SELECT id, full_name, email, role, LEFT(password_hash, 7) AS hash_prefix FROM users ORDER BY id DESC LIMIT 5;
SELECT product_id, stored_filename, content_type FROM product_images ORDER BY id DESC LIMIT 5;
```

The password column contains a BCrypt hash (`$2a$12$…`), never the plain password. Uploaded files
are in the `uploads\` folder with random names that match `stored_filename`. Restart the app — the
listing is still there.

### 2.9 Test authentication and authorization

- Log out and open <http://localhost:8080/dashboard.html> → you are redirected to login.
- Register a **second** user in a private/incognito window and open the first user's listing:
  you see *Message the seller* and *Save to wishlist*, but no Edit/Delete buttons.
- Try to bypass the UI: on the second user's tab, open DevTools (F12) → Console and run
  (replace `1` with the first user's listing id):

  ```js
  const t = await (await fetch('/api/auth/csrf')).json();
  (await fetch('/api/products/1', { method: 'DELETE', headers: { [t.headerName]: t.token } })).status
  ```

  The result is **403** — the server checks ownership, so changing IDs in URLs does not help.
- Open <http://localhost:8080/admin.html> as a normal student → *Administrators only*;
  `fetch('/api/admin/stats').then(r => r.status)` returns **403** (401 when logged out).
- Log in as the admin from `SEED_ADMIN_EMAIL` → the **Admin** link appears; you can resolve reports,
  hide/restore/delete listings and suspend/reinstate students.

### 2.10 Run the automated tests

Tests use a separate database `unitrade_test` (created automatically; permissions granted in step 2.2)
and roll back every test, so your real data is untouched.

```powershell
mvn test
```

---

## 3. Frontend pages

| Page | File | What it does (all data via the REST API) |
|------|------|------------------------------------------|
| Home | `index.html` | Hero search, category tiles, 8 newest listings (`GET /api/products`), how-it-works. |
| Marketplace | `marketplace.html` | Search box, category/campus/condition/price filters, sort, pagination; state kept in the URL; wishlist hearts. |
| Product details | `product.html?id=` | Photo gallery, price, condition, campus, seller's public name; owner sees Edit / Mark sold / Delete; other students can wishlist, message the seller or report the listing. |
| Register | `register.html` | Client-side validation mirroring server rules; server field errors shown inline. |
| Login | `login.html` | Creates a server session; supports `?next=` redirect (same-site only). |
| Sell / Edit | `sell.html`, `sell.html?id=` | Create or update a listing; photo picker with previews, type/size checks, remove existing photos. |
| Dashboard | `dashboard.html` | Profile editing, listing stats, all own listings with view/edit/sold/relist/delete. |
| Wishlist | `wishlist.html` | Saved listings with remove buttons. |
| Inbox | `inquiries.html` | Received (as seller) / Sent (as buyer) tabs, threaded messages, reply, close. |
| Admin | `admin.html` | Stats, reported listings with resolve dialog (optionally hide listing), listing moderation, user suspension. |

Shared code: `js/api.js` (fetch wrapper, CSRF handling, error mapping for 400/401/403/404/409/413/429/500)
and `js/ui.js` (layout, safe DOM builder, formatting, toasts, dialogs, product cards).
Success messages are shown only after the server confirms the operation.

---

## 4. REST API

All responses are JSON. Errors use one shape:
`{ "timestamp", "status", "code", "message", "path", "fieldErrors"? }`.
State-changing requests (POST/PUT/PATCH/DELETE) need the CSRF header from `GET /api/auth/csrf`.

### Authentication & profile

| Method | URL | Access | Description |
|--------|-----|--------|-------------|
| GET | `/api/auth/csrf` | public | `{ headerName, token }` for state-changing requests |
| POST | `/api/auth/register` | public | Register a student → 201 profile (409 if email exists) |
| POST | `/api/auth/login` | public | `{ email, password }` → starts session (401 wrong credentials, 403 suspended, 429 too many attempts) |
| POST | `/api/auth/logout` | any | Invalidates the session → 204 |
| GET | `/api/auth/me` | user | Current user's private profile (401 if not logged in) |
| GET | `/api/auth/session` | public | `{ authenticated, user }`, always 200 (used by the navigation) |
| GET / PUT | `/api/users/me` | user | Read / update name, campus, phone |
| GET | `/api/users/me/products` | user | All own listings, including sold and hidden |

### Listings

| Method | URL | Access | Description |
|--------|-----|--------|-------------|
| GET | `/api/products` | public | Search available listings. Params: `q`, `category`, `campus`, `condition`, `minPrice`, `maxPrice`, `sort` (`newest`, `oldest`, `price_asc`, `price_desc`), `page`, `size` (≤ 48) |
| GET | `/api/products/{id}` | public | Listing detail (hidden listings: owner/admin only, else 404) |
| POST | `/api/products` | user | Create → 201 |
| PUT | `/api/products/{id}` | owner/admin | Update |
| DELETE | `/api/products/{id}` | owner/admin | Delete → 204 (photos, wishlist entries, inquiries and reports cascade) |
| PATCH | `/api/products/{id}/status` | owner/admin | `{ status: "SOLD" \| "AVAILABLE" }` |
| POST | `/api/products/{id}/images` | owner/admin | multipart, part name `images`; JPEG/PNG/WebP, ≤ 5 MB each, ≤ 5 per listing |
| DELETE | `/api/products/{id}/images/{imageId}` | owner/admin | Remove a photo |
| GET | `/api/images/{storedName}` | public | Serves an uploaded photo |
| POST | `/api/products/{id}/reports` | user | `{ reason, details }` → 201 (409 if already reported) |
| GET | `/api/meta` | public | Categories, conditions, campuses, report reasons |

### Wishlist

| Method | URL | Access | Description |
|--------|-----|--------|-------------|
| GET | `/api/wishlist` | user | Saved listings |
| GET | `/api/wishlist/ids` | user | Saved listing IDs |
| POST | `/api/wishlist/{productId}` | user | Add (201; 200 if already saved) |
| DELETE | `/api/wishlist/{productId}` | user | Remove → 204 |

### Inquiries (buyer ⇄ seller)

| Method | URL | Access | Description |
|--------|-----|--------|-------------|
| POST | `/api/inquiries` | user | `{ productId, message }` → 201; appends to an existing open thread |
| GET | `/api/inquiries/received` | user | Threads about my listings |
| GET | `/api/inquiries/sent` | user | Threads I started |
| GET | `/api/inquiries/{id}` | participants | Full thread (404 for everyone else, including admins) |
| POST | `/api/inquiries/{id}/messages` | participants | Reply |
| PATCH | `/api/inquiries/{id}/close` | participants | Close the thread |

### Admin (ROLE_ADMIN only)

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/admin/stats` | Counts of users, listings by status, pending reports |
| GET | `/api/admin/reports?status=` | Reported listings (`PENDING`, `ACTION_TAKEN`, `DISMISSED`, or all) |
| PATCH | `/api/admin/reports/{id}` | `{ status, adminNote, hideListing }` |
| GET | `/api/admin/products?q=&status=` | All listings for review |
| PATCH | `/api/admin/products/{id}/status` | Hide / restore / set any status |
| DELETE | `/api/admin/products/{id}` | Delete any listing |
| GET | `/api/admin/users?q=` | Users with listing counts |
| PATCH | `/api/admin/users/{id}/status` | `{ enabled: false \| true }` — suspend / reinstate (not admins, not yourself) |

---

## 5. Database schema

Defined in `src/main/resources/db/migration/V1__create_schema.sql` (InnoDB, utf8mb4).

| Table | Key columns | Constraints & indexes |
|-------|-------------|-----------------------|
| `users` | id, full_name, email, password_hash, campus, phone, role, enabled, created_at, updated_at | PK; **UNIQUE(email)**; CHECK role ∈ {STUDENT, ADMIN} |
| `products` | id, seller_id → users, title, description, category, item_condition, price DECIMAL(10,2), campus, status, timestamps | FK cascade; CHECKs on price > 0, status, condition, category; indexes (status, created_at), (status, price), (category, status), (campus, status), (item_condition, status), seller_id; FULLTEXT(title, description) |
| `product_images` | id, product_id → products, stored_filename, original_filename, content_type, size_bytes, sort_order, created_at | **UNIQUE(stored_filename)**; CHECK content_type; index (product_id, sort_order) |
| `wishlist` | id, user_id → users, product_id → products, created_at | **UNIQUE(user_id, product_id)**; index product_id |
| `inquiries` | id, product_id, buyer_id, seller_id, status, last_message_at, timestamps | FKs; CHECK status; indexes (buyer_id, last_message_at), (seller_id, last_message_at), (product_id, buyer_id, status) |
| `inquiry_messages` | id, inquiry_id → inquiries, sender_id → users, body, created_at | index (inquiry_id, created_at) |
| `listing_reports` | id, product_id, reporter_id, reason, details, status, admin_note, resolved_by, resolved_at, timestamps | **UNIQUE(product_id, reporter_id)**; CHECKs on reason and status; index (status, created_at) |

`inquiry_messages` is an addition to the requested tables so an inquiry can hold a real back-and-forth conversation.
Enums are stored as readable strings. Hibernate runs with `ddl-auto=none`: Flyway alone owns the schema.

Relationships: a user has many products; a product has many images, wishlist entries, inquiries and reports;
an inquiry belongs to one product, one buyer and one seller and has many messages.

---

## 6. Security design

- **Passwords:** BCrypt (cost 12). Hashes never leave the server; API responses are DTOs without secrets.
- **Sessions:** server-side HTTP session; cookie `UNITRADE_SESSION` is `HttpOnly`, `SameSite=Lax`,
  `Secure` when `COOKIE_SECURE=true`. The session ID is rotated on login (session-fixation protection).
- **CSRF:** enabled for every state-changing request (synchronizer token stored in the session,
  sent by the frontend in `X-CSRF-TOKEN`). The token is rotated at login; the client refreshes it automatically.
- **Authorization:** URL rules in `SecurityConfig` (public reads, authenticated writes, `/api/admin/**` → ADMIN),
  plus service-layer checks on every request: listing ownership, inquiry participation, and the admin role
  re-read from the database. Suspending a user takes effect immediately, even for an existing session.
- **Validation:** Bean Validation on all request DTOs, mirrored in the frontend for fast feedback; campuses
  are validated against a fixed list; text is normalised (control characters stripped).
- **Privacy:** other students see only "First L." and campus; email and phone are never exposed publicly.
  Conversations are visible only to their two participants.
- **Uploads:** type detected from magic bytes (JPEG/PNG/WebP only), JPEG/PNG headers parsed to reject fake
  or oversized images (max 6000 px per side), 5 MB per file, 5 per listing, random server-generated file names,
  stored outside the web root, served with `nosniff` and a sandboxing CSP. The client's file name is never used in paths.
- **XSS:** strict Content-Security-Policy (`script-src 'self'`, no inline scripts or styles). The frontend inserts
  user content only via `textContent`, never `innerHTML`.
- **Other headers:** `X-Frame-Options: DENY`, `frame-ancestors 'none'`, `Referrer-Policy: strict-origin-when-cross-origin`.
- **Brute force:** 5 failed logins for one email lock it for 15 minutes (in-memory).
- **Errors:** no stack traces or internal messages in responses; unexpected errors are logged server-side.
- **Configuration:** all credentials come from environment variables / `.env`; none are hard-coded.
  Demo data is opt-in, and admin accounts exist only if you configure them.

---

## 7. Tests

`src/test/java/com/unitrade` contains Spring Boot integration tests that run the full application
(security filters, controllers, services, JPA) against MySQL through MockMvc:

| Test class | Covers |
|------------|--------|
| `AuthIntegrationTest` | Registration (hashing, no secrets in responses, role escalation ignored, duplicates, validation), CSRF enforcement, login → session → `/me`, logout, wrong password, suspended accounts, 401 on private endpoints |
| `ProductIntegrationTest` | Create/read, anonymous create rejected, validation, 404, update & delete permissions (owner / other / admin), mark sold, hidden-listing visibility, search/filter/sort, LIKE-wildcard escaping, image upload validation and serving, path traversal |
| `WishlistIntegrationTest` | Add (idempotent), list, IDs, per-user isolation, remove, own/hidden/missing listing rules, login required |
| `InquiryIntegrationTest` | Buyer/seller threads, non-participant and admin access denied, replies, thread reuse, close, own-listing rule, validation |
| `AdminIntegrationTest` | 401/403/200 on admin APIs, report → resolve → hide flow, duplicate reports, seller can't unhide, suspend/reinstate, admin protection, DB role re-check |

Run with `mvn test` (MySQL must be running; see 2.10).

---

## 8. Manual testing checklist

- [ ] App starts; Flyway applies V1; <http://localhost:8080> loads with demo listings (if seeding on)
- [ ] Register with invalid data → inline errors; valid data → redirected to login
- [ ] Register the same email again → "account already exists"
- [ ] Wrong password → "Invalid email or password"; 5 wrong attempts → temporary lock
- [ ] Log in → dashboard shows your name; navigation shows Wishlist / Inbox / Dashboard / Log out
- [ ] Sell an item with 1–5 photos → listing page shows photos; row exists in `products` and `product_images`
- [ ] Try uploading a `.txt` renamed to `.png` → rejected
- [ ] Marketplace: search text, each filter, price range, all four sorts, pagination
- [ ] Edit the listing → changes persist after refresh; remove a photo
- [ ] Mark as sold → disappears from marketplace, shows "Sold" on dashboard; relist
- [ ] Second user: wishlist add/remove (heart and product page), send an inquiry
- [ ] First user: Inbox → Received shows it; reply; second user sees reply under Sent; close thread
- [ ] Second user cannot edit/delete first user's listing (UI hides buttons, API returns 403)
- [ ] Report a listing as second user; reporting twice → "already reported"
- [ ] Admin: resolve the report with "hide listing" → listing disappears publicly, owner sees warning
- [ ] Admin: suspend second user → their next action returns "account suspended"; reinstate
- [ ] Non-admin opening `admin.html` → "Administrators only"
- [ ] Log out → private pages redirect to login
- [ ] Resize to phone width: navigation collapses, filters toggle, layouts stack

---

## 9. Troubleshooting

| Symptom | Fix |
|---------|-----|
| `'mvn' is not recognized` / `'java' is not recognized` | Add Maven's / the JDK's `bin` folder to Path and open a new terminal. Check `JAVA_HOME` points to JDK 21. |
| `release version 21 not supported` | Maven is running on an older JDK. `mvn -version` shows which; set `JAVA_HOME` to JDK 21. |
| `Communications link failure` / `Connection refused` | MySQL is not running: open *Services* (`services.msc`) → start **MySQL80**, or check the port in `DB_URL`. |
| `Access denied for user 'unitrade'@'localhost'` | Wrong `DB_PASSWORD` in `.env`, or step 2.2 skipped. Re-run `CREATE USER`/`GRANT`, or `ALTER USER 'unitrade'@'localhost' IDENTIFIED BY '...';` |
| `Unknown database 'unitrade'` | Run `CREATE DATABASE unitrade ...` (step 2.2). |
| `Public Key Retrieval is not allowed` | Keep `allowPublicKeyRetrieval=true` in `DB_URL` (local development only). |
| `Port 8080 was already in use` | Set `PORT=8081` in `.env`, or find the process: `netstat -ano \| findstr :8080` then `taskkill /PID <pid> /F`. |
| Flyway `Validate failed: Migrations have failed validation` | You edited an applied migration. For a disposable dev DB: `DROP DATABASE unitrade; CREATE DATABASE unitrade ...;` and restart. In real environments add a new `V2__...sql` instead. |
| Flyway `Unsupported Database: MySQL 5.7` | Use MySQL 8.0 or newer. |
| `.env` values ignored | Start the app **from the project folder** (the file is looked up in the working directory). Real environment variables override `.env`. |
| Tests fail with `Access denied ... unitrade_test` | Run `GRANT ALL PRIVILEGES ON unitrade_test.* TO 'unitrade'@'localhost';` |
| "Your security token expired" | The page retries automatically once; if it persists, reload the page (the session may have expired). |
| Photos don't appear | Check the `uploads` folder exists and is writable; the log prints its absolute path at startup. |
| Page changes not visible | Hard-refresh (Ctrl+F5). If running from the jar, rebuild it. |

---

## 10. Production deployment notes

This project ships with a secure **development** setup. Before exposing it to real users:

1. **HTTPS only**, behind a reverse proxy (Nginx, Caddy, a cloud load balancer). Set `COOKIE_SECURE=true` and
   `server.forward-headers-strategy=framework` so Spring sees the original scheme.
2. **Disable demo seeding** (`SEED_DEMO_DATA=false`). Create the first admin once (via `SEED_ADMIN_*` for a single
   start, or an `UPDATE users SET role='ADMIN' WHERE email=...`), then remove the variables.
3. **Secrets** come from the platform's secret manager / environment variables; never deploy a `.env` file.
4. **Database:** a least-privilege MySQL user (SELECT/INSERT/UPDATE/DELETE on `unitrade`; run Flyway with a
   separate migration user if your policy requires), TLS to MySQL (`useSSL=true`, remove `allowPublicKeyRetrieval`),
   automated backups.
5. **Uploads:** put `UPLOAD_DIR` on persistent storage with backups (or move to object storage such as S3),
   consider re-encoding images to strip metadata (EXIF GPS), and add malware scanning if required.
6. **Multiple instances:** sessions and the login throttle are in memory. Use Spring Session (Redis/JDBC) and a
   shared rate limiter, or run a single instance.
7. **Email verification / college-domain restriction**, password reset and account deletion are not implemented
   and are recommended before launch.
8. Monitoring (Spring Boot Actuator behind authentication), log aggregation, and dependency updates
   (`mvn versions:display-dependency-updates`).

---

## 11. Known limitations

- **Build verification:** the code was written for Spring Boot 3.5.7 / Java 21 and the SQL migration was executed
  and checked on MySQL 8.0, but the authoring environment could not download Maven dependencies, so
  `mvn package` / `mvn test` have **not** been run there. The GitHub Actions workflow runs the full build and
  test suite on the first push; check it before deploying and report any error output.
- Search uses parameterised `LIKE` matching on title and description (every word must match). It is fine for a
  campus-sized catalogue; for large data sets switch to the existing FULLTEXT index or a search engine.
- No email verification, password reset or "forgot password" flow.
- Inquiries are not real-time (the inbox refreshes when you open a thread or send a message).
- Login throttling and sessions are per application instance (see production notes).
- Seeded demo listings use category illustrations rather than photos.
- Timestamps are stored in the server's local time zone.
