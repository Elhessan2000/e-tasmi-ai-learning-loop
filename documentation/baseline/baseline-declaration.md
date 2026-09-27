# Pre-Challenge Baseline Declaration

## 1. Project Identity

| Item | Value |
|---|---|
| Project | e-Tasmi AI Learning Loop |
| Challenge | AI Challenge in Service of Islamic Content 2026 |
| Track | Track 03 — Interactive Experiences & Learning Journeys for Islam |
| Participant | Elhessan Abdelrahman Adam |
| Entry type | Individual |

## 2. Purpose

This document establishes the documented starting state of the existing e-Tasmi platform before the official challenge development period. It records the Git baseline, the isolated local environment used to inspect that copy, and the pre-challenge behaviour confirmed from the existing system audit. It does not describe challenge-period implementation.

## 3. Baseline Source Snapshot

This is the Git snapshot of the pre-challenge source. It is the baseline commit itself.

| Item | Value |
|---|---|
| Branch | `master` |
| Baseline commit | `a35871918907d7a403c9469537c25f940009b340` |
| Short commit | `a358719` |
| Commit message | chore: establish pre-challenge baseline |
| Baseline tag | `baseline-pre-ai-challenge-2026` |

The tag points at commit `a358719`. This declaration does not move the tag or rewrite history.

Docker and host-port isolation changes made later, so this challenge copy can run safely beside the original e-Tasmi installation, are not part of baseline commit `a358719`.

## 4. Baseline Inspection Environment

This is the later local environment used to inspect and capture the baseline. It is not the baseline source commit, and it is not a production configuration.

| Item | Value |
|---|---|
| Application | http://localhost:8084 |
| phpMyAdmin | http://localhost:8083 |
| MySQL host port | 3317 |
| Internal application database connection | `jdbc:mysql://db:3306/etasmi` |
| Java | 17 |
| Tomcat | 9 |
| MySQL | 8.4 |
| Docker | Docker Compose |

Inside Docker, the application reaches MySQL on the Compose service `db` at port 3306. Host port 3317 is only the published port of this later inspection environment.

Those host ports, container names, and volume names were introduced after commit `a358719` so the challenge copy would not bind the original e-Tasmi ports or reuse its Docker volumes. They are inspection setup, not baseline source.

## 5. Existing System at Baseline

At the baseline, the pre-challenge platform already included the following behaviour, as recorded in the existing system audit:

- A student can submit a recitation by uploading or recording audio.
- An instructor can evaluate a submitted recitation and record a score and feedback.
- An instructor can start AI-assisted recitation analysis from the instructor evaluation flow.
- The student can see the stored evaluation result and feedback.
- A Qur'an Library is already present.
- Qur'an reference and library functionality already exists as a library, separate from the recitation comparison path described in section 6.

## 6. Existing AI Capability

The pre-challenge system already had AI-assisted recitation analysis. That analysis is started by the instructor. It is not described here as a student-facing learning loop.

The existing audit describes this pipeline:

audio retrieval → transcription → AI evaluation → analysis result → instructor review/evaluation.

The expected passage for that analysis is the session's `quran_portion` value: a free-text reference entered for the session in the existing recitation workflow. The audit records that this comparison uses that free-text portion, not ayah identifiers from the Qur'an Library.

The analysis result is shown for the instructor's review. The human evaluation (score and feedback) is what the existing evaluation record stores for the student.

## 7. Known Pre-Challenge Gaps

These gaps are the boundary between the baseline and later challenge work. They are recorded here so that challenge-period additions are not mistaken for pre-existing functionality.

- AI findings were not persistently stored as structured records.
- There was no per-finding Accept/Edit/Reject workflow.
- There was no Verified Learning Focus.
- There was no student-facing AI learning-focus workflow.
- There was no Practice Again learning loop.
- There was no parent linkage for resubmissions.
- A trusted Qur'an reference existed in the library, but it was not integrated as the recitation reference path. Recitation analysis used the session's free-text `quran_portion`.

None of these missing behaviours is claimed as completed challenge work in this document.

## 8. Evidence

Screenshot evidence for this baseline is stored in:

`documentation/baseline/screenshots/`

Image files present in that folder:

- `login.png` — Existing login interface
- `student-dashboard.png` — Existing student dashboard
- `recitation-submission.png` — Existing student recitation submission
- `student-recitation-result.png` — Existing student result and feedback
- `instructor-dashboard.png` — Existing instructor dashboard
- `instructor-evaluation.png` — Existing instructor evaluation interface
- `existing-ai-analysis.png` — Existing instructor AI-assisted recitation analysis
- `quran-library.png` — Existing Qur'an Library
- `quran-reference.png` — Existing Qur'an reference/library view
- `existing-recitation-workflow-student.png` — Existing student-side recitation workflow
- `existing-recitation-workflow-instructor-completed.png` — Existing instructor-side completed recitation workflow

`README.md` in that folder is an index, not a screenshot. No other image files were present when this declaration was completed.

## 9. Scope Boundary

Functionality documented in this baseline section represents the pre-challenge state. Challenge-period contribution will be documented separately in documentation/challenge/ and will identify the new work performed during the official challenge development period.

## 10. Declaration

The baseline Git commit, the baseline tag, this documentation, and the screenshots listed above are intended to establish the starting state of the e-Tasmi project. They distinguish pre-existing work from additions made later during the official challenge development period. Challenge-period work is not part of this baseline and is not described here as already completed.

Baseline commit: `a358719`

Baseline tag: `baseline-pre-ai-challenge-2026`
