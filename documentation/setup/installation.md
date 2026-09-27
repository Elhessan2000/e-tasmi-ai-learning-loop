# Installation

These steps describe the isolated local challenge environment. They are **local challenge environment** values. They are not the original e-Tasmi installation on the same computer.

## Requirements

- Docker
- Docker Compose

TODO — record any additional confirmed host requirements, such as the operating system version.

## Repository setup

The local project directory is:

`C:\Users\ASUS\Desktop\e-Tasmi-AI-Challenge`

Compose is run from:

`C:\Users\ASUS\Desktop\e-Tasmi-AI-Challenge\e-Tasmi`

TODO — add the public repository URL and clone command when the public remote is confirmed.

## Environment variables

Copy `.env.example` to `.env` inside the `e-Tasmi` directory if a local environment file is not already present.

Leave `MYSQLHOST` unset so the application uses the challenge MySQL container.

Do not put API keys, passwords, or other secrets into Git. Keep them only in the ignored `.env` file.

## Docker setup

From `e-Tasmi`:

```powershell
docker compose up -d --build
```

Compose project name: `etasmi-ai-challenge`

Application image: `etasmi-challenge-tomcat:latest`

TODO — list the challenge container names here if this guide should repeat them beside the Compose file.

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
| Application-to-database connection inside Docker | `jdbc:mysql://db:3306/etasmi` |

Inside Docker, Tomcat listens on 8080 and MySQL listens on 3306. The host ports in the table are the published ports for this challenge copy.

## Troubleshooting

TODO — add confirmed startup failures and fixes as they are observed. Do not include command output that contains secrets.
