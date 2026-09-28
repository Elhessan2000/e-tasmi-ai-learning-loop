# e-Tasmi

This repository is the isolated AI Challenge copy. It does not share Docker containers, host ports, the image tag, or data volumes with the original e-Tasmi installation on the same computer.

Run Compose from the `e-Tasmi` directory. The Compose project name is `etasmi-ai-challenge`.

## Current stack
- Java Servlet/JSP web application
- Custom Docker build that packages the current source into a WAR
- Tomcat 9
- MySQL 8
- phpMyAdmin

## New local development workflow

### Prerequisites
- Docker Desktop
- VS Code

### Start the project
Run from the repository root:

```powershell
docker compose up -d --build
```

### Email verification (intentionally disabled)

Email verification is intentionally disabled in the AI Challenge development environment. Student registration does not require email verification. Instructor registration does not require email verification but remains subject to administrator approval.

The switch is `EMAIL_VERIFICATION_ENABLED` (default `false` in Docker Compose and `.env.example`). SMTP, Brevo, and Resend are not required for registration. The original verify-email flow remains in the codebase and can be restored later by setting `EMAIL_VERIFICATION_ENABLED=true`.

This applies only to this isolated challenge copy. It does not change the original production e-Tasmi system.

### Enable real Zoom live sessions
The live-session flow supports:
- Zoom meeting creation from the backend (Server-to-Server OAuth; instructor flow unchanged)
- Embedded **student** join via the Zoom Meeting SDK Web when SDK credentials and `ZOOM_EMBED_ENABLED` are set
- Fallback to opening the stored Zoom join link in the browser if embed is off or SDK is not configured

To enable embedded student join:

1. Copy `.env.example` to `.env`
2. Fill in the Zoom values (REST + Meeting SDK)
3. Rebuild/recreate the app container (`docker compose up -d --build app`)

Required values:
- `ZOOM_ACCOUNT_ID`
- `ZOOM_CLIENT_ID`
- `ZOOM_CLIENT_SECRET`
- `ZOOM_MEETING_SDK_KEY`
- `ZOOM_MEETING_SDK_SECRET`
- `ZOOM_DEFAULT_HOST_EMAIL`

Optional values:
- `ZOOM_EMBED_ENABLED` (defaults to `true` in Docker Compose; set `false` to always use external Zoom only)
- `ZOOM_WEB_SDK_VERSION`
- `ZOOM_TIMEZONE`

Example:

```powershell
Copy-Item .env.example .env
docker compose up -d --build app
```

### Open the services
These are the isolated AI Challenge host addresses. Inside Docker, Tomcat remains on 8080, MySQL on 3306, phpMyAdmin on 80, and Caddy on 443.

- App (HTTP): http://localhost:8084/
- App (HTTPS, for **embedded Zoom** on LAN / phones): https://localhost:8444/ — first visit may show a certificate warning (Caddy `tls internal`); accept it or trust Caddy’s local CA. Use this URL (with your machine’s LAN IP instead of `localhost` when joining from another device). Plain `http://192.168.x.x:8084` cannot run the in-browser Zoom SDK; use **:8444** instead.
- phpMyAdmin: http://localhost:8083/
- MySQL host port: `localhost:3317`

### Default database credentials
- Database: `etasmi`
- User: `etasmi`
- Password: `etasmi123`
- Root password: `root`

### phpMyAdmin login
- Server: `db`
- Username: `etasmi`
- Password: `etasmi123`

### Default admin account
On first successful app startup, `DBSeeder` creates:
- Email: `admin@etasmi.com`
- Password: `Admin123!`

### Demo sample accounts
The local sample dataset includes:
- Student: `student.demo@etasmi.local`
- Instructor: `instructor.demo@etasmi.local`
- Password for both: `Admin123!`

These demo emails are local placeholders, so real verification messages will only work for accounts registered with a real email address.

## Important project rules
- Use `e-Tasmi/src/` as source code.
- Do not treat `e-Tasmi/build/` as source code.
- Use `e-Tasmi/setup/etasmi_schema.sql` as the database source of truth.
- Use `e-Tasmi/setup/sample_data.sql` for repeatable local demo data.
- Treat `db/etasmi.sql` as an old dump, not the setup file.

## Files added for the reset
- `docker-compose.yml`
- `Dockerfile`
- `.dockerignore`
- `docs/reset-audit.md`
- `e-Tasmi/setup/sample_data.sql`

## Main issues already identified
A quick scan found several structural problems:
- generated NetBeans output is mixed into the repo
- old SQL files do not match the current Java code
- parts of the app still assume XAMPP-era paths and behavior
- some module logic used incorrect IDs when writing related records

Details are documented in `docs/reset-audit.md`.

## Useful commands
Start or rebuild:

```powershell
docker compose up -d --build
```

Stop:

```powershell
docker compose down
```

Re-import sample data into the running DB:

```powershell
Get-Content e-Tasmi\setup\sample_data.sql | docker exec -i etasmi-challenge-db mysql -uetasmi -petasmi123
```
