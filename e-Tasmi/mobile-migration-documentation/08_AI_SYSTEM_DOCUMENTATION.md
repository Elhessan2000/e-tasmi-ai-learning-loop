# 08 — AI SYSTEM DOCUMENTATION

e-Tasmi has **four** AI subsystems, all backed by OpenAI. Three are student-facing, one is instructor-facing.

| # | Subsystem | Role | Persisted? |
|---|---|---|---|
| 1 | Quran Assistant (text chat) | Student | **No** |
| 2 | Voice Assistant (STT → chat → TTS) | Student | **No** |
| 3 | AI verse translation (Quran Library) | Student | **No** (JVM-local cache only) |
| 4 | Recitation Analysis | Instructor | **No** (HTTP session only) |

**The single most important architectural fact: no AI output is ever written to the database.** There is no transcript column, no AI-score column, no analysis table, and no conversation history table. Every AI result is ephemeral — lost on logout, session timeout, or application restart.

**The second most important fact: AI is never triggered automatically.** Recitation submission does not invoke AI. An instructor must explicitly press "Analyze".

---

## 1. OPENAI CONFIGURATION

| Variable | Purpose | Default | Required |
|---|---|---|---|
| `OPENAI_API_KEY` | Bearer credential for every call | — | **Yes** — all AI is disabled without it |
| `OPENAI_ASSISTANT_MODEL` | Quran Assistant chat | `gpt-4o-mini` | No |
| `OPENAI_TRANSLATION_MODEL` | AI verse translation | `gpt-4o-mini` | No |
| `OPENAI_VOICE_STT_MODEL` | Voice speech-to-text | `gpt-4o-transcribe` | No |
| `OPENAI_VOICE_TTS_MODEL` | Voice text-to-speech | `gpt-4o-mini-tts` | No |
| `OPENAI_VOICE_NAME` | TTS voice | `alloy` | No |
| `OPENAI_RECITATION_MODEL` | Recitation transcription | `gpt-4o-transcribe` | No |
| `OPENAI_EVALUATOR_MODEL` | Recitation evaluation | **`gpt-4o`** | No |
| `OPENAI_CLASSIFIER_MODEL` | Legacy alias, honoured when `OPENAI_EVALUATOR_MODEL` is unset | — | No |

**Recitation evaluation is the only feature that defaults to full `gpt-4o`.** Everything else uses `gpt-4o-mini`. It is therefore both the most expensive call and the most quality-sensitive one — worth knowing before anyone "optimises" it downward.

**Whisper is not used.** Both transcription paths default to `gpt-4o-transcribe`.

All calls use `java.net.HttpURLConnection` with `Authorization: Bearer {key}`. There is no organization-ID variable. **Every call is server-side.** The key never reaches the browser and must never reach a Flutter binary.

**There is no quota, no spend cap, no per-user request budget, and no caching of chat responses.** Any authenticated student can issue unlimited requests.

---

## 2. SUBSYSTEM 1 — QURAN ASSISTANT (TEXT CHAT)

### 2.1 Flow

```
  Student types a question in the Quran Library assistant panel
        │
        ▼
  POST /student/api/quran-assistant/chat        (max body 64 KB)
  { mode, message, history[], context{surahId, surahName, verseKey, ayahNumber} }
        │
        ├─ AuthFilter: session + role STUDENT   → 401 if absent
        ├─ validate: message non-blank, mode recognised
        ├─ truncate message at 2,400 chars
        ├─ keep at most the last 12 history messages
        │
        ├─ build the message array:
        │     [0] system  ← base scope prompt + mode-specific instruction
        │     [1] system  ← verse context (when a verse is selected)
        │     [2..] the trimmed history
        │     [n] user    ← the current message
        │
        ▼
  POST https://api.openai.com/v1/chat/completions
  { model: OPENAI_ASSISTANT_MODEL, temperature: 0.35, max_tokens: 1800, messages: [...] }
        │
        ├─ success → truncate the reply at 12,000 chars
        │            detect the scope-reminder marker
        │            200 { ok:true, reply, mode, scopeReminder }
        │
        ├─ no API key    → 503 { ok:false, error:"ai_not_configured" }
        └─ upstream fail → 502 { ok:false, error:"ai_upstream_failed" }
        │
        ▼
  Rendered in the browser. NOTHING IS SAVED.
  History lives in browser memory and is resent on every turn.
```

