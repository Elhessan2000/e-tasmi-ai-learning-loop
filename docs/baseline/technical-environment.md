# Technical Environment at Pre-Challenge Baseline

## 1. Purpose

This document records the technical environment associated with the pre-challenge e-Tasmi system, and the later isolated local environment used to inspect that baseline safely.

The source snapshot and the inspection setup are not the same thing. Commit `a358719` is the pre-challenge source. Host ports and container names used to run this copy beside the original installation were introduced after that commit. They are described in section 7 as inspection setup.

This document does not describe challenge-period feature work. Credentials are not recorded here.

## 2. Application Architecture

The existing system audit describes a server-rendered web application:

- JSP and Java Servlet web application
- Server-rendered pages, with session-based access
- Apache Tomcat as the web and application server
- MySQL as the relational database
- Docker and Docker Compose for the local multi-container environment

The audit records no Maven build file and no application semantic version in configuration.

## 3. Technology Stack

Versions below are the ones confirmed by the Dockerfile, Compose file, and existing system audit. Image tags are stated as tags, not as a claim that a more precise build number was measured.

| Component | Version / Technology | Role |
|---|---|---|
| Java | 17 | Application runtime and build |
| Apache Tomcat | 9 (`tomcat:9.0-jdk17-temurin`) | Web and application server |
| MySQL | 8.4 (`mysql:8.4`) | Relational database |
| JSP | Existing | Server-rendered interface |
| Java Servlets | Existing | Request and application handling |
| Docker | Existing | Containerization |
| Docker Compose | Existing | Local multi-container environment |
| Caddy | Image tag `caddy:2-alpine` | Optional reverse proxy in Compose |
| phpMyAdmin | Image tag `phpmyadmin:5-apache` | Local database inspection UI in Compose |

## 4. Application and Database

The database name is `etasmi`.

Inside Docker, the application connects to MySQL on the Compose service `db` at port 3306:

`jdbc:mysql://db:3306/etasmi`

The Compose setting `ETASMI_JDBC_URL` uses that address and adds local connection parameters for SSL, public key retrieval, and server time zone. No password is recorded in this document.

Compose mounts this schema file for first-time database initialisation:

`e-Tasmi/e-Tasmi/setup/etasmi_schema.sql`

It is mounted as `docker-entrypoint-initdb.d/01_schema.sql`. The application also contains `SchemaBootstrap`, which can apply `WEB-INF/db/railway_schema.sql` when the `user` table is missing. This document does not reproduce either script.

## 5. External Services

The audit confirms these external services. None of them is required for every feature. Core session, enrolment, and human evaluation can run when a given integration is not configured. Features that depend on a missing key fail or stay unavailable.

| Service | Role in the pre-challenge system |
|---|---|
| OpenAI | Instructor recitation analysis uses it for transcription and for the evaluation request. Separate student assistant, voice, and translation-fallback features also call OpenAI when configured. |
| Quran Foundation | Supplies Qur'an Library content, including chapters, verses, and related resources, when the integration is configured. |
| Cloudinary | One storage path for submitted recitation audio, and for some other uploads. The audit also records local `/uploads` storage when Cloudinary is not used. |
| Zoom | Optional live-session meetings and embedded join. The audit records that a session can instead use a manual meeting link or a non-live mode. |

API keys, account identifiers, and other secrets are not listed here.

For the recitation AI path, OpenAI is the transcription and evaluation provider. Model names are in section 6. For the Qur'an Library, the existing integration is Quran Foundation. For audio storage, the audit already documents Cloudinary or local uploads.

## 6. AI Model Configuration at Baseline

The application source and the baseline Compose file do not name the same models. Environment configuration can override the source defaults. No runtime test result is recorded for either pair.

Java source defaults, used only when the related environment variables are absent:

- transcription: `gpt-4o-transcribe`
- evaluation: `gpt-4o`

Baseline Compose overrides, applied when the host does not set the variables:

- `OPENAI_RECITATION_MODEL=whisper-1`
- `OPENAI_CLASSIFIER_MODEL=gpt-4o-mini`

Compose does not set `OPENAI_EVALUATOR_MODEL`. The service checks that variable first, then `OPENAI_CLASSIFIER_MODEL`, then the Java default. In the baseline Compose configuration, the effective transcription model is therefore `whisper-1`, and the effective evaluation/classifier path is `gpt-4o-mini`.

The same distinction is recorded in [existing-ai-capabilities.md](existing-ai-capabilities.md).

## 7. Local Challenge Inspection Environment

**Later Local Inspection Environment — Not Part of Baseline Source Commit**

These addresses belong to the isolated copy used to inspect the baseline on this machine. They are not the original production ports, and they are not the host ports published in commit `a358719`.

| Item | Inspection value |
|---|---|
| Application | http://localhost:8084 |
| phpMyAdmin | http://localhost:8083 |
| MySQL host port | 3317 |
| Internal application database | `jdbc:mysql://db:3306/etasmi` |

Inside that Compose network, Tomcat still listens on 8080 and MySQL still listens on 3306. Host port 3317 only publishes the database. Host port 8084 only publishes the application.

These host ports were introduced after baseline commit `a358719` so this challenge copy could run beside the original e-Tasmi installation. The source snapshot itself publishes MySQL on host port 3306, the application on 8080, Caddy on 8443, and phpMyAdmin on 8081, with the original container names. Those published ports are source configuration in that commit. They are not described here as a production deployment.

## 8. Baseline Git Source Snapshot

| Item | Value |
|---|---|
| Commit | `a358719` |
| Tag | `baseline-pre-ai-challenge-2026` |

This commit and tag identify the pre-challenge source snapshot. The full commit hash is recorded in [baseline-declaration.md](baseline-declaration.md). This document does not move the tag or rewrite history.

## 9. Environment and Secret Handling

Secrets are supplied through local environment configuration.

- `.env` is excluded from Git.
- `.env.example` contains placeholders.
- API keys and passwords must never be committed.
- Production credentials must not be included in public documentation.

No secret values are recorded in this document.

## 10. Reproducibility Notes

A developer reproducing the local inspection environment needs:

- this repository, with the pre-challenge source identified by commit `a358719` and tag `baseline-pre-ai-challenge-2026`
- Docker and Docker Compose
- a local `.env` created from `.env.example`, kept out of Git, with `MYSQLHOST` left unset so the application uses the Compose database
- Compose started from the `e-Tasmi` directory
- the schema initialisation performed by that Compose setup on a new database volume

The installation steps are maintained separately in [../development/installation.md](../development/installation.md). This document does not replace that guide.

## 11. Baseline Boundary

The technical information in this document records the pre-challenge platform and the isolated environment used to inspect it. Changes made solely to isolate the challenge copy locally are environment setup changes and are not part of the baseline source snapshot.
