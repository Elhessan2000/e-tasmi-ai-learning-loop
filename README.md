# e-Tasmi AI Learning Loop

<p align="center">
  <strong>Turning instructor-verified findings into the learner’s next practice step.</strong><br>
  Track 03 — Interactive Experiences &amp; Learning Journeys for Islam<br>
  <em>AI Challenge in Service of Islamic Content 2026</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Track-03-0C7C86?style=flat-square" alt="Track 03">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Servlet_API-4.0-007396?style=flat-square" alt="Servlet API 4.0">
  <img src="https://img.shields.io/badge/Tomcat-9-F8DC75?style=flat-square&logo=apachetomcat&logoColor=black" alt="Tomcat 9">
  <img src="https://img.shields.io/badge/MySQL-8.4-4479A1?style=flat-square&logo=mysql&logoColor=white" alt="MySQL 8.4">
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker Compose">
  <img src="https://img.shields.io/badge/STT-ElevenLabs_Scribe_v2-000000?style=flat-square" alt="ElevenLabs Scribe v2">
  <img src="https://img.shields.io/badge/Explainer-GPT--6.1_Sol-412991?style=flat-square&logo=openai&logoColor=white" alt="GPT-6.1 Sol">
</p>

| Interface | Address |
|---|---|
| **Live demonstration** | [https://e-tasmi.com](https://e-tasmi.com) |
| **Local challenge application** | [http://localhost:8084](http://localhost:8084) |
| **Local login** | [http://localhost:8084/en/auth/login](http://localhost:8084/en/auth/login) |
| **API documentation / OpenAPI** | Not applicable. This entry is a server-rendered Servlet/JSP application and does not expose Swagger UI. Architecture and endpoints are documented in [docs/architecture/learning-loop.md](docs/architecture/learning-loop.md) and [e-Tasmi/.env.example](e-Tasmi/.env.example). |

If the public host changes, replace the live demonstration row with the current deployment URL. Do not treat a placeholder such as `https://your-domain.com` as a live service.

---

## 1. Abstract

e-Tasmi already supported session enrolment, recitation upload, and instructor scoring. The challenge contribution is the **verified learning loop after that score**: automatic transcription, comparison against a trusted Qur’an passage, instructor verification of every finding, publication of only accepted work, and a linked Practice Again attempt on the same enrolment.

The Qur’an text used for comparison is retrieved from Quranpedia Hafs (`GET /mushafs/1/{surah}`). Word-level differences are computed in Java by a deterministic Levenshtein path over orthographically normalised tokens. OpenAI `gpt-6.1-sol` (`reasoning_effort=high`) explains those stored findings; it does not supply the mushaf text. Speech-to-text for the learning loop uses ElevenLabs Scribe v2 when `STT_PROVIDER=elevenlabs`.

> [!IMPORTANT]
> AI output is advisory. A finding is stored `PENDING` and cannot reach the learner until an authenticated instructor executes `ACCEPT`, `EDIT`, or `REJECT`, and the evaluation is published. Pending findings block publication.

**Participant:** Elhessan Abdelrahman Adam — Individual entry, Track 03.

---

## 2. Problem and contribution

A numeric score does not tell a learner what to practise next. Three failure modes follow if that gap is filled carelessly:

| Failure mode | Consequence this design refuses |
|---|---|
| Model-invented mushaf text | Comparison is withheld when Quranpedia is unreachable. No generated Qur’an text is stored. |
| Unverified AI findings on the student screen | `LearningLoopPublicationPolicy` refuses publication while any finding is `PENDING`. |
| Unlinked resubmission | Practice Again writes `parent_recitation_id` and the next `attempt_number` on the same enrolment. |

The pre-challenge baseline (Git tag `baseline-pre-ai-challenge-2026`) already provided accounts, sessions, live Zoom meetings, the Qur’an Library, recitation upload, and instructor-triggered on-screen analysis. That work is not claimed as challenge contribution. See [docs/baseline/](docs/baseline/README.md).

---

## 3. Theoretical methodology

The pipeline separates **retrieval**, **alignment**, **bounded advisory inference**, and **human authority**.

### 3.1 Trusted reference

The session stores a structured passage `(surah_number, ayah_start, ayah_end)`. At analysis time the server fetches Hafs mushaf id 1 from Quranpedia:

```text
GET https://api.quranpedia.net/v1/mushafs/1/{surah}
```

No API key is sent. Publication of an `OK` analysis requires `reference_source=QURANPEDIA`. The student Qur’an Library remains on the Quran Foundation Content API and is not the comparison source.

### 3.2 Deterministic orthographic alignment

`RecitationComparisonEngine` is the sole source of word-level findings. It performs a Levenshtein edit path over word tokens. Token equality uses orthographic matching so mushaf spelling is not treated as a recitation error. The same reference and transcript always yield the same findings. There is no network call and no model in this step.

Finding types produced by the engine include `MISSING_WORD`, `INCORRECT_WORD`, `EXTRA_WORD`, and `PASSAGE_MISMATCH`.

### 3.3 Bounded advisory inference

After alignment, OpenAI `gpt-6.1-sol` is asked to explain findings that already exist. The chat request is `POST https://api.openai.com/v1/chat/completions` with `reasoning_effort=high`. The model is instructed that the word-level comparison is already done. It does not replace the Java diff and does not author the trusted passage.

Speech-to-text, when the challenge provider is selected, is:

```text
POST https://api.elevenlabs.io/v1/speech-to-text
model_id = scribe_v2
```

If `STT_PROVIDER` is empty or `openai`, the selector uses OpenAI transcription instead. A failed ElevenLabs call does not silently fall back to OpenAI.

### 3.4 Safe-failure states

| Condition | Stored analysis status | Publication |
|---|---|---|
| No speech | `CANNOT_EVALUATE` | Blocked until analysis succeeds |
| Non-Qur’an audio, or audio too short | `REJECTED` | Instructor may publish a manual score. AI is not retried |
| Quranpedia unreachable | `REFERENCE_UNAVAILABLE` | No findings. No generated Qur’an text |
| Provider or pipeline error | `FAILED` | Blocked until analysis succeeds |

---

## 4. Human-in-the-loop verification protocol

Every AI finding is inserted with instructor status `PENDING`. `FindingVerificationService` is the only path off that state. Callers cannot write `PENDING` as a decision.

| Instructor action | Persisted status | Reaches the learner after publication? |
|---|---|---|
| Accept | `ACCEPTED` | Yes |
| Edit | `EDITED` | Yes (instructor text when supplied) |
| Reject | `REJECTED` | No |
| Add finding | `INSTRUCTOR_ADDED` | Yes |

Publication is an explicit instructor save. The server refuses the save when any finding on the latest analysis is still `PENDING`. After `published_at` is set, the student sees score, instructor feedback, and **Verified Learning Focus** (accepted, edited, and instructor-added items only). Unpublished reviews show a pending state: no score, no focus, no Practice Again.

> [!NOTE]
> This is a verification barrier, not a UI hint. The student projection (`VerifiedLearningFocusService`) reads the evaluation stamped with `analysis_id` and the finding rows for that analysis. There is no client-side promotion of pending items.

Practice Again creates a new recitation on the same enrolment and passage, with `parent_recitation_id` set and `attempt_number` equal to one plus the highest attempt on that enrolment. Automatic analysis runs again on the new attempt. Student Progress lists published recitations and compares the latest published attempt with its parent, or with the previous published attempt on the same enrolment.

---

## 5. System architecture

The application is a three-tier Java web system packaged with Docker Compose (`etasmi-ai-challenge`). There is no Maven `pom.xml`; the image compiles sources with `javac` against Servlet API 4.0.1. The presentation layer is JSP and project CSS, not Tailwind.

| Layer | Responsibility | Implementation |
|---|---|---|
| Client / presentation | Role-specific JSP views, recitation studio, instructor verification, student result and progress | JSP, JavaScript, project CSS on Apache Tomcat 9 |
| Service / orchestration | HTTP servlets, learning-loop services, publication policy, background analysis jobs | Java 17 Servlet API 4.0 |
| Speech-to-text | Arabic recitation transcript | ElevenLabs Scribe v2 (`STT_PROVIDER=elevenlabs`) |
| Advisory explanation | Natural-language explanation of stored findings | OpenAI `gpt-6.1-sol`, reasoning effort high |
| Trusted reference | Comparison mushaf text | Quranpedia Hafs, mushaf id 1, no API key |
| Library content | Student Qur’an Library (verses, translations, reciters) | Quran Foundation Content API |
| Media | Recitation audio | Cloudinary, or local disk when Cloudinary is unset |
| Persistence | Accounts, sessions, recitations, analyses, findings, evaluations | MySQL 8.4 via JDBC |
| Optional platform services | Live Tasmi meetings, mail | Zoom; SMTP, Brevo, or Resend |

| External call | Endpoint | Role in the loop |
|---|---|---|
| ElevenLabs | `POST https://api.elevenlabs.io/v1/speech-to-text` | Transcription |
| Quranpedia | `GET https://api.quranpedia.net/v1/mushafs/1/{surah}` | Trusted Hafs text |
| OpenAI | `POST https://api.openai.com/v1/chat/completions` | Explanation of stored findings |
| Quran Foundation | `{QF_API_ENDPOINT}/content/api/v4/...` | Library only |
| Cloudinary | Cloudinary upload API | Durable audio when configured |

API keys are read from environment variables on the server. They are never sent to the browser.

Full note: [docs/architecture/learning-loop.md](docs/architecture/learning-loop.md).

---

## 6. Empirical validation

Recorded on the isolated challenge environment. These are functional and alignment results, not recognition-accuracy or learning-gain claims.

| Suite | Result | Record |
|---|---|---|
| Functional and end-to-end cases | 31 / 31 passed | [docs/challenge/validation.md](docs/challenge/validation.md) |
| Deterministic alignment self-check (`ArabicComparisonSelfCheck`) | 32 / 32 passed | Same record |

Covered behaviour includes automatic analysis, Quranpedia comparison and outage, instructor Accept / Edit / Reject / Add, the publication gate, unpublished-result isolation, student IDOR (unowned and missing ids both return HTTP 404), Verified Learning Focus, Practice Again lineage, stale analysis-job recovery, silence, short audio, and non-Qur’an speech.

> [!WARNING]
> Do not quote these figures as ASR word-error rate or as evidence that learners improved. The comparison engine is deterministic given a transcript; transcription quality is a separate empirical question and is not claimed here.

---

## 7. Reproducibility and local setup

The challenge copy is isolated. It does not share Compose project name, image tag, host ports, or volumes with a production e-Tasmi installation on the same machine.

```powershell
cd e-Tasmi
copy .env.example .env
docker compose up -d --build
```

| Service | Local URL / port |
|---|---|
| Application | http://localhost:8084 |
| phpMyAdmin | http://localhost:8083 |
| MySQL (host) | localhost:3317 |

Compose project: `etasmi-ai-challenge`. Image: `etasmi-challenge-tomcat:latest`. Containers: `etasmi-challenge-app`, `etasmi-challenge-db`, `etasmi-challenge-phpmyadmin` (Caddy is also defined in Compose). Leave `MYSQLHOST` unset so the app uses the local MySQL container.

First startup creates the schema and a bootstrap administrator at `admin@etasmi.com`. The password is generated by `DBSeeder` and is not printed here. Set `ETASMI_BOOTSTRAP_ADMIN_PASSWORD` in the ignored `.env` file before first start to choose it. Optional synthetic users in `e-Tasmi/e-Tasmi/setup/sample_data.sql` reuse that password hash (`student.demo@etasmi.local`, `instructor.demo@etasmi.local`).

Full steps: [docs/development/installation.md](docs/development/installation.md).

### Configuration (learning loop)

Copy `e-Tasmi/.env.example` to `e-Tasmi/.env`. Never commit `.env`.

| Variable | Purpose |
|---|---|
| `OPENAI_API_KEY` | Finding explanation |
| `OPENAI_EVALUATOR_MODEL` | Default `gpt-6.1-sol` when unset |
| `STT_PROVIDER` | Set `elevenlabs` for Scribe v2 |
| `ELEVENLABS_API_KEY` | Speech-to-text |
| `ELEVENLABS_STT_MODEL` | `scribe_v2` (default) |
| `QURANPEDIA_API_ENDPOINT` | Leave unset, or `https://api.quranpedia.net/v1`. An empty value disables the client |
| `CLOUDINARY_*` | Durable recitation storage on cloud hosts |

On Railway, set `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, and `MYSQLPASSWORD`. Email verification is off by default (`EMAIL_VERIFICATION_ENABLED=false`). Zoom and SMTP are optional and are not required to demonstrate the loop.

---

## 8. Challenge capabilities

| Capability | Behaviour |
|---|---|
| Structured passage | Session stores surah and ayah range |
| Automatic analysis | A new attempt is analysed without a manual click; stale jobs are recovered |
| Trusted reference | Quranpedia Hafs text, not model output |
| Deterministic comparison | Java Levenshtein over orthographic tokens |
| AI explanation | `gpt-6.1-sol` explains stored findings only |
| Instructor verification | Accept, edit, reject, or add each finding |
| Publication barrier | Pending findings cannot be published |
| Verified Learning Focus | Learner sees only accepted, edited, or instructor-added findings |
| Practice Again | Linked attempt on the same enrolment and passage |
| Student Progress | Published recitation history and attempt comparison |
| Safe failure | Silence, non-Qur’an audio, and a missing reference do not invent feedback |

---

## 9. Limitations

- Email verification is disabled in this challenge copy so local registration does not depend on SMTP.
- Payment is a manual QR and receipt flow. There is no ToyyibPay integration.
- Zoom live sessions are optional and are not required to demonstrate the learning loop.
- The Qur’an Library still uses Quran Foundation. The learning-loop comparison source is Quranpedia.
- No recognition-accuracy or learning-improvement statistic is claimed.
- There is no public OpenAPI / Swagger surface.

---

## 10. Repository layout and further documentation

```text
.
├── README.md
├── docs/                      Public documentation
│   ├── architecture/
│   ├── baseline/
│   ├── challenge/
│   ├── development/
│   ├── evidence/
│   ├── submission/
│   └── testing/
└── e-Tasmi/                   Isolated challenge application
    ├── docker-compose.yml
    ├── Dockerfile
    ├── .env.example
    └── e-Tasmi/               Java / JSP source
```

| Document | Contents |
|---|---|
| [docs/README.md](docs/README.md) | Documentation index |
| [docs/architecture/learning-loop.md](docs/architecture/learning-loop.md) | Current architecture |
| [docs/challenge/contribution-log.md](docs/challenge/contribution-log.md) | Challenge-period implementation |
| [docs/challenge/validation.md](docs/challenge/validation.md) | Validation cases and observed results |
| [docs/development/installation.md](docs/development/installation.md) | Local installation |
| [docs/evidence/README.md](docs/evidence/README.md) | Tools, services, and evidence booklet |
| [docs/challenge/history/](docs/challenge/history/README.md) | Earlier audits. Not current implementation |

---

## 11. Participant

**Elhessan Abdelrahman Adam** — Individual entry, Track 03, AI Challenge in Service of Islamic Content 2026.
