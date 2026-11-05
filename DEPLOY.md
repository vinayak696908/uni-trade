# Putting UniTrade live on the internet

This guide deploys UniTrade to **Railway** (railway.com). It runs the Spring Boot app and a
MySQL database together and gives you a public `https://….up.railway.app` address. The steps
take about 15 minutes. Railway is a paid service with a small trial credit; check its current
pricing before you start.

What gets deployed:

```
Browser ──HTTPS──▶ Railway edge ──▶ UniTrade container (Dockerfile, Java 21, port $PORT)
                                         │            │
                                         ▼            ▼
                                 Railway MySQL    Volume /data/uploads (product photos)
```

The repository already contains everything the platform needs:

| File | Purpose |
|------|---------|
| `Dockerfile` | Builds the jar with Maven, runs it on Java 21 as a non-root user, `SPRING_PROFILES_ACTIVE=prod` |
| `railway.json` | Tells Railway to use the Dockerfile and to health-check `/actuator/health` |
| `src/main/resources/application-prod.properties` | Secure cookies, HTTPS proxy headers, uploads on `/data/uploads` |
| `.github/workflows/ci.yml` | Builds and runs all tests against MySQL on every push |
| `mvnw`, `mvnw.cmd`, `.mvn/` | Maven Wrapper: build without installing Maven |

---

## Step 1 — Put the code on GitHub

1. Create a **new empty repository** on github.com (for example `unitrade`). Private is fine.
2. In PowerShell, inside the `unitrade-live` folder:

   ```powershell
   git init
   git add .
   git commit -m "UniTrade marketplace"
   git branch -M main
   git remote add origin https://github.com/<your-username>/unitrade.git
   git push -u origin main
   ```

3. Open the repository's **Actions** tab. The *Build and test* workflow compiles the app and runs
   every integration test against a real MySQL database. **Wait for a green tick before deploying.**
   If it fails, open the failed step and copy the error.

`.env` is in `.gitignore`, so your local passwords are never pushed.

## Step 2 — Create the Railway project and database

1. Sign in at railway.com with your GitHub account.
2. **New Project → Deploy from GitHub repo →** choose `unitrade`. Railway finds `railway.json` and
   starts building the Dockerfile. The first deploy will fail its health check because there is no
   database yet; that is expected.
3. In the same project click **+ Create → Database → MySQL**. Wait until it shows *Active*.

## Step 3 — Connect the app to MySQL

Open the **unitrade** service → **Variables** → **Raw Editor** and paste:

```
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
SEED_DEMO_DATA=true
SEED_ADMIN_EMAIL=you@example.com
SEED_ADMIN_PASSWORD=ChooseALongAdminPassword123
```

- `${{MySQL.…}}` are Railway *reference variables*: Railway fills in the real values, so no password
  is ever written into your code. If your database service has a different name than `MySQL`, use that name.
- The connection uses Railway's private network, which is why `useSSL=false` is acceptable here.
- `SEED_DEMO_DATA=true` adds the 13 sample listings on the first start (only when the products table is
  empty). Remove it later if you want an empty marketplace.
- `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` create your moderator account (password must be 12+ characters).
  After the first successful start, **delete both variables**: the account stays in the database.

Click **Deploy** (or *Apply changes*). Flyway creates all tables automatically on startup.

## Step 4 — Keep uploaded photos across redeploys

Without a volume, product photos are deleted every time the app redeploys.

1. Right-click the unitrade service (or **⋯ → Attach volume**) → **Add Volume**, mount path **`/data`**.
2. Railway mounts volumes as the root user, while the container runs as a normal user. Add one more
   variable so the app can write to it:

   ```
   RAILWAY_RUN_UID=0
   ```

3. Redeploy. The deploy log prints `Image uploads directory: /data/uploads`.

## Step 5 — Get your public address

unitrade service → **Settings → Networking → Generate Domain**. Railway gives you
`https://unitrade-production-xxxx.up.railway.app`. That is your live site; share it with classmates.

