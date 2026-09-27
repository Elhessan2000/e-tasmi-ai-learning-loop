# Existing System at Pre-Challenge Baseline

This document describes the e-Tasmi platform as it existed at the pre-challenge baseline. It is based on the existing system audit in the repository and on the baseline screenshots. It does not describe challenge-period features as present.

Screenshot files are in [screenshots/](screenshots/README.md).

## 1. System Overview

e-Tasmi is an existing web-based Qur'an recitation learning platform. A student enrols in a tasmi session, submits recitation audio, and later views the instructor's score and feedback. An instructor manages sessions, plays submitted audio, may run an on-screen AI analysis, and saves the official score and feedback. An administrator manages users and approves instructors.

Access is session-based. The audit records no separate course entity. The live path is server-rendered JSP with Java servlets.

## 2. User Roles

The audit records three roles. No other role is defined on the user role enumeration.

**Student.** A student signs in with email and password, can self-register, and uses a student dashboard. The audit records that a student can browse and enrol in sessions, submit a recitation, view their own evaluations and progress, and open the Qur'an Library. A student cannot create sessions, evaluate another person's recitation, or start the instructor AI analysis.

**Instructor.** An instructor uses the same login. Self-registration creates an instructor account that stays pending until an administrator approves it. The audit records that an approved instructor can create and manage their own tasmi sessions, list enrolled recitations, play submitted audio, optionally run AI analysis, and save a score and feedback. An instructor cannot approve their own account.

**Admin.** An administrator signs in through the same login and is sent to the admin dashboard. Public registration does not create this role. The audit records that an administrator can manage users, approve or reject instructors, monitor payments, and view reports and audit logs. The audit records no administrator servlet for official recitation evaluation.

## 3. Existing Student Workflow

The verified pre-challenge student path is:

Student → session enrolment → recitation submission → uploaded or recorded audio → instructor evaluation → student views result and feedback.

The audit records this as a working path:

1. The student browses tasmi sessions and enrols. Enrolment creates an enrollment row for that student and session.
2. The student submits recitation audio from the recitation page, either as an upload or as a browser recording. The file is stored and linked to that enrollment.
3. The instructor evaluates that submission.
4. The student opens the recitations page again and sees the stored evaluation, including the result and feedback.
5. Progress is available on the student progress page. The audit records that progress is updated when the instructor saves the evaluation.

Payment, notifications, and live-session join exist beside this path. They are not required steps in every recitation. The audit records payment when a session fee is greater than zero, and live join as a separate partial feature.

## 4. Existing Instructor Workflow

The verified pre-challenge instructor path for a submitted recitation is:

Instructor → view recitation and evaluation → access submitted audio → optionally trigger existing AI analysis → review the analysis result → enter and save score and feedback.

The existing AI analysis is instructor-triggered. The audit records an optional control, “Analyze with eTasmi AI”, on the instructor evaluations page. The student cannot start that analysis.

After analysis, the on-screen result is available for review. The audit records that the score fields are not filled in automatically. The instructor types the score and feedback and saves them. That saved row is the official evaluation. The student then sees the result, and progress is updated.

## 5. Existing Recitation System

Terminology below matches the audit and the baseline schema.

**Tasmi sessions.** An instructor creates a `tasmi_session`. The audit records portion, fee, mode, and optional live-meeting fields among the session data. Students enrol in a session rather than in a course.

**Quran portion / session reference.** The session stores `quran_portion`, a free-text portion string of limited length. The audit identifies this instructor-entered text as the expected passage for the existing AI comparison. It is not an ayah identifier.

**Recitation submissions.** The live submission is a `recitation` row tied to an enrollment. It stores the audio path and the submission date. Audio is saved through Cloudinary or under local recitation uploads. The student can play back their own submission. A separate `recitation_submissions` table exists in the schema, and the audit records that it has no controller on the live path.

**Evaluation.** One official `evaluation` is stored per recitation. The instructor saves it from the evaluations page.

**Score.** The official score is a human-entered value from 0 to 100 on that evaluation. An AI run may show a suggested score on screen. The audit records that this suggestion does not become the official score by itself.

**Feedback.** The instructor enters feedback with the score. Both are stored on the evaluation and shown to the student.

**Progress and result.** After the evaluation is saved, the student can see the result on the recitations page. The progress record is updated from that evaluation. The student progress page reads the progress data.

## 6. Existing AI-Assisted Recitation Analysis

The pre-challenge instructor analysis follows this pipeline:

Audio → audio retrieval → transcription → AI evaluation → analysis result → instructor review.

The instructor starts it from the evaluations page. The service reads the submitted media, requests a transcription, then requests an AI evaluation. The audit records an on-screen report with a transcript, word differences, and a suggested score. If the model call fails, the same service can fall back to a local Arabic token comparison. The result is displayed for the instructor. It is not written as the official evaluation.

