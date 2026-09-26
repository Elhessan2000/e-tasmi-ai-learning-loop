# e-Tasmi': Platform Architecture, Workflows, and Features

## 1. Executive Summary / Overview

e-Tasmi' is a web-based Qur'anic recitation and evaluation platform designed to digitalize and augment traditional *Tasmi'* practice—the instructor-supervised recitation, memorization, and oral assessment of the Holy Qur'an. Conventional Tasmi' is pedagogically robust but operationally fragmented: scheduling, audio collection, grading, feedback, and progress records are often distributed across messaging applications, spreadsheets, and disconnected live-video links. e-Tasmi' consolidates these activities into a single, role-based learning environment in which students submit recitations tied to assigned Surahs and Ayahs, instructors manage classes and evaluations, and automated analysis accelerates—but does not replace—expert review.

The platform serves two primary instructional roles. **Students** enrol in Tasmi' sessions, join live classes, record or upload recitations, track completion and scores, consult an embedded Qur'an study assistant, and review instructor-approved feedback. **Instructors** create and administer sessions, monitor enrolments and submissions, analyse recitations with an automated assessment engine, and issue the final grade and qualitative comments. A third administrative role supports user verification, payments oversight, and system reporting, but is outside the instructional core described here.

Two complementary AI subsystems are embedded in this workflow. A student-facing conversational assistant, powered by large language models (LLMs) accessed through a server-side application programming interface (API), provides on-demand guidance on tafsir, tajweed rules, memorization technique, and verse meaning. An instructor-facing assessment engine combines automatic speech recognition (ASR) with phonetic and lexical alignment to produce a structured preliminary report—transcript, word-level discrepancies, passage-match flags, pronunciation notes, and a suggested score. That report is presented to the qualified instructor as a recommendation. Only the instructor's saved score and feedback become the official evaluation.

The resulting architecture is a **hybrid evaluation pipeline**: Stage 1 performs automated acoustic and textual pre-evaluation; Stage 2 places the audio, the expected passage, and the AI report under expert human review, after which the instructor locks the final decision. This design uses computation to absorb time-consuming transcription and alignment work while keeping pedagogical judgement, theological precision, and grading authority with the Qur'an instructor.

---

## 2. Core Platform Capabilities and Architecture

### 2.1 Purpose and instructional scope

e-Tasmi' operationalizes the full Tasmi' cycle as a managed digital workflow:

