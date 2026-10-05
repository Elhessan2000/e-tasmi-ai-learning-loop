# Installation

These steps describe the isolated local challenge environment. They are **local challenge environment** values. They are not the original e-Tasmi installation on the same computer.

## Requirements

- Docker
- Docker Compose

Docker Desktop on Windows, macOS, or Linux is sufficient.

## Repository setup

Clone the repository, then run Compose from the `e-Tasmi` directory.

## Environment variables

Copy `.env.example` to `.env` inside the `e-Tasmi` directory if a local environment file is not already present.

Leave `MYSQLHOST` unset so the application uses the challenge MySQL container.

Email verification is intentionally disabled in this AI Challenge development environment (`EMAIL_VERIFICATION_ENABLED=false` by default). Student registration does not require email verification. Instructor registration does not require email verification but remains subject to administrator approval. SMTP, Brevo, and Resend are not required for registration.

Do not put API keys, passwords, or other secrets into Git. Keep them only in the ignored `.env` file.

## Docker setup

From `e-Tasmi`:

```powershell
docker compose up -d --build
```

Compose project name: `etasmi-ai-challenge`

Application image: `etasmi-challenge-tomcat:latest`

Containers: `etasmi-challenge-app`, `etasmi-challenge-db`, `etasmi-challenge-caddy`, `etasmi-challenge-phpmyadmin`.

## Database setup

MySQL 8.4 runs in the challenge database container. The database name inside that container is `etasmi`.

On first start, the container initialises from the project schema script mounted by Compose. Do not import a dump that contains real user records.

## Application startup

Wait until the database container is healthy and the application container is running, then open the application URL below.

## Local URLs

| Service | Local challenge environment |
|---|---|
| Application | http://localhost:8084 |
| Login page | http://localhost:8084/en/auth/login |
| phpMyAdmin | http://localhost:8083 |
| MySQL host port | 3317 |
| Caddy HTTPS | https://localhost:8444 |
| Application-to-database connection inside Docker | `jdbc:mysql://db:3306/etasmi` |

Inside Docker, Tomcat listens on 8080 and MySQL listens on 3306. The host ports in the table are the published ports for this challenge copy.

## Troubleshooting

If the application cannot reach MySQL, confirm `MYSQLHOST` is unset locally so Compose uses the `db` service. Do not paste logs that contain secrets.