The comparison text is the session's `quran_portion` free-text reference. The existing recitation workflow does not use a Qur'an Library verse key as its authoritative comparison reference.

The audit records that this analysis is not stored as a durable structured record. Refreshing the page does not reload a saved AI report, and saving the official evaluation clears the on-screen analysis state.

This instructor analysis is separate from other pre-challenge AI features the audit also records for students, such as the Qur'an assistant chat, voice assistant, and translation fallback. Those features are not the recitation evaluation path.

## 7. Existing Qur'an Library

The Qur'an Library already existed before the challenge. The audit places it on the student Qur'an Library route and records Quran Foundation as the external source for chapters, verses, and related resources, with reciter audio supplied from a Qur'an CDN. It is partial in the audit because it depends on configured access to those services. There is no local mushaf database in the application.

The baseline screenshots show this library in the student interface:

- [quran-library.png](screenshots/quran-library.png): Existing Qur'an Library.
- [quran-reference.png](screenshots/quran-reference.png): Existing Qur'an reference/library view.

That library is distinct from the recitation AI comparison path. Library text and reciter audio are for reading and listening in the library. Recitation analysis still compares the submission with the session's free-text `quran_portion`.

## 8. Existing Interface Evidence

Files below are in [documentation/baseline/screenshots/](screenshots/README.md). The description states what each filename and the verified library screenshots support. It does not treat a filename as proof of a detail that is not part of that screen's subject.

| Screenshot | What it demonstrates |
|---|---|
| [login.png](screenshots/login.png) | Existing login interface |
| [student-dashboard.png](screenshots/student-dashboard.png) | Existing student dashboard |
| [recitation-submission.png](screenshots/recitation-submission.png) | Existing student recitation submission |
| [student-recitation-result.png](screenshots/student-recitation-result.png) | Existing student result and feedback |
| [instructor-dashboard.png](screenshots/instructor-dashboard.png) | Existing instructor dashboard |
| [instructor-evaluation.png](screenshots/instructor-evaluation.png) | Existing instructor evaluation interface |
| [existing-ai-analysis.png](screenshots/existing-ai-analysis.png) | Existing instructor AI-assisted recitation analysis |
| [quran-library.png](screenshots/quran-library.png) | Existing Qur'an Library |
| [quran-reference.png](screenshots/quran-reference.png) | Existing Qur'an reference/library view |
| [existing-recitation-workflow-student.png](screenshots/existing-recitation-workflow-student.png) | Existing student-side recitation workflow |
| [existing-recitation-workflow-instructor-completed.png](screenshots/existing-recitation-workflow-instructor-completed.png) | Existing instructor-side completed recitation workflow |

## 9. Existing Technical Components

Verified from the baseline environment and the existing system audit:

- Java 17
- Apache Tomcat 9
- MySQL 8.4
- Docker and Docker Compose
- A JSP and servlet web application
- OpenAI for the existing instructor recitation analysis, including transcription and the evaluation request described in the audit
- Quran Foundation integration for the existing Qur'an Library
- Cloudinary as one storage path for submitted recitation audio, with local upload storage as the other path recorded by the audit

No credentials or secrets are recorded here.

The isolated local inspection addresses are documented in [baseline-declaration.md](baseline-declaration.md). Those host ports were introduced after the baseline source commit so this copy could run beside the original installation. They are not production settings.

## 10. Existing Data Model

The audit identifies these tables as part of the pre-challenge recitation path. This is not a full schema listing.

| Entity | Role in the existing system |
|---|---|
| `user` | Account, including role |
| `student` | Student profile linked to a user |
| `instructor` | Instructor profile linked to a user, including approval state |
| `tasmi_session` | Instructor session, including the free-text `quran_portion` |
| `enrollment` | Link between a student and a session |
| `recitation` | Submitted audio for an enrollment |
| `evaluation` | Official score and feedback for one recitation |
| `progress` | Student progress updated from evaluation |

The audit states that there is no table for persisted AI analysis results. Challenge-specific tables are not described here.

## 11. Existing Limitations Relevant to the Challenge

These are verified limits of the pre-challenge system. They explain the boundary for later challenge work. They are not descriptions of completed challenge features.

- AI findings were not persistently stored as structured records.
- There was no per-finding instructor Accept, Edit, or Reject workflow.
- There was no Verified Learning Focus.
- There was no student-facing AI learning-focus flow.
- There was no Practice Again loop.
- There was no linkage between resubmissions.
- The Qur'an Library reference exists separately from the recitation comparison path, which uses `quran_portion`.

## 12. Baseline Boundary

This document describes the functionality present in the e-Tasmi system at the pre-challenge baseline. Features implemented during the official challenge development period will be documented separately and will not be treated as pre-existing functionality.
