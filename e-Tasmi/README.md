# e-Tasmi challenge application

Isolated AI Challenge copy. It does not share Docker containers, host ports, or volumes with a production e-Tasmi installation.

Product overview: [../README.md](../README.md)  
Installation: [../docs/development/installation.md](../docs/development/installation.md)

## Run

```powershell
copy .env.example .env
docker compose up -d --build
```

| Service | Address |
|---|---|
| App | http://localhost:8084/ |
| phpMyAdmin | http://localhost:8083/ |
| MySQL host port | `localhost:3317` |

Compose project name: `etasmi-ai-challenge`.

## Local accounts

These are synthetic challenge accounts, not real users.

| Role | Email | Password |
|---|---|---|
| Admin | `admin@etasmi.com` | Created by `DBSeeder` on first startup. Not printed here. |
| Demo student | `student.demo@etasmi.local` | Same hash as the bootstrap admin |
| Demo instructor | `instructor.demo@etasmi.local` | Same hash as the bootstrap admin |

Set `ETASMI_BOOTSTRAP_ADMIN_PASSWORD` in the ignored `.env` file before startup to choose the local admin password. `sample_data.sql` copies that password hash onto the demo student and instructor.

Email verification is disabled (`EMAIL_VERIFICATION_ENABLED=false`). Instructors still require administrator approval.

## Configuration

Copy `.env.example` to `.env`. Do not commit `.env`.

Learning-loop variables: `OPENAI_API_KEY`, `STT_PROVIDER=elevenlabs`, `ELEVENLABS_API_KEY`, `ELEVENLABS_STT_MODEL=scribe_v2`. Quranpedia needs no API key. Leave `MYSQLHOST` unset so the app uses the local MySQL container.

## Source

- Java/JSP: `e-Tasmi/src/` and `e-Tasmi/web/`
- Schema: `e-Tasmi/setup/etasmi_schema.sql`
- Sample data: `e-Tasmi/setup/sample_data.sql`