### 2.2 Modes

Eight modes, each injecting a different system instruction:

| Mode | Focus |
|---|---|
| `tafsir` | Classical exegesis of the selected verse |
| `tajweed` | Pronunciation and recitation rules |
| `meaning` | Plain-language meaning |
| `memorization` | Ḥifẓ technique and retention advice |
| `word` | Word-by-word Arabic breakdown |
| `asbab` | Asbāb al-nuzūl (occasions of revelation) |
| `tips` | General study guidance |
| `general` | Unconstrained (still scope-limited) |

### 2.3 Scope enforcement

The base system prompt restricts the assistant to Quran and Islamic-studies topics. When the model detects an off-topic question it emits a marker the servlet converts into `scopeReminder: true`, and the UI shows a gentle redirect notice. **This is prompt-level enforcement only** — there is no classifier or hard filter, so it is best-effort.

### 2.4 Verse context

When the student has a verse open, a second system message injects `surahId`, `surahName`, `verseKey`, and `ayahNumber` so the model can answer "this verse" questions without the student restating them.

### 2.5 Limits

| Limit | Value |
|---|---|
| Request body | 64 KB |
| User message | 2,400 characters (truncated) |
| History | **12 messages** (older turns silently dropped) |
| Reply | 12,000 characters (truncated) |
| `max_tokens` | 1,800 |
| `temperature` | 0.35 |

---

## 3. SUBSYSTEM 2 — VOICE ASSISTANT

### 3.1 Full turn — `POST /student/api/quran-assistant/voice/turn`

`multipart/form-data`, max 25 MB. Fields: the recorded audio blob, `lang` (`en` \| `ar` \| `ms`), `mode`.

**Three sequential OpenAI calls:**

```
  Browser MediaRecorder captures audio
        │
        ▼
  POST /student/api/quran-assistant/voice/turn
        │
        ├─ CALL 1 — Speech to text
        │   POST /v1/audio/transcriptions
        │   model = OPENAI_VOICE_STT_MODEL (gpt-4o-transcribe)
        │   language hint from `lang`
        │        │
        │        ├─ empty transcript → 200 { ok:true, transcript:"", noSpeech:true }  ← STOP
        │        ▼
        ├─ CALL 2 — Chat
        │   POST /v1/chat/completions
        │   same prompt construction as the text assistant,
        │   with a language instruction so the reply matches `lang`
        │        │
        │        ▼
        ├─ CALL 3 — Text to speech
        │   POST /v1/audio/speech
        │   model = OPENAI_VOICE_TTS_MODEL (gpt-4o-mini-tts)
        │   voice = OPENAI_VOICE_NAME (alloy)
        │   → MP3 bytes → Base64
        │        │
        │        ├─ TTS fails → still return the text reply, omit `audio`
        │        ▼
        ▼
  200 { ok:true, transcript, reply, mode, scopeReminder,
        audio:"<base64 mp3>", audioMime:"audio/mpeg" }
```

**Latency:** three chained network calls. Expect several seconds end-to-end — an important mobile UX consideration.

### 3.2 Replay — `POST /student/api/quran-assistant/voice/speak`

JSON `{ text, lang }` → Base64 MP3. Used to replay an existing reply without re-running transcription or chat.

### 3.3 Language handling

`lang` accepts `en`, `ar`, `ms`. It is passed as a transcription hint, injected as a reply-language instruction, and used to select the TTS voice, so the spoken answer matches the student's chosen language.

---

## 4. SUBSYSTEM 3 — AI VERSE TRANSLATION

### 4.1 `POST /student/api/quran-library/translate-ai`

```json
{ "verses": [ { "verseKey": "2:255", "arabic": "..." } ] }
```

| Limit | Value |
|---|---|
| Request body | 2 MB |
| Verses per request | **320** |
| Server-side batch size | 30 verses per OpenAI call |

**Response:**
```json
{ "ok": true, "source": "openai", "translations": { "2:255": "..." } }
```

### 4.2 Caching

Results are stored in a process-wide `ConcurrentHashMap` keyed by verse. This is a **JVM-local, unbounded, non-persistent** cache — it is not shared across instances and is lost on restart. A partially failed batch returns whatever succeeded rather than failing the whole request.

