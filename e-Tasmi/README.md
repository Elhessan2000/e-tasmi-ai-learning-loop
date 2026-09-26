# e-Tasmi

This repository is being reset from an old NetBeans + XAMPP setup to a VS Code + Docker workflow.

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

### Enable real email verification
The verification flow is now wired into registration, but real email delivery needs SMTP credentials.

1. Copy `.env.example` to `.env`
2. Fill in real SMTP values
3. Rebuild the app container

Example:

```powershell
Copy-Item .env.example .env
docker compose up -d --build app
```

For Gmail:
- `SMTP_USERNAME` should be your real Gmail address
- `SMTP_PASSWORD` should be a Google App Password, not your normal Gmail password
- `SMTP_FROM` should usually be the same address as `SMTP_USERNAME`

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
- App (HTTP): http://localhost:8080/
- App (HTTPS, for **embedded Zoom** on LAN / phones): https://localhost:8443/ — first visit may show a certificate warning (Caddy `tls internal`); accept it or trust Caddy’s local CA. Use this URL (with your machine’s LAN IP instead of `localhost` when joining from another device). Plain `http://192.168.x.x:8080` cannot run the in-browser Zoom SDK; use **:8443** instead.
- phpMyAdmin: http://localhost:8081/
- MySQL: `localhost:3306`

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
Get-Content e-Tasmi\setup\sample_data.sql | docker exec -i etasmi-db mysql -uetasmi -petasmi123
```
