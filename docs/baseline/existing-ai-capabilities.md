# Existing AI Capabilities at Pre-Challenge Baseline

## 1. Scope

This document records AI capabilities already present in e-Tasmi before the official challenge development period. It is based on the existing system audit and on the baseline application code. It does not describe planned or challenge-period AI work as existing, and it does not report test results.

Related baseline descriptions are in [existing-system.md](existing-system.md) and [baseline-declaration.md](baseline-declaration.md).

## 2. Existing AI Features

The pre-challenge system has two separate kinds of AI.

**Recitation-analysis path.** An instructor can start an AI-assisted analysis of a submitted recitation. That path retrieves the audio, transcribes it, and produces an on-screen evaluation report. The official score remains the score the instructor types and saves. This path is documented in sections 3 to 6.

**Other student AI features.** The audit also records a student Qur'an assistant, a voice assistant, and a translation fallback. Those features are documented in section 7. They do not evaluate a recitation submission and they do not write an official score.

The audit records no embeddings, vector database, retrieval-augmented generation, or acoustic Tajweed classifier on this path.

## 3. Existing Instructor Recitation AI Pipeline

The instructor initiates the analysis. On the evaluations page the instructor submits `action=analyze` for one recitation. A student cannot start this analysis.

The verified pipeline is:

Student recitation audio → audio retrieval → transcription → AI evaluation → analysis result → instructor review.

1. The student has already submitted recitation audio for an enrollment.
2. The instructor analysis reads that media from its stored location. The audit records a Cloudinary URL or a local `/uploads` file. The servlet refuses the run when the media cannot be read, and it also refuses the run when the session has no expected Qur'an portion.
3. Transcription sends the audio to the OpenAI transcriptions API. The request uses Arabic (`language=ar`) and temperature 0. The model name is resolved as described below.
4. AI evaluation sends the transcript and the expected portion to a chat model and expects JSON. The call uses temperature 0 and a fixed seed. The model name is resolved as described below.
5. The analysis result is shown on the instructor evaluations page.
6. The instructor reviews that result, then enters and saves the official score and feedback separately.

The source code and the Compose environment name different models. This document does not treat those names as the same setting, and it does not report a runtime test of either pair.

- The Java defaults, used only when the environment variables are absent, are `gpt-4o-transcribe` for transcription and `gpt-4o` for evaluation. `RecitationAiAnalysisService` applies `OPENAI_RECITATION_MODEL` when it is set, and otherwise uses `gpt-4o-transcribe`. For evaluation it checks `OPENAI_EVALUATOR_MODEL`, then `OPENAI_CLASSIFIER_MODEL`, then `gpt-4o`. The existing system audit records these application defaults.
- The baseline Docker Compose file overrides those defaults when the host does not set the variables. It passes `OPENAI_RECITATION_MODEL=whisper-1` and `OPENAI_CLASSIFIER_MODEL=gpt-4o-mini`. It does not set `OPENAI_EVALUATOR_MODEL`.
- In that Compose configuration, the effective recitation transcription model is `whisper-1`, and the effective evaluation/classifier path is `gpt-4o-mini`. `gpt-4o-transcribe` and `gpt-4o` are the unset-environment defaults in the Java source. They are not the models selected by that Compose configuration.

If the evaluator call fails or returns an unusable report, the same service falls back to a local Arabic token comparison of the transcript against the expected text. That fallback still produces an on-screen report. It is not a second product feature.

The service can also reject audio that it classifies as non-Qur'anic, when that classification is confident and no Qur'anic passage was identified. A passage mismatch does not by itself cause that rejection.

## 4. Existing Reference Method

The recitation AI comparison uses the session's `quran_portion`.

`quran_portion` is free text. The instructor evaluation servlet reads it from the session row and passes that string as the expected text. Analysis is not started when that text is missing. The schema stores it as a free-text portion string, not as an ayah identifier.

The existing recitation comparison does not use a Qur'an Library verse key as its authoritative reference. The Qur'an Library remains a separate reading and listening area. Its verse text is not the comparison input for this analysis.

## 5. Existing AI Output

A successful analysis result, defined by `RecitationAiAnalysisService.AnalysisResult`, includes the fields below. They are shown to the instructor as an on-screen report. They are not written as the official evaluation.