### 4.3 Purpose

This supplements, and does not replace, the official Quran Foundation translations. The student can request an AI rendering when a preferred translation resource is unavailable.

---

## 5. SUBSYSTEM 4 — RECITATION ANALYSIS (INSTRUCTOR)

The most complex AI feature and the one most relevant to the platform's purpose.

### 5.1 Complete flow

```
  Instructor opens /instructor/evaluations, picks a recitation, presses "Analyze"
        │
        ▼
  POST /instructor/evaluations   action=analyze&recitationId=X
        │
        ├─ AuthFilter: INSTRUCTOR + verification_status = APPROVED
        ├─ ownership: recitation → enrollment → tasmi_session.instructor_id = me
        │
        ├─ PRECONDITION CHECKS
        │     • OPENAI_API_KEY present?          no → status FAILED
        │     • session.quran_portion non-empty? no → status FAILED
        │       (this is the EXPECTED TEXT — without it there is nothing to compare against)
        │     • audio file resolvable and within size bounds?  no → FAILED
        │
        ├─ FETCH AUDIO
        │     Cloudinary URL → download   |   local /uploads path → read from disk
        │
        ├─ STEP 1 — TRANSCRIBE
        │     POST /v1/audio/transcriptions
        │     model = OPENAI_RECITATION_MODEL (gpt-4o-transcribe)
        │     language hint: Arabic
        │           │
        │           ├─ empty / failed → status CANNOT_EVALUATE   ← STOP
        │           ▼
        ├─ STEP 2 — ANALYZE  (alignment + judgement)
        │     POST /v1/chat/completions
        │     model = OPENAI_EVALUATOR_MODEL (gpt-4o)
        │             falls back to OPENAI_CLASSIFIER_MODEL if set
        │     prompt carries BOTH:
        │        • expected text  = tasmi_session.quran_portion
        │        • actual text    = the Whisper transcript
        │     instructed to return STRUCTURED output:
        │        • is this Quranic recitation at all?
        │        • word-level comparison / differences
        │        • tajwīd observations
        │        • a suggested score 0–100
        │        • narrative feedback
        │           │
        │           ├─ high-confidence "not Quran" → status REJECTED
        │           ▼
        ├─ STEP 3 — STORE EPHEMERALLY
        │     HttpSession attribute:  etasmi.aiAnalysis.{recitationId}
        │     also placed on the request for immediate rendering
        │
        ▼
  Instructor sees: transcript · differences · tajwīd notes · suggested score · feedback
        │
        │   The suggested score is ADVISORY. It is NOT written to evaluation.score
        │   unless the instructor types it into the form.
        ▼
  POST /instructor/evaluations   action=save&recitationId=X&score=&feedback=
        │
        ├─ INSERT evaluation (score, feedback)      ← ONLY the instructor's own values
        ├─ recompute progress.completion_rate = AVG(evaluation.score)
        └─ REMOVE the session key etasmi.aiAnalysis.{recitationId}
                    │
                    ▼
        THE AI ANALYSIS IS NOW PERMANENTLY GONE.
```

### 5.2 Result statuses

| Status | Meaning | Instructor experience |
|---|---|---|
| `OK` | Analysis succeeded | Full report displayed |
| `REJECTED` | High-confidence non-Quranic audio | Warning shown; manual grading still available |
| `CANNOT_EVALUATE` | Transcription empty or failed | Message shown; manual grading still available |
| `FAILED` | No API key, missing `quran_portion`, or file too large/small | Message shown; manual grading still available |

**AI failure never blocks grading.** In every failure path the instructor can still enter a score and feedback manually. This is correct, defensive design and must be preserved.

### 5.3 The `quran_portion` dependency

`tasmi_session.quran_portion` is the expected text. Without it there is no reference for comparison and analysis returns `FAILED`. It is an **optional** field at session creation, so an instructor can unknowingly create a session for which AI analysis will never work. Worth surfacing in the mobile UI at creation time.

### 5.4 Arabic handling

- Transcription is hinted to Arabic.
- The comparison prompt is built around Arabic text and, per the transcript of the audit, does not implement custom diacritic (taškīl) normalization — comparison quality depends on the model.
- The database is `utf8mb4` throughout, so Arabic storage is safe.