1. Session definition (title, schedule, assigned Qur'anic portion, mode, capacity, and payment terms).
2. Student enrolment and, where applicable, payment confirmation.
3. Live recitation via integrated video conferencing, and/or asynchronous recitation submission.
4. Automated pre-evaluation of submitted audio against the assigned passage.
5. Instructor review, annotation, scoring, and publication of feedback.
6. Persistent progress, attendance, and evaluation history for both the learner and the teacher.

Assigned content is expressed as a *Qur'an portion* on each Tasmi' session (for example, a named Surah, an ayah range, or a memorization assignment). Recitations are bound to the student's enrolment in that session, so every audio file is evaluated in the context of a specific lesson rather than as an unanchored recording.

### 2.2 Multi-role access and application stack

The system is a three-tier web application:

| Layer | Implementation |
| --- | --- |
| Presentation | JSP views and client-side JavaScript (dashboards, Recitation Studio, evaluation portal, chat and voice UI, MediaRecorder for in-browser capture) |
| Application | Java Servlet controllers and service layer on Apache Tomcat; session-based authentication with role-specific route protection |
| Data | MySQL relational schema: users, instructors, students, Tasmi' sessions, enrolments, recitations, evaluations, payments, progress, attendance, notifications, and audit records |
| External services | OpenAI REST APIs (LLM, ASR, text-to-speech) invoked only from the server; Zoom for live sessions; Qur'an Foundation content APIs for library text, reciters, and tafsir; Cloudinary and/or local storage for media |

Authenticated roles are **Student**, **Instructor**, and **Administrator**. Login establishes an HTTP session. Instructors require administrative approval before instructional functions are enabled. API keys for speech and language models remain on the server and are never exposed to the browser.

### 2.3 Functional modules by role

**Student module.** Session catalogue and enrolment; live Tasmi' (embedded or external Zoom join); Recitation Studio (record or upload audio bound to an enrolment and assigned portion); recitation history with evaluation status; progress dashboard (completion rate, submissions, evaluated recitations, attendance); Qur'an Library (Surah/Ayah reading with reciter audio, translations, and tafsir); embedded Qur'an Assistant (text and turn-based voice); payments and receipts; profile and notifications.

**Instructor module.** Session creation and roster management; calendar and live-session hosting; evaluation portal (active vs. reviewed sessions, pending students, per-recitation audio review); AI Assessment Assistant (transcription, alignment, preliminary score and feedback draft); final score and qualitative feedback persistence; attendance; session materials; payment settings and verification as implemented.

**Shared instructional artefacts.** Each Tasmi' session carries a `quran_portion` used both as the student's assignment label and as the *expected text* for automated analysis. Recitations store an audio path (remote URL or local upload). Evaluations store the instructor's score and feedback. Progress records maintain a completion rate that is updated as enrolments, submissions, and evaluations accumulate.

### 2.4 End-to-end instructional data flow

```
Instructor creates Tasmi' session (portion, schedule, capacity)
        │
        ▼
Student enrols → (optional) payment confirmed → enrolment APPROVED
        │
        ├── Live session (Zoom)
        └── Recitation Studio: record / upload audio → recitation record
                    │
                    ▼
         Instructor Evaluation Portal
                    │
                    ├── Stage 1: ASR + alignment + preliminary report
                    └── Stage 2: instructor listens, edits, scores, saves
                    │
                    ▼
         Official evaluation visible to student; progress updated
```

This flow is the operational backbone of e-Tasmi'. The AI components described in Sections 3–5 attach to it; they do not form a separate product.

---

## 3. Student Module and AI Integration (Chatbot Engine and Models)

### 3.1 Student dashboard and learning workspace

After authentication, the student workspace presents a dashboard of upcoming and active sessions, average score where evaluations exist, enrolment status, and shortcuts into recitations, progress, the Qur'an Library, and live class. Navigation is consistent across Recitation Studio, My Sessions, Progress, Payments, and Profile. Localisation supports English, Arabic, and Bahasa Melayu, including right-to-left layout for Arabic.

### 3.2 Recitation submission and audio management

The Recitation Studio implements a session-first submission workflow:

1. The student selects an **approved enrolment**. Paid sessions additionally require a successful payment before submission is enabled.
2. The interface displays the assigned **Surah / portion** for that session, so the recording is always tied to a known lesson.
3. The student either **records live** in the browser (MediaRecorder) or **uploads** a previously captured audio file. Multipart upload limits accommodate typical recitation lengths (application-level file-size caps, with a stricter bound applied later by the assessment engine).
4. Audio is stored (Cloudinary or local `/uploads`) and a recitation record is created against the enrolment.
5. The student receives confirmation that the instructor will review the submission. Subsequent visits show history: portion, session, submission date, and whether the recitation has been evaluated.

This design preserves the traditional pairing of *assignment → recitation → teacher hearing*, while making the audio artefact durable, replayable, and independently reviewable.

### 3.3 Progress tracking and history

The Progress view aggregates a learning snapshot:

- **Completion rate** persisted per student and displayed as a percentage bar.
- **Enrolments:** total vs. approved; completed sessions.
- **Recitations:** number submitted vs. number already evaluated.
- **Attendance:** marked sessions and present rate.
- **Payments:** pending vs. successful, where the session is paid.

The dashboard and recitation history together give the student a longitudinal record of attempts and outcomes. Official scores and written feedback appear only after the instructor has saved an evaluation; preliminary AI drafts are not published as student-facing grades.

### 3.4 Qur'an Library as study context

The Qur'an Library is the student's reading and listening surface: chapter list, verse text, reciter audio, translations, and tafsir resources. When the assistant is open, the currently displayed Surah and Ayah can be passed as **reading context**. Vague queries such as “what does this mean?” or “how do I pronounce this word?” are therefore resolved against the verse on screen rather than requiring the student to re-specify chapter and verse numbers.

### 3.5 AI chatbot assistant: role

The e-Tasmi' Qur'an Assistant is an interactive study companion embedded in the student view (floating panel with text chat and optional voice). It is not a grader. Its role is to provide immediate, lesson-relevant guidance so that practice between live Tasmi' sessions remains structured. Supported task modes include:

| Mode | Pedagogical focus |
| --- | --- |
| Tafsir | Meaning, context, and key scholarly points |
| Tajweed | Applicable rules (e.g., *madd*, *noon sakinah*, *tanween*, *makharij*), common mistakes, and practice steps |
| Meaning | Concise translation and deeper sense of the ayah |
| Memorization (*hifz*) | Chunking, repetition schedules, similar-ayah traps, revision tips |
| Word analysis | Root, morphology, Qur'anic usage, and a simple gloss |
| Asbab al-Nuzul | Historical context of revelation where known |
| Tips | Study habits and recitation practice advice |
| General | In-scope Qur'anic learning support |

The assistant mirrors the language of the student's latest message (English, Arabic, or Bahasa Melayu), may cite Arabic script with diacritics, and declines off-topic requests (unrelated homework, general chat, non-Qur'anic domains) with a short invitation to return to Qur'an study. It is instructed not to invent ayah text, hadith, or chains of narration, and to recommend consulting a qualified teacher when uncertain. Formal assessment remains the instructor's responsibility.

