# Final Report

This report describes the challenge-period work in the isolated e-Tasmi copy. It does not treat that work as part of the pre-challenge baseline.

## Executive summary

The challenge copy compares a student's recitation with a trusted Qur'an passage, lets the instructor accept, edit, reject, or add each finding, and shows the student only the published result. The student can practise the same passage again as a linked attempt. When the trusted reference cannot be loaded, the comparison is withheld and no Qur'an text is generated.

## Problem

A recitation review that invents the expected text, or that shows an unverified AI finding to the student, is not safe to use for Qur'an instruction. The instructor must decide each finding before the student sees a learning focus, and a second attempt must stay on the same enrollment.

## Existing system

Before this challenge work, e-Tasmi already had accounts, sessions, enrollment, recitation upload, instructor evaluation, a Qur'an library, and an instructor AI report that was not stored as structured findings. That behaviour is described in the baseline documents. It is not repeated here as challenge contribution.

## Challenge contribution

Phases 0 through 6, recorded in [../challenge/contribution-log.md](../challenge/contribution-log.md):

- Structured passage on the session, and a Quranpedia Hafs trusted reference.
- Deterministic findings with type, verse key, and word position.
- Persisted analysis history.
- Instructor verification and a server-side publication gate.
- A student result page with verified learning focus and Practice Again.
- Separate instructor cards for a missing reference and a failed analysis.

The learning loop, the instructor verification gate, Quranpedia Hafs comparison, ElevenLabs Scribe v2 transcription, and the Docker Compose challenge stack are implemented and pushed on `master`. Student Progress history was added on 5 Oct 2026 in commit `d4bb3ea`. It does not add a database table and it does not change the existing snapshot formula.

## Architecture

The browser talks to existing servlets. Servlets call services. Services use JDBC prepared statements. New reads for the student projection go through `VerifiedLearningFocusService`, which loads the evaluation stamped `analysis_id` and the finding rows for that analysis. Practice Again is an extra check inside `RecitationService.submit`. The comparison engine does not call a model to decide word differences.

## AI approach

Speech-to-text is ElevenLabs Scribe v2 when `STT_PROVIDER=elevenlabs`. The model is not asked to invent the mushaf text. Word differences are computed in Java from the Quranpedia Hafs reference. OpenAI gpt-6.1-sol explains stored findings only. A missing reference returns `REFERENCE_UNAVAILABLE` and stores no findings.

## Instructor verification

Each stored finding can be accepted, edited, rejected, or added by the instructor who owns the session. Accepted text stays the original proposal. An edit uses the instructor field when it is non-blank. Rejected and pending findings are not shown to the student. Saving the evaluation is refused while any finding on the latest analysis is still pending.

## Student learning loop

The student sees the score and instructor feedback for an existing evaluation. Verified focus, what to practise, and Practice Again appear only after `published_at` is set. A published review with nothing to practise shows a calm empty state. Practice Again creates a new recitation with `parent_recitation_id` set and `attempt_number` equal to one plus the highest attempt on that enrollment. An ordinary submit stays unlinked and keeps attempt number 1.

Student Progress keeps the existing Learning Snapshot. It adds Latest Verified Focus, a comparison with the previous published attempt on the same enrollment (the parent when that parent is published), and Recitation History. History includes only published evaluations. Focus counts include only `ACCEPTED`, `EDITED`, and `INSTRUCTOR_ADDED`. The page loads at most 60 rows and shows 8 until Show more is used. View Result opens `/student/recitation-result`. Practice Again opens `/student/recitations?practice=`. There is no academic term, so the chip says "Last updated".

## Reliability and safety

`REFERENCE_UNAVAILABLE` has its own instructor card. The card states that the comparison was withheld and that no Qur'an text was generated. It does not render the stored reference or transcript. A failed analysis is a separate retry state and does not use that wording. The instructor can still save a manual score. Missing and unowned student result URLs return the same 404.

## Validation results

See [../challenge/validation.md](../challenge/validation.md) for the full table.

Summary:

- Comparison self-check: 32 / 32
- Recorded functional cases: 31 / 31
- Covered: correct, missing, incorrect, extra word, passage mismatch, orthographic variants, reference unavailable, silence, short audio, non-Qur'an speech, publication gate, Practice Again, ownership, and Quranpedia outage
- Student Progress is documented separately in the validation file. It is not included in the 31 functional cases.

## Limitations

- Practice attempts still count in the existing progress average. The formula was not changed.
- Practice Again was verified with a recording already stored for an earlier recitation. ffmpeg is not installed, and the local placeholder files were rejected by storage before a new recording could be saved.
- Third-party terms for OpenAI, ElevenLabs, Quranpedia, and Quran Foundation should be reviewed before a public production launch.
- The demo file is [demo/Live Demo.mp4](demo/Live%20Demo.mp4). This report does not transcribe its frames. The file was not in git history at the time of this documentation pass.
- Behaviour screenshots are not yet in `docs/challenge/challenge-evidence/`.
- Student Progress has no academic-term field. The page says "Last updated". The Learning Snapshot completion rate remains the existing progress calculation.

## Future work

A person still has to capture the behaviour screenshots listed in `docs/challenge/challenge-evidence/README.md`, confirm third-party terms, and review privacy for the public repository.

## Technical stack

The challenge app remains Java servlets on Tomcat 9, JSP, MySQL 8.4, and the existing Docker Compose project. Challenge behaviour was added in Java services and JSP. No new framework was introduced.

## Third-party components

Speech-to-text is ElevenLabs Scribe v2. The trusted passage text is Quranpedia Hafs (`GET /mushafs/1/{surah}`, field `text`). Quran Foundation remains the Qur'an Library source. Licence notes are in [../baseline/third-party-rights.md](../baseline/third-party-rights.md).

## Evidence

- [../challenge/validation.md](../challenge/validation.md)
- [../challenge/contribution-log.md](../challenge/contribution-log.md)
- [demo/README.md](demo/README.md)
- Baseline documents under `docs/baseline/`