---

## 6. WHAT AI DOES **NOT** DO

Confirmed by exhaustive search. The mobile app must not assume any of the following exists.

| Assumption | Reality |
|---|---|
| AI runs automatically on submission | **No.** Instructor-initiated only |
| AI results are stored | **No.** Session-scoped, then discarded |
| Students see AI analysis | **No.** Instructor-only |
| AI auto-assigns the score | **No.** Advisory only |
| Conversation history is saved | **No.** Browser memory, capped at 12 messages |
| There is an admin AI dashboard or usage report | **No AI features for admins at all** |
| There is a general-purpose chatbot | **No.** Scope-limited to Quran/Islamic studies |
| Instructors have an AI assistant | **No.** Chat and voice are student-only |
| There is streaming / SSE output | **No.** Every call is request/response |
| There is a fallback model or provider | **No.** OpenAI only; failure surfaces an error |
| There is offline/on-device AI | **No** |
| Tajwīd is scored algorithmically | **No.** It is an LLM observation, not a signal-processing analysis |

---

## 7. ERROR HANDLING SUMMARY

| Failure | HTTP | Body / behavior |
|---|---|---|
| No `OPENAI_API_KEY` (chat) | `503` | `{ok:false, error:"ai_not_configured"}` |
| OpenAI call fails (chat) | `502` | `{ok:false, error:"ai_upstream_failed"}` |
| Validation failure (chat) | `400` | `{ok:false, error:"..."}` |
| No speech detected (voice) | `200` | `{ok:true, transcript:"", noSpeech:true}` |
| TTS fails (voice) | `200` | Text reply returned, `audio` omitted |
| Translation batch partial failure | `200` | Successful verses returned; failures omitted |
| Analysis: no key / no expected text / bad file | — | Status `FAILED`, manual grading available |
| Analysis: empty transcript | — | Status `CANNOT_EVALUATE` |
| Analysis: not Quranic | — | Status `REJECTED` |

**Uniform principle:** AI failure degrades gracefully and never blocks a core workflow.

---

## 8. MIGRATION ARCHITECTURE FOR AI

### Current

```
   Browser  ──fetch──▶  Servlet  ──HTTPS──▶  OpenAI
                           │
                           └──▶  HttpSession  (discarded)
```

### Target

```
   Flutter  ──Bearer token──▶  PHP API  ──HTTPS──▶  OpenAI
                                  │
                                  ├──▶  MySQL   ← RECOMMENDED: persist analysis results
                                  └──▶  rate limiter / spend cap  ← RECOMMENDED
```

### Required for the mobile build

1. **The OpenAI key stays server-side, always.** A Flutter binary is decompilable; an embedded key is a public key.
2. **Reproduce every limit exactly:** 12-message history, 2,400-char message, 12,000-char reply, 320-verse translation cap, batch size 30, 25 MB voice upload, `temperature 0.35`, `max_tokens 1800`, and the 90-second assistant request timeout.
2a. **Keep the model defaults as they are**, in particular `gpt-4o` for recitation evaluation. Downgrading it to `gpt-4o-mini` to save cost would silently degrade the platform's core pedagogical feature.
3. **Reproduce the prompt construction verbatim** — base scope prompt, mode instruction, verse context, then history, then the user message. Prompt changes alter behavior in ways that are hard to detect.
4. **Preserve the four analysis statuses and the graceful-degradation rule.**
5. **Preserve `quran_portion` as the AI precondition.**

### Recommendations (OPTIONAL — not part of the reproduction requirement)

- **Persist recitation analysis results.** Losing an expensive analysis on session timeout is the weakest point of the current design. This would require new columns or a new table and is therefore a schema change requiring client approval.
- **Add per-user rate limits and a spend cap.** There are none today, and mobile usage is typically higher.
- **Move the translation cache to a shared store** so it survives restarts and works across instances.
- **Consider streaming chat responses** for perceived latency on mobile.
- **Consider persisting assistant conversation history** so a student can resume a thread across devices.

Each of these is a change in behavior, not a reproduction of it, and belongs in the OPTIONAL FUTURE SUGGESTIONS section of doc 13.