### 3.6 Chatbot technology and models

The assistant is implemented as a server-side NLP service over the OpenAI Chat Completions API. The default conversational model is **`gpt-4o-mini`**, a GPT-4-family Transformer decoder, overridable by environment configuration. This places the student assistant in the class of large language models (for example, OpenAI GPT-4 and related fine-tuned Transformer architectures) integrated via API to produce context-aware replies.

**Text path.** The browser posts the student message, selected mode, a bounded recent history window (up to 12 messages), and optional Surah/Ayah context to an authenticated servlet. The service composes a domain-scoped system prompt plus a contextual user payload, calls the LLM (temperature 0.35 for precise written answers; higher token budget for structured Markdown), and returns the reply. Chat history is client-supplied for the turn window; the server does not persist long-term conversational memory in the database.

**Voice path.** A turn-based pipeline provides hands-free study: microphone capture with silence detection → speech-to-text (`gpt-4o-transcribe`, Whisper-family ASR) → the same assistant service in a spoken-tutor persona (short, markdown-free replies) → text-to-speech (`gpt-4o-mini-tts`) playback. This is sequential turn-taking, not continuous streaming speech-to-speech. Language hints (e.g., Arabic) may be passed to the transcriber.

**Controls.** Message length is capped; API credentials never leave the server; replies that the service classifies as off-topic refusals are flagged for the client. Temperature, token limits, and persona differ between text (detailed, structured) and voice (brief, oral) so that latency and pedagogical tone match the channel.

---

## 4. Instructor Module and AI Integration (Assessment Engine and Models)

### 4.1 Instructor dashboard and class management

The instructor workspace centres on Tasmi' session ownership. Instructors create sessions with schedule, duration, assigned Qur'an portion, capacity, and delivery mode; they view a calendar of upcoming classes; they host live Zoom sessions; and they inspect enrolments per session. Roster views distinguish students who have submitted recitations from **pending** students (approved enrolments with no submission yet), which supports follow-up before a live or asynchronous deadline.

Group and individual progress are visible through session grouping, evaluation counts, and the evaluation portal's active/reviewed split. Instructors can mark a session as reviewed (moving it out of the active queue) or reopen it. Attendance and session materials complete the class-management surface.

### 4.2 Evaluation portal

The evaluation portal (`/instructor/evaluations`) is the operational grading workspace. Recitations belonging to the instructor are loaded with student identity, session title, assigned portion, audio location, and existing evaluation state. The portal is organised as:

- **Active sessions** — submissions still in the review queue.
- **Reviewed sessions** — sessions the instructor has marked complete, reopenable if needed.
- **Per-recitation drawer** — audio player, assigned portion, AI analysis action, score field, and qualitative feedback.

The instructor listens to the original recording, optionally runs automated analysis, edits any suggested score and comments, and saves. Persistence of score and feedback is performed only through the instructor's save action. A successful save clears the in-session AI draft for that recitation so that the stored evaluation is unambiguously the instructor's.

### 4.3 AI assessment assistant: role

The instructor-facing assessment engine processes submitted audio together with the session's expected Qur'an text. It is designed to:

