# Data Privacy and Public Repository Handling

## 1. Purpose

This document records how data, credentials, uploaded content, and sensitive information are handled for the public challenge repository.

It describes the pre-challenge situation found in the existing system audit and in this repository. It does not state a privacy certification, a legal compliance conclusion, or a provider retention policy.

## 2. Public Repository Principle

The public repository must not contain:

- API keys
- passwords
- access tokens
- private credentials
- real beneficiary records
- personal or sensitive information

This is a repository-preparation rule. It is not a verified legal or compliance claim.

## 3. Environment Secrets

The verified baseline approach is:

- `.env` is a local file and is ignored by Git.
- `.env.example` is the tracked template and contains placeholders.
- Credentials are not included in public documentation.
- Production credentials must not be committed.

The root `.gitignore` ignores `.env`, `.env.*`, and `.env.template-overwrite-backup`, and it keeps `.env.example` with a negation rule. The existing system audit records a local `.env` as present and gitignored, and it records `.env.example` as a tracked template.

A check of this repository's current Git history found one commit, `a358719`. `.env` is not present in that history, and `.env` is not tracked. This check applies to this repository's current Git history only. It does not apply to any other repository or historical copy. The broader privacy checklist in section 10 remains open.

No secret values are written here.

The audit also records local database defaults in the Compose file. Those defaults are not reproduced in this document, and they must not be treated as production credentials.

## 4. Database Data

The database name is `etasmi`.

The schema used for reproducibility is included as `e-Tasmi/e-Tasmi/setup/etasmi_schema.sql`. Compose mounts that file for first-time initialisation. This document does not copy rows from it.

Real production database dumps, and any other dump that contains personal records or password hashes, must not be included in the public repository. The working tree includes the schema script. It does not include a dump file named `etasmi.sql`.

Test and demo data should be synthetic or fully anonymized. This document does not include database records, and it does not assert that every sample script in the repository has already been reviewed.

Local database files under `mysql-data/` are ignored by Git.

## 5. Uploaded Audio

Recitation audio is user-submitted content in the application. The student uploads or records it. The application stores the path and the instructor can play it back.

The audit records two existing storage paths:

- Cloudinary, when that integration is configured
- local `/uploads`, including `/uploads/recitations/...`, when the file is stored on disk

The audit also records that local files under `/uploads/*` are served without the authentication filter, so a local file can be reached if its path is known. That is an existing audit finding. It is not a new data flow.

Actual user recordings are not included in this document. They must not be placed in public documentation unless they are properly authorized and anonymized. Challenge demonstration and testing should use synthetic, test, or appropriately authorized material.

`uploads/` is ignored by Git. The ignore file also names `student-test-audio.mp3`.

## 6. Screenshots and Evidence

Baseline screenshots are intended to document the system interface. They are kept in [../baseline/screenshots/](../baseline/screenshots/).

Before public submission, screenshots must be checked for:

- names
- email addresses
- phone numbers
- personal records
- payment information
- passwords
- tokens
- sensitive content

This document does not claim that every screenshot has already been verified.

## 7. Files Excluded from the Public Repository

These categories must remain excluded:

- `.env` and other secret environment files
- database data volumes
- production database dumps
- uploaded user files
- secret and configuration backups
- generated build artifacts when they should not be published
- private credentials

The root `.gitignore` currently covers, among other paths:

- `.env`, `.env.*`, and `.env.template-overwrite-backup`, while allowing `.env.example`
- `uploads/` and `mysql-data/`
- `build/`, `dist/`, `node_modules/`, `*.war`, and `*.class`
- local session-cookie text files and named temporary capture files
- `nbproject/private/`

A dump is not excluded by a dedicated `*.sql` rule. Keeping production dumps out of the repository therefore depends on review, not only on that ignore file. The `.env` history check in section 3 covers this repository's current history only.

## 8. Challenge Test Data

Challenge testing should use:

- synthetic data
- fully anonymized data
- controlled test accounts
- controlled test audio

This section does not create test records. The planned validation scenarios in the challenge documentation are not recorded as executed.

## 9. Data Flow Relevant to Recitation AI

The pre-challenge instructor analysis path, as recorded by the audit, is:

Student recitation audio → stored and later retrieved by the application → transcription service → AI analysis → instructor review

Storage is Cloudinary or local `/uploads`. Transcription and the evaluation request use OpenAI when that analysis is run. The instructor then reviews the on-screen result and saves the official score and feedback separately. The AI result itself is not stored as a durable database record.

The exact external-service data handling and retention terms must be checked against the providers' current official terms before final submission. This document does not state those retention policies.

## 10. Privacy Review Before Final Submission

None of the following is marked complete.

- [ ] No secrets in Git history
- [ ] No real user records in the public repository
- [ ] No production database dump
- [ ] No real sensitive audio without authorization
- [ ] Screenshots reviewed for personal information
- [ ] Demo data reviewed
- [ ] External service terms reviewed
- [ ] Ownership/permission issues reviewed
- [ ] Public repository reviewed before final submission

## 11. Baseline Boundary

This document records data and privacy considerations associated with the pre-challenge system and public repository preparation. It does not describe new challenge-period data flows as already implemented.
