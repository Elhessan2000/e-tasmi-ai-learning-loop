# Learning Loop architecture

This is the current challenge architecture. The pre-challenge system is documented in [../baseline/architecture/README.md](../baseline/architecture/README.md).

## Product flow

```mermaid
flowchart TD
    student[Student] --> submit[Recitation submission]
    submit --> store[Cloudinary or local storage]
    store --> pipeline[Automatic analysis]
    pipeline --> stt[ElevenLabs Scribe v2]
    pipeline --> ref[Quranpedia Hafs text]
    stt --> compare[Deterministic comparison]
    ref --> compare
    compare --> explain[OpenAI gpt-6.1-sol explanation]
    explain --> instructor[Instructor verification]
    instructor --> publish[Publication]
    publish --> focus[Verified Learning Focus]
    focus --> again[Practice Again]
    again --> submit
```

## Trust boundary

```text
AI findings          Instructor decision         Learner
─────────────        ───────────────────         ───────
PENDING       ──►    Accept / Edit / Reject
                     or add a finding      ──►   published result only
```

There is no path from an unverified finding to the learner.

## External services

| Service | Role in the Learning Loop | Credential |
|---|---|---|
| ElevenLabs | Speech-to-text (`scribe_v2`, `language_code=ar`) | `ELEVENLABS_API_KEY` |
| Quranpedia | Trusted comparison text, mushaf 1 (Hafs) | None. Public HTTPS API |
| OpenAI | Explains stored findings (`gpt-6.1-sol`, reasoning effort high) | `OPENAI_API_KEY` |
| Cloudinary | Recitation media | `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` |
| Quran Foundation | Qur'an Library only | `QF_CLIENT_ID`, `QF_CLIENT_SECRET` |
| Zoom | Optional live sessions | Optional |
| Email | Optional password reset | Optional |

## Safe failure

| Condition | Stored status | Publication |
|---|---|---|
| No speech | `CANNOT_EVALUATE` | Blocked until analysis succeeds |
| Non-Qur'an audio | `REJECTED` | Instructor may publish a manual score. AI is not retried |
| Quranpedia unreachable | `REFERENCE_UNAVAILABLE` | No findings. No generated Qur'an text |

## Application stack

Java 17 servlets and JSP on Tomcat 9, JDBC, MySQL 8.4, packaged with Docker Compose. The challenge Compose file also runs Caddy on host port 8444 and phpMyAdmin on host port 8083. The application is published on host port 8084. API keys are read from environment variables and are never sent to the browser.

`FAILED` analysis cannot be published. A missing reference is `REFERENCE_UNAVAILABLE`, which is a different card from a failed analysis.

## Student Progress

The existing Learning Snapshot stays on `/student/progress`. It is the pre-existing `progress` calculation from `ProgressService`. The challenge addition is the published history on that same page:

- Latest Verified Focus, counted only from `ACCEPTED`, `EDITED`, and `INSTRUCTOR_ADDED`
- A comparison with the published parent, or else the previous published attempt on the same enrollment
- Recitation History of published results only, at most 60 loaded rows, 8 shown until Show more
- View Result and Practice Again reuse the existing result and studio routes
- The session user id selects the student. The page does not take a student id from the query string
- Arabic layout uses the document `dir`. Reduced motion disables the progress animations. Show more moves focus to the first revealed link
- There is no academic term. The chip says "Last updated"

No history table was added.

## What is not this loop

| Path | Role | Not the learning-loop evaluator |
|---|---|---|
| ElevenLabs Scribe v2 | Learning-loop speech-to-text | Batch `POST /v1/speech-to-text`. Scribe Realtime v2 is not implemented |
| OpenAI `gpt-6.1-sol` | Explains findings that Java already stored. `reasoning_effort=high` | Does not produce the mushaf text or the word diff |
| OpenAI `gpt-4o-mini` | Existing Qur'an assistant default | Separate from recitation evaluation |
| OpenAI transcription | Fallback only when `STT_PROVIDER` is empty or `openai` | Not the configured challenge path. A failed ElevenLabs call does not switch providers |
| Quran Foundation | Qur'an Library | Not the current comparison source |
| Figma | An account screenshot is in the tools dossier | That screenshot does not show a challenge-period design file |
| XeLaTeX | Used to produce the tools booklet in `docs/evidence/` | The presentation deck is outside the tracked tree. Readex Pro and Urbanist: Status: Not verified from the tracked files |

Current pricing should be checked against each provider's official pricing page at submission time. Quranpedia's public API page, captured as EV-04, states free read-only access with attribution and rate limits. That notice is not a named software licence.