- Transcribe the recitation into text using ASR suited to Arabic and Qur'anic diction.
- Classify whether the audio is Qur'anic recitation at all (as opposed to noise, song, or unrelated speech).
- Determine whether the recited content **matches the assigned passage**, independently of whether it is Qur'an (a student who recites *Al-Ikhlas* when *Al-Baqarah* was assigned is still reciting Qur'an, but fails the assignment match).
- Align heard tokens against a reference text: correct, missing, substituted (`expected → heard`), and extra words (ordinary *basmala* / *isti'adha* ignored).
- Surface pronunciation and tajweed notes only where the transcript supports a lexical observation; acoustic rules that a plain transcript cannot prove (madd length, ghunna, qalqalah, makhraj quality) are marked unverified or omitted.
- Produce a preliminary score (0–100), a one-sentence instructor summary, and short student-facing feedback.

The engine therefore analyses tajweed and pronunciation **to the extent recoverable from ASR plus alignment**, and flags discrepancies for the instructor's ear. It does not publish a grade.

### 4.4 Assessment technology and models

The pipeline is a two-call, server-side service (`RecitationAiAnalysisService`):

| Step | Model (default) | Technology class | Input | Output |
| --- | --- | --- | --- | --- |
| 1. Transcription | `gpt-4o-transcribe` | ASR / large audio models in the OpenAI Whisper family; the same architectural class as Whisper and wav2vec 2.0 systems specialised for Arabic phonetics | Recitation audio bytes (size-bounded) | Transcript text |
| 2. Evaluation | `gpt-4o` (env-overridable) | GPT-4-family Transformer LLM | Expected passage + transcript | Structured JSON report |

Both models are invoked over HTTPS from the application server. The transcriber uses the OpenAI `/v1/audio/transcriptions` endpoint (the same family historically associated with Whisper). The evaluator uses Chat Completions with `response_format: json_object`, **temperature 0**, and a **fixed seed** so that identical inputs tend to yield repeatable drafts.

**Guardrails at intake.** Expected text is mandatory (taken from the session's Qur'an portion). Audio shorter than a conservative minimum (~3 s) is rejected as unevaluable; files above 25 MB are refused at the analysis layer. Empty transcripts yield a cannot-evaluate outcome. Non-Qur'an audio is rejected only when the evaluator reports high confidence (≥ 0.85) that the content is not Qur'an **and** no Qur'anic passages were identified—passage mismatch never triggers rejection.

**Local fallback.** If the LLM evaluation call fails, a deterministic Arabic tokenisation and word-diff alignment still produces a useful discrepancy list so the instructor is not blocked by an upstream outage.

**Structured report fields.** `is_quran`, `is_quran_confidence`, `matches_expected_passage`, `detected_passages`, `mixed_passages`, `reference_text`, `correct_words`, `missing_words`, `incorrect_words`, `extra_words`, `pronunciation_notes`, `score`, `summary`, `feedback`. Accuracy percentage for the UI is derived from the word lists:  
\(\mathrm{accuracy} = 100 \times N_{\mathrm{correct}} / (N_{\mathrm{correct}} + N_{\mathrm{missing}} + N_{\mathrm{incorrect}})\).

The evaluator is contractually forbidden from inventing Qur'an text: when the passage matches, `reference_text` must be a verbatim copy of the assigned lesson text. Word-level “incorrect” flags are allowed only when the transcript clearly shows a different token; plausible ASR artefacts of the correct word are counted as correct. This combination of ASR, pattern matching, and phonetic/lexical alignment is what the instructor sees as the preliminary recommendation.

---

## 5. Hybrid Evaluation Pipeline and Quality Verification

e-Tasmi' produces assessments through a **dual-stage hybrid mechanism**. Automated analysis handles transcription, passage matching, and first-pass scoring; a qualified instructor performs auditory review, correction, and the binding educational decision.

### 5.1 Stage 1 — Automated pre-evaluation

When a student submits a recitation, the audio is stored against the enrolment and the assigned Surah/portion. From the evaluation portal, analysis proceeds as follows:

1. **Media retrieval.** The service loads the recording (remote URL or local file) and validates duration/size constraints.
2. **Speech recognition.** ASR (`gpt-4o-transcribe`) converts the waveform to a transcript. Classical Arabic, tajweed-influenced pronunciation, and speaker variation make this step inherently noisy; the rest of the pipeline is designed around that fact.
3. **Classification and alignment.** A single evaluator call receives the expected lesson text and the transcript. It jointly decides Qur'an vs. non-Qur'an, assigned-passage match vs. mismatch (including mixed-surah recitation), and the word-level diff against a declared reference text.
4. **Preliminary artefacts.** The instructor is shown: transcript; expected text; accuracy; suggested score; lists of correct, missing, incorrect, and extra words; pronunciation notes (lexical only, acoustic claims labelled unverified); a passage-match note; a short summary; and draft student feedback.
5. **Outcome states.** `OK` (report ready, possibly with a passage-mismatch prefix); `REJECTED` (high-confidence non-Qur'an); `CANNOT_EVALUATE` (no speech, transcription failure); `FAILED` (configuration or missing expected text). Drafts are held in the instructor's HTTP session keyed by recitation identifier; they are not written as the official evaluation.

Stage 1 therefore performs the labour-intensive acoustic-to-text conversion and a first alignment against the assigned ayahs. It highlights likely substitutions and omissions so the instructor's listening can be targeted rather than exhaustive from a blank page.

### 5.2 Stage 2 — Expert validation and final decision

The AI report is a **preliminary recommendation**, not a published grade. In Stage 2 the instructor:

1. **Replays the original audio** in the evaluation drawer—the ground-truth signal that ASR cannot fully capture (madd, ghunna, qalqalah, makhraj, rhythm, and pedagogical tone).
2. **Reads the AI flags** (word lists, passage mismatch, unverified pronunciation notes) as a checklist against what is heard.
3. **Corrects** false ASR artefacts, adjusts the score, and rewrites or replaces the feedback with personalised, teacher-authored comments.
4. **Saves** score and feedback through the evaluation service. Only this write creates the durable evaluation record. The session-held AI draft is then discarded.
5. Optionally **marks the session reviewed**, moving the class cohort out of the active queue, or reopens it later.

Students see the instructor-approved result: score, qualitative feedback, and updated progress. They do not receive an unreviewed machine grade.

Quality is thus verified at two complementary layers. Stage 1 verifies *textual correspondence* (was the assigned passage recited, and which tokens appear to differ?). Stage 2 verifies *oral and pedagogical quality* (was the recitation correct to the teacher's standard of tajweed and memorization, and what should the student practise next?). Empty or non-Qur'an submissions are stopped or clearly labelled before they consume grading time; wrong-passage recitations remain visible as Qur'an with an explicit mismatch flag so the instructor can respond proportionately.

### 5.3 Value of the hybrid architecture

The hybrid pipeline assigns work according to comparative advantage. ASR and the evaluator absorb transcription, token alignment, and first-draft commentary—tasks that scale poorly when an instructor must grade many recordings by ear alone. The instructor retains **strict pedagogical integrity** (rubric, encouragement, next-step advice), **theological precision** (no unsupervised machine claim over sacred text), and **absolute final authority** over the recorded score. Repeatable evaluator settings (temperature 0, fixed seed, JSON schema) make drafts inspectable and comparable across similar submissions, while the local word-diff fallback keeps the portal usable if the language-model call is unavailable.

---

## 6. Technical and Pedagogical Value

### 6.1 Technical value

e-Tasmi' demonstrates a complete, operational stack for digital Tasmi' rather than a standalone speech demo or a generic chatbot:

- **Enrolment-bound recitation** couples every audio file to a lesson, a student, and an instructor, which is the prerequisite for accountable assessment.
- **Server-side model orchestration** keeps credentials, prompts, and scoring logic off the client. Student chat, voice turns, and recitation analysis share the same API boundary and domain constraints.
- **Model specialisation by task.** A lighter GPT-4-family model (`gpt-4o-mini`) serves low-latency tutoring; a stronger evaluator (`gpt-4o`) performs structured grading drafts; Whisper-family ASR (`gpt-4o-transcribe`) serves both voice tutoring and recitation transcription; TTS closes the spoken study loop.
- **Structured, auditable drafts.** JSON fields, independent `is_quran` vs. `matches_expected_passage` signals, verbatim reference-text rules, and ASR-artefact caution reduce opaque, uneditable machine judgements.
- **Resilience.** Size/duration checks, cannot-evaluate and rejection states, and a local alignment fallback prevent silent failure in the grading queue.
- **Institutional completeness.** Live Zoom Tasmi', asynchronous studio submission, payments, attendance, notifications, and a Qur'an Library sit on the same identity and session model, so AI features attach to real classes rather than to anonymous files.

### 6.2 Pedagogical value

For **students**, the platform restores continuity between class and independent practice. Recitation Studio captures the oral performance that Tasmi' requires; progress and history make completion visible; the Qur'an Assistant answers tafsir, tajweed, and hifz questions in the student's language, optionally anchored to the ayah currently on screen; voice turns allow practice without typing. Formal grades arrive only from the teacher, which keeps the assistant in a tutoring role and avoids confusing a draft score with a certified result.

For **instructors**, the evaluation portal replaces ad hoc audio collection with a single queue. Automated pre-evaluation shortens the path from “open file” to “know where to listen,” especially for missing verses, substitutions, and wrong-passage submissions. The teacher still hears the student, still writes the feedback, and still owns the mark—the traditional Tasmi' relationship, supported by software rather than displaced by it.

For **the programme as a whole**, hybrid evaluation is the mechanism that makes scale compatible with quality. Acoustic analysis is delegated to ASR and alignment algorithms; authenticity of Qur'anic text, fairness of scoring, and the spiritual–pedagogical character of feedback remain under expert review. e-Tasmi' thus digitalizes the Tasmi' workflow end to end while keeping the qualified instructor as the final arbiter of recitation quality.

---

*This section describes the e-Tasmi' platform as implemented: Java Servlet/JSP on Tomcat, MySQL, OpenAI-hosted GPT-4-family and Whisper-family models, Zoom live sessions, and an instructor-confirmed evaluation record. Model identifiers are defaults and may be overridden by deployment configuration.*