To use your own domain instead, choose **Custom Domain** in the same place and add the CNAME record it
shows at your domain registrar.

## Step 6 — Smoke test the live site

| Check | Expected |
|-------|----------|
| Open `https://<your-domain>/actuator/health` | `{"status":"UP"}` |
| Open `https://<your-domain>/` | Home page with the sample listings |
| Register a student account, log in | Dashboard shows your name |
| Sell an item with a photo | Listing page shows the photo |
| Redeploy (unitrade → Deployments → Redeploy), reload the listing | Listing **and** photo are still there |
| Log in as the admin from step 3 | **Admin** link appears in the header |
| Second browser, second account: try to delete the first user's listing | Refused (403) |
| DevTools → Application → Cookies | `UNITRADE_SESSION` is `HttpOnly`, `Secure`, `SameSite=Lax` |

To look at the data: MySQL service → **Data** tab, or connect a MySQL client using the
*public* connection details from the MySQL service's **Connect** tab.

---

## Updating the live site

Push to GitHub; Railway rebuilds and redeploys automatically:

```powershell
git add .
git commit -m "Describe the change"
git push
```

Database changes go in a **new** migration file, e.g. `src/main/resources/db/migration/V2__add_column.sql`.
Never edit `V1__create_schema.sql` after it has run on the live database.

## Environment variables reference

| Variable | Required | Meaning |
|----------|----------|---------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | yes* | MySQL connection. *If omitted, the app falls back to Railway's `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD` when those are present on the service. |
| `PORT` | set by the platform | HTTP port (default 8080) |
| `SPRING_PROFILES_ACTIVE` | preset to `prod` in the Dockerfile | Production settings |
| `UPLOAD_DIR` | preset to `/data/uploads` | Where photos are stored; mount a volume at `/data` |
| `COOKIE_SECURE` | optional | `true` by default in prod; set `false` only if you serve the app over plain HTTP |
| `SEED_DEMO_DATA` | optional | `true` adds sample listings to an empty database |
| `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD` | optional, temporary | Create the first admin; remove after first start |
| `RAILWAY_RUN_UID` | when a volume is attached | `0` lets the app write to the Railway volume |
| `DB_POOL_SIZE` | optional | Max database connections (default 5, suits small plans) |

## Other hosts

The Dockerfile runs on any platform that runs containers (Render, Fly.io, a VPS with Docker). You need:
a MySQL 8 database, the variables above, a persistent disk mounted at `/data`, and the health check
pointed at `/actuator/health`. If the database is reached over the public internet, use
`useSSL=true&requireSSL=true` in `DB_URL` instead of `useSSL=false`.

Run the production image locally (Docker Desktop on Windows):

```powershell
docker build -t unitrade .
docker run -p 8080:8080 -e DB_URL="jdbc:mysql://host.docker.internal:3306/unitrade?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" -e DB_USERNAME=unitrade -e DB_PASSWORD=yourpassword -e COOKIE_SECURE=false unitrade
```

`COOKIE_SECURE=false` is needed there only because `http://localhost` is not HTTPS.

## Troubleshooting

| Symptom in the deploy log | Fix |
|---------------------------|-----|
| `Communications link failure` | `DB_URL` is wrong or the MySQL service name in `${{MySQL.…}}` doesn't match. Check the Variables tab shows resolved values. |
| `Access denied for user` | `DB_USERNAME` / `DB_PASSWORD` references point to the wrong service. |
| `AccessDeniedException: /data/uploads` | Add `RAILWAY_RUN_UID=0` (step 4). |
| Health check failed, no error | First start can take ~60 s while Flyway runs; `railway.json` allows 180 s. Check the log for the real exception. |
| Flyway warns that the MySQL version is newer than tested | A warning only; the app continues. |
| Login works locally but not on the live site | The site must be opened over `https://` (secure cookie). Use the Railway domain, not a raw IP. |
| Build fails in `mvn package` | Fix the error shown by the GitHub Actions run first; it runs the same build plus tests. |