| Output | What the baseline result contains |
|---|---|
| Transcript | The transcription text |
| Correct words | Words treated as matching the reference |
| Missing words | Words present in the reference and not found in the recitation |
| Incorrect words | Substitutions, each stored as expected text followed by the heard text |
| Extra words | Words present in the recitation and not in the reference |
| Pronunciation notes | A list of notes. The service prompt limits these to the transcript and states that acoustic Tajweed cannot be proved from text alone |
| Passage-related flags | Whether the recitation matches the expected passage, whether passages are mixed, detected passage labels, a passage note, and a Qur'an/non-Qur'an classification with a confidence value |
| Suggested score | An integer from 0 to 100 on the analysis result, plus an accuracy percentage derived from the word lists |
| Summary and feedback | A short instructor summary and a feedback string on the analysis result |

The suggested score is not the official score. The evaluations page tells the instructor to review the report before confirming a final score. Saving an evaluation uses the score and feedback the instructor submits. The audit records that the score fields are not filled from the AI result automatically.

The local fallback fills the transcript, word lists, a templated feedback string, a suggested score, and a note that the AI evaluator was unavailable. Its pronunciation-notes list is empty.

## 6. Existing AI Persistence

The AI analysis result was not stored as a durable structured record. The audit records no AI-analysis table.

After a successful or unsuccessful analyze request, the servlet keeps the result in two places for that response:

- the HTTP session, under a key for that recitation id
- the current request, so the evaluations page can render the report immediately

A later GET of the evaluations page does not read that session value back onto the request. The report is therefore not rehydrated as a persistent AI finding record. When the instructor successfully saves the official evaluation, the servlet removes the session value for that recitation.

## 7. Existing Student AI Features

The audit verifies three student AI features. They are separate from the recitation evaluation workflow. None of them writes a recitation score, and none of them is stored in MySQL.

**Qur'an assistant / chat.** `POST /student/api/quran-assistant/chat` sends a student message to OpenAI `gpt-4o-mini` (`OPENAI_ASSISTANT_MODEL`) and returns a reply. The audit records learning modes such as tafsir, Tajweed, meaning, memorization, word study, and general questions. The reply is shown in the student assistant widget and is not persisted.

**Voice assistant.** The voice endpoints accept spoken audio, transcribe it, request a short reply, and can return speech audio. The audit records `gpt-4o-transcribe` for speech to text and `gpt-4o-mini-tts` for speech output. The turn is not persisted.

**Translation-related AI.** When a Quran Foundation English translation is missing, `POST /student/api/quran-library/translate-ai` can request an English rendering from `gpt-4o-mini`. The audit records that this cache is in memory only.

These features support library and assistant use. They do not compare a submitted recitation with `quran_portion`, and they do not replace instructor evaluation.

## 8. Existing Limitations Relevant to the Challenge

The items below are **PRE-CHALLENGE GAPS**. They describe limits of the baseline system. They are not completed challenge features.

- No persistent structured AI findings.
- No per-finding Accept, Edit, or Reject.
- No instructor verification gate for individual findings.
- No Verified Learning Focus.
- No student-facing learning focus.
- No Practice Again loop.
- No trusted Qur'an reference integrated into the recitation comparison path. Comparison uses the free-text `quran_portion`.

## 9. Evidence

The earlier instructor analysis screenshot was removed because it showed a real account. The behaviour is described in this document, not by that image.

The surrounding pre-challenge workflow is described in [existing-system.md](existing-system.md), sections 4, 6, and 7. The baseline boundary is in [baseline-declaration.md](baseline-declaration.md).

Behaviour in this document is taken from:

- [../challenge/history/ETASMI_CURRENT_SYSTEM_AUDIT.md](../challenge/history/ETASMI_CURRENT_SYSTEM_AUDIT.md) (historical; prefer [../architecture/learning-loop.md](../architecture/learning-loop.md) for the current loop)
- `RecitationAiAnalysisService` for the result fields, transcription step, evaluator step, and local fallback
- `InstructorEvaluationServlet` for the instructor-only analyze action, the session attribute, and removal of that attribute when the official evaluation is saved

## 10. Boundary Statement

This document describes AI functionality that existed in the pre-challenge system. New functionality developed during the official challenge development period will be documented separately as challenge-period contribution.
