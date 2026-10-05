# Existing System vs. Challenge Contribution

## 1. Purpose

This document establishes the boundary between two sets of functionality:

- functionality already present in e-Tasmi before the challenge
- functionality intended for the official challenge development period

The challenge column records work that has since been implemented. It was not present in the baseline snapshot `a358719`. Evidence for the delivered column is [../challenge/validation.md](../challenge/validation.md).

Sources are the existing system audit and the completed baseline documents: [existing-system.md](existing-system.md), [existing-ai-capabilities.md](existing-ai-capabilities.md), and [baseline-declaration.md](baseline-declaration.md).

## 2. Pre-Challenge Baseline

The baseline system already supports this recitation path:

- A student submits recitation audio for an enrolled tasmi session.
- An instructor evaluates that submission.
- The instructor can trigger AI-assisted recitation analysis.
- That analysis shows a transcript and analysis findings on the instructor screen.
- The instructor enters and saves the official score and feedback.
- The student can view that stored result and feedback.
- A Qur'an Library and reference view already exist for reading and listening. That library is separate from the recitation comparison path, which uses the session's free-text `quran_portion`.

## 3. Comparison Matrix

Challenge rows below name the delivered behaviour and the validation id. They do not claim that the baseline screenshots show those features.

| Capability | Pre-Challenge State | Challenge Contribution | Evidence / Notes |
|---|---|---|---|
| Student recitation submission | Existing | Reused | [recitation-submission.png](screenshots/recitation-submission.png) |
| Instructor evaluation | Existing | Reused, with a publication gate | E2E-02, E2E-03 |
| AI-assisted recitation analysis | Existing; instructor-triggered; not stored as findings | Extended: automatic analysis, persisted rows | E2E-01, E2E-08 |
| Structured AI findings | On-screen result only | Delivered as stored finding rows | V-02–V-05, E2E-10 |
| Trusted Qur'an reference | Library only; comparison used free-text `quran_portion` | Delivered. Quranpedia Hafs `GET /mushafs/1/{surah}` | E2E-17, E2E-18 |
| Instructor Accept | Not present at baseline | Delivered | E2E-03 |
| Instructor Edit | Not present at baseline | Delivered | E2E-07 |
| Instructor Reject | Not present at baseline per finding | Delivered | E2E-07 |
| Instructor-added finding | Not present at baseline | Delivered | E2E-07 |
| Instructor verification gate | Not present at baseline per finding | Delivered. Pending findings block publish | E2E-02 |
| Verified Learning Focus | Not present at baseline | Delivered on the published student result | E2E-05 |
| Student learning-focus display | Not present at baseline | Delivered. Pending and rejected findings are hidden | E2E-04, E2E-05 |
| Practice Again | Not present at baseline | Delivered. Parent id and next attempt number | E2E-08 |
| Resubmission linkage | Not present at baseline | Delivered as `parent_recitation_id` | E2E-08, E2E-15 |
| Continuous learning loop | Not present at baseline | Delivered through publish and Practice Again | E2E-05, E2E-08, E2E-17 |
| Student Progress history | Existing snapshot only | Delivered on the existing page. No new table. Not part of the 31 functional cases | Student Progress section of [../challenge/validation.md](../challenge/validation.md) |

## 4. Existing AI Boundary

At baseline, AI analysis already existed. It was instructor-triggered. It produced an on-screen analysis for that instructor review. Its comparison reference was the session `quran_portion` free-text value. AI results were not persisted as durable structured findings. The instructor still entered and saved the official score and feedback. The suggested score on the analysis screen is not that official score.

That extension has been carried out. The baseline AI system is still not claimed as new work. The current explainer is GPT-6.1 Sol. Baseline rows that name `gpt-4o` describe the pre-challenge evaluator.

## 5. Existing Qur'an Reference Boundary

At baseline, Qur'an Library and reference functionality already existed. The baseline screenshots show a reciters catalogue and a surah text view with listen and translation controls. The recitation AI comparison did not use the library verse key as its authoritative comparison reference.

That connection is the challenge-period Quranpedia path. It was not present at baseline. Quran Foundation remains the library.

## 6. Delivered Learning Loop

The sequence below is the implemented challenge flow. It is not a description of the baseline system.

Student Recitation → ElevenLabs Scribe v2 → Quranpedia Hafs text → Java comparison → GPT-6.1 Sol explanation → Instructor Accept / Edit / Reject / Add → Published Verified Learning Focus → Practice Again → Next Recitation

Published results also appear on Student Progress. That page keeps the existing Learning Snapshot.

At baseline, the path stopped at instructor review of an on-screen analysis, an instructor-entered score and feedback, and the student viewing that stored result.

## 7. Baseline Evidence

Files are in [screenshots/](screenshots/README.md). Each line states the subject of that screenshot. A baseline filename is not evidence of a challenge-period feature.

| Screenshot | What it demonstrates |
|---|---|
| [quran-library.png](screenshots/quran-library.png) | Qur'an Library home, synthetic account |
| [instructor-evaluation.png](screenshots/instructor-evaluation.png) | Empty evaluations list, synthetic instructor |
| [recitation-submission.png](screenshots/recitation-submission.png) | Recitation page, synthetic student |

## 8. Documentation Boundary

The pre-challenge state is established by the baseline Git snapshot, baseline documentation, and baseline screenshots. Challenge-period implementation is recorded in [../challenge/contribution-log.md](../challenge/contribution-log.md) and [../challenge/validation.md](../challenge/validation.md).

The Git snapshot itself is identified in [baseline-declaration.md](baseline-declaration.md). This comparison file does not add a new commit, tag, date, or test result.

## 9. Final Boundary Statement

Pre-existing functionality is not attributed to the challenge period. The challenge column above is delivered work. It was absent from baseline snapshot `a358719`.
