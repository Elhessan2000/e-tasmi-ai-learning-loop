<p align="center">
  <img src="https://img.shields.io/badge/e--Tasmi-AI%20Learning%20Loop-0B1F33?style=for-the-badge" alt="e-Tasmi AI Learning Loop">
</p>

<h1 align="center">e-Tasmi</h1>

<p align="center">
  <strong>Turning instructor-verified findings into the learner’s next practice step.</strong><br>
  Track 03 — Interactive Experiences &amp; Learning Journeys for Islam<br>
  AI Challenge in Service of Islamic Content 2026
</p>

<p align="center">
  <a href="https://e-tasmi.com">
    <img src="https://img.shields.io/badge/Live%20Demonstration-e--tasmi.com-0C7C86?style=for-the-badge&logo=googlechrome&logoColor=white" alt="Live Demonstration: e-tasmi.com">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Servlet%20API-4.0-007396?style=flat-square" alt="Servlet API 4.0">
  <img src="https://img.shields.io/badge/Tomcat-9-F8DC75?style=flat-square&logo=apachetomcat&logoColor=black" alt="Tomcat 9">
  <img src="https://img.shields.io/badge/MySQL-8.4-4479A1?style=flat-square&logo=mysql&logoColor=white" alt="MySQL 8.4">
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker">
  <img src="https://img.shields.io/badge/ElevenLabs-Scribe%20v2-000000?style=flat-square" alt="ElevenLabs Scribe v2">
  <img src="https://img.shields.io/badge/OpenAI-GPT--6.1%20Sol-412991?style=flat-square&logo=openai&logoColor=white" alt="GPT-6.1 Sol">
</p>

---

## 1. What is e-Tasmi?

e-Tasmi closes the gap between a recitation score and the learner’s next practice step. After a student submits audio, the system transcribes it, compares it with a trusted Qur’an passage, and holds every finding until an instructor verifies it. Only published, instructor-verified findings become the learner’s next assignment.

| Deterministic orthography | Advisory AI | Human authority |
|---|---|---|
| Word differences are a Java alignment against Quranpedia Hafs text. The same transcript always yields the same findings. | ElevenLabs Scribe v2 transcribes. GPT-6.1 Sol explains stored findings. The model does not write the mushaf text. | Findings stay `PENDING` until the instructor accepts, edits, or rejects them. Pending work cannot be published. |

> [!IMPORTANT]
> The learner never sees an unverified finding. Publication is a server-side gate, not a hidden button.

## 2. End-to-end workflow

```mermaid
flowchart TD
    A[Student recitation] --> B[Dual engine<br/>ElevenLabs Scribe v2 transcript<br/>Java diff against Quranpedia Hafs]
    B --> C[Instructor verification gate<br/>Accept · Edit · Reject]
    C --> D[Verified learning focus<br/>Practice Again]
    D --> A
```

1. **Student recitation.** Audio is stored for a session that already names the surah and ayah range.
2. **Dual engine.** Scribe v2 produces the Arabic transcript. Quranpedia supplies Hafs mushaf text (`GET /mushafs/1/{surah}`). A deterministic orthographic alignment computes the findings. GPT-6.1 Sol then explains those findings.
3. **Instructor verification.** Each finding is accepted, edited, rejected, or added by the instructor who owns the session.
4. **Verified learning focus.** After publication, the learner sees the score, the instructor’s words, and the practice list, then starts a linked Practice Again attempt.

> [!NOTE]
> If Quranpedia cannot be reached, the comparison is withheld and no Qur’an text is generated. Silence stays `CANNOT_EVALUATE`. Non-Qur’an audio is `REJECTED` and is not retried.

## 3. Verification gate

| Instructor action | Stored state | Shown to the learner after publication |
|---|---|---|
| Accept | `ACCEPTED` | Yes |
| Edit | `EDITED` | Yes |
| Add | `INSTRUCTOR_ADDED` | Yes |
| Reject | `REJECTED` | No |
| Not yet reviewed | `PENDING` | No. Publication is refused. |

An ordinary published review must carry a Quranpedia reference. Student Progress then shows that published history and compares the latest attempt with the one it was practised from.

## 4. Stack at a glance

| Layer | What runs there |
|---|---|
| Presentation | JSP and project CSS on Tomcat 9 |
| Orchestration | Java 17 servlets. Servlet API 4.0 |
| Speech and explanation | ElevenLabs Scribe v2 · OpenAI GPT-6.1 Sol |
| Trusted text | Quranpedia Hafs API |
| Library | Quran Foundation Content API |
| Persistence | MySQL 8.4 |
| Delivery | Docker Compose · Cloudinary for audio when configured |

Architecture detail: [docs/architecture/learning-loop.md](docs/architecture/learning-loop.md)

## 5. Evidence and where to go next

Functional validation recorded **31 / 31** workflow cases and **32 / 32** deterministic alignment checks. These results describe the learning loop. They are not a claim about recognition accuracy or learning gain.

| | |
|---|---|
| Live system | [https://e-tasmi.com](https://e-tasmi.com) |
| Install locally | [docs/development/installation.md](docs/development/installation.md) |
| Validation record | [docs/challenge/validation.md](docs/challenge/validation.md) |
| What was built | [docs/challenge/contribution-log.md](docs/challenge/contribution-log.md) |
| Tools and licences | [docs/evidence/README.md](docs/evidence/README.md) |
| Documentation index | [docs/README.md](docs/README.md) |

<p align="center">
  <strong>Elhessan Abdelrahman Adam</strong><br>
  Individual entry · Track 03<br>
  AI Challenge in Service of Islamic Content 2026
</p>
