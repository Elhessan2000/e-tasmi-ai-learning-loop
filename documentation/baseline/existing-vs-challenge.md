# Existing System vs. Challenge Contribution

## 1. Purpose

This document establishes the boundary between two sets of functionality:

- functionality already present in e-Tasmi before the challenge
- functionality intended for the official challenge development period

The second set is a plan. Only work actually performed during the official challenge period will later be recorded as challenge contribution. Nothing in the challenge column of this document is treated as present in the repository today.

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

Challenge rows below are planning statements. They use “Planned” and “To be implemented during challenge”. They do not record finished work.

| Capability | Pre-Challenge State | Challenge Contribution | Evidence / Notes |
|---|---|---|---|
| Student recitation submission | Existing | Planned: extend/reuse | [recitation-submission.png](screenshots/recitation-submission.png); [existing-system.md](existing-system.md) |
| Instructor evaluation | Existing | Planned: extend/reuse | [instructor-evaluation.png](screenshots/instructor-evaluation.png); official score and feedback are stored on `evaluation` |
| AI-assisted recitation analysis | Existing; instructor-triggered | Planned: extend | [existing-ai-capabilities.md](existing-ai-capabilities.md); [existing-ai-analysis.png](screenshots/existing-ai-analysis.png) |
| Structured AI findings | Partially existing as an on-screen analysis result | Planned: persist as structured records. To be implemented during challenge | Not present as a durable finding record at baseline. See [existing-ai-capabilities.md](existing-ai-capabilities.md) |
| Trusted Qur'an reference | Existing in the Qur'an Library; not connected to the recitation comparison path | Planned: integrate into the recitation analysis path. To be implemented during challenge | [quran-reference.png](screenshots/quran-reference.png). Baseline comparison uses `quran_portion` |
| Instructor Accept | Not present at baseline | Planned. To be implemented during challenge | Not yet recorded |
| Instructor Edit | Not present at baseline | Planned. To be implemented during challenge | Not yet recorded |
| Instructor Reject | Not present at baseline per finding | Planned. To be implemented during challenge | Not yet recorded |
| Instructor verification gate | Not present at baseline per finding | Planned. To be implemented during challenge | Not yet recorded |
| Verified Learning Focus | Not present at baseline | Planned. To be implemented during challenge | Not yet recorded |
| Student learning-focus display | Not present at baseline | Planned. To be implemented during challenge | Not yet recorded |
| Practice Again | Not present at baseline | Planned. To be implemented during challenge | Not yet recorded |
| Resubmission linkage | Not present at baseline | Planned. To be implemented during challenge | Not yet recorded |
| Continuous learning loop | Not present at baseline | Planned. To be implemented during challenge | Not yet recorded |

## 4. Existing AI Boundary

At baseline, AI analysis already existed. It was instructor-triggered. It produced an on-screen analysis for that instructor review. Its comparison reference was the session `quran_portion` free-text value. AI results were not persisted as durable structured findings. The instructor still entered and saved the official score and feedback. The suggested score on the analysis screen is not that official score.

The planned challenge contribution is to extend this existing path. This document does not claim the entire AI system as new, and it does not claim that the extension has been carried out.

## 5. Existing Qur'an Reference Boundary

At baseline, Qur'an Library and reference functionality already existed. The baseline screenshots show a reciters catalogue and a surah text view with listen and translation controls. The recitation AI comparison did not use the library verse key as its authoritative comparison reference.

The planned challenge contribution is to connect a trusted Qur'an reference to the recitation analysis workflow. That connection is not present at baseline.

## 6. Planned Learning Loop

The sequence below is a **PLANNED CHALLENGE-PERIOD FLOW**. It is not a description of the baseline system.

Student Recitation → AI Analysis → Instructor Review → Accept / Edit / Reject → Verified Learning Focus → Student Practice → Practice Again → Next Recitation

At baseline, the path that exists stops at instructor review of an on-screen analysis, an instructor-entered score and feedback, and the student viewing that stored result. Accept, Edit, Reject, Verified Learning Focus, student practice from that focus, Practice Again, and linkage into a next recitation are not present at baseline.

## 7. Baseline Evidence

Files are in [screenshots/](screenshots/README.md). Each line states the subject of that screenshot. A filename is not evidence that a planned challenge feature exists.

| Screenshot | What it demonstrates |
|---|---|
| [existing-ai-analysis.png](screenshots/existing-ai-analysis.png) | Existing instructor AI-assisted recitation analysis |
| [quran-library.png](screenshots/quran-library.png) | Existing Qur'an Library |
| [quran-reference.png](screenshots/quran-reference.png) | Existing Qur'an reference/library view |
| [instructor-evaluation.png](screenshots/instructor-evaluation.png) | Existing instructor evaluation interface |
| [recitation-submission.png](screenshots/recitation-submission.png) | Existing student recitation submission |
| [student-recitation-result.png](screenshots/student-recitation-result.png) | Existing student result and feedback |

## 8. Documentation Boundary

The pre-challenge state is established by the baseline Git snapshot, baseline documentation, and baseline screenshots. Challenge-period implementation will be recorded separately through challenge contribution logs, commits, validation evidence, and final submission documentation.

The Git snapshot itself is identified in [baseline-declaration.md](baseline-declaration.md). This comparison file does not add a new commit, tag, date, or test result.

## 9. Final Boundary Statement

Pre-existing functionality is not attributed to the challenge period. Work performed during the official challenge development period will be documented separately, and only after it has actually been done. Until that work occurs, this comparison matrix is a planning and boundary document.
