# Third-Party Components, Rights and Licences

## 1. Purpose

This document records third-party services and components that were already part of the pre-challenge e-Tasmi system. It also identifies rights and licence information that must be documented before a public challenge submission.

Licence names, grant terms, and permission records are stated only where they have been confirmed. Where they have not, this document says “To be verified” or “Not yet documented”. No credentials, API keys, account identifiers, or other secrets are recorded here.

## 2. Third-Party Services

Roles below are taken from the existing system audit and the completed baseline documents. The licence column is not filled from memory.

| Service / Component | Existing Use | Pre-Challenge Status | Licence / Terms | Evidence / Source |
|---|---|---|---|---|
| OpenAI | Instructor recitation transcription and evaluation; separate student assistant, voice, and translation-fallback features when configured | Pre-existing | To be verified | Existing system audit, section 10; [existing-ai-capabilities.md](existing-ai-capabilities.md) |
| Quran Foundation | Qur'an Library chapters, verses, and related reference resources when configured | Pre-existing | To be verified | Existing system audit, section 11; [existing-system.md](existing-system.md) |
| Cloudinary | One storage path for recitation audio and some other uploads. Local `/uploads` storage is the other path recorded by the audit | Pre-existing | To be verified | Existing system audit, recitation pipeline |
| Zoom | Optional live-session meetings and embedded join | Pre-existing | To be verified | Existing system audit, live-session rows |
| MySQL | Relational database for the application | Pre-existing. Image tag `mysql:8.4` in Compose | To be verified | [technical-environment.md](technical-environment.md) |
| Apache Tomcat | Web and application server | Pre-existing. Image `tomcat:9.0-jdk17-temurin` | To be verified | [technical-environment.md](technical-environment.md) |
| Docker / Docker Compose | Local multi-container environment | Pre-existing | To be verified | Dockerfile and `docker-compose.yml` |
| phpMyAdmin | Local database inspection UI in Compose | Pre-existing. Image tag `phpmyadmin:5-apache` | To be verified | Compose file; [technical-environment.md](technical-environment.md) |

The audit records that API terms for Quran Foundation, the Qur'an CDN, OpenAI, Zoom, and Cloudinary are not stored in the repository, and that a human check is required before public release. No root `LICENSE` file was found.

## 3. OpenAI

OpenAI was already used by the pre-challenge system for instructor recitation transcription and evaluation. The audit also records other student AI features that call OpenAI when configured: the Qur'an assistant chat, the voice assistant, and the translation fallback. Those student features are separate from the recitation evaluation workflow.

API credentials are not reproduced here.

Licence/terms: To be verified against the applicable OpenAI terms for the challenge/public demonstration.

## 4. Quran Foundation

The Qur'an Library integration already existed before the challenge. The audit records Quran Foundation as the external source for chapters, verses, and related reference resources, used when the integration is configured. Reciter audio in the library is recorded as coming from a Qur'an CDN. That library role is pre-challenge functionality.

The baseline recitation AI comparison did not use a Quran Foundation verse key as its authoritative reference. That comparison used the session's free-text `quran_portion`.

Licence/terms: To be verified against the applicable Quran Foundation API/content terms.

The Qur'an CDN terms are also not stored in the repository. CDN terms: To be verified.

## 5. Cloudinary

Cloudinary is a pre-existing component. The audit records it as one storage path for submitted recitation audio, and for some profile, receipt, and other uploads. When Cloudinary is not used, the audit records local upload storage instead. Cloudinary is not required for every feature.

Account details and credentials are not recorded here.

Licence/terms: To be verified.

## 6. Zoom

Zoom's existing role is optional live-session functionality: creating meetings when configured, and embedded or linked join for a tasmi session. The audit records that a session can use a manual meeting link or a non-live mode instead.

Zoom is not required for the core recitation-analysis challenge flow unless later implementation changes that. No such change is recorded in this baseline document.

Licence/terms: To be verified.

## 7. Open-Source Software

These components are in current use as recorded in [technical-environment.md](technical-environment.md) and the Compose file.

| Component | Current use |
|---|---|
| MySQL | Database server for the `etasmi` database |
| Apache Tomcat | Server for the JSP and servlet application |
| Docker | Container runtime for the local environment |
| Docker Compose | Definition of the local multi-container environment |
| phpMyAdmin | Browser UI for inspecting the local database |

Licence/terms: To be verified and recorded from the applicable official project licence.

The audit also records third-party Java libraries under `e-Tasmi/e-Tasmi/web/WEB-INF/lib/`, including a MySQL connector, and states that each has its own licence and that human review is still needed. Those library licences are not listed here. Status: To be verified.

## 8. Pre-Existing Project Ownership

The e-Tasmi application is pre-existing project work. It is the system captured by the baseline source snapshot, not work created as a challenge-period feature.

The participant must have the necessary rights and permissions to submit and publicly publish the code used in the challenge repository. That requirement is stated here as a submission condition. It is not a record that those rights have already been confirmed.

University, employer, and other third-party ownership questions must be verified separately where they apply. The existing system audit did not find a university or final-year-project ownership statement as a licence header in the application source, and it did not find a root `LICENSE` file. That absence is not a finding that no such claim exists.

| Item | Status |
|---|---|
| Who owns the pre-existing e-Tasmi code | To be verified |
| Permission to submit and publicly publish this repository | To be verified |
| University, employer, sponsor, or other claim | To be verified. Not yet documented |

No ownership agreement is stated in this document.

## 9. Third-Party Assets

Each asset's licence and source should be verified before final submission. This section does not assign ownership.

| Asset type | Status |
|---|---|
| Images | To be verified. Not yet documented as an inventory |
| Audio | To be verified. Not yet documented as an inventory. Submitted recitation audio is user content stored by the application; its rights are separate from vendor storage terms |
| Video | To be verified. Not yet documented as an inventory |
| Fonts | To be verified. Not yet documented as an inventory |
| Icons | To be verified. Not yet documented as an inventory |
| JavaScript and CSS libraries | To be verified. The audit records application JavaScript plus Chart.js, Lottie, and the Zoom Meeting SDK loaded for the live-session page. Licences for those libraries are not recorded here |

## 10. Evidence to Collect Before Final Submission

None of the following is marked complete.

- [ ] Official project licence pages
- [ ] API terms
- [ ] Content-use terms
- [ ] Attribution requirements
- [ ] Repository licences
- [ ] Ownership and permission records
- [ ] Asset sources
- [ ] Any university or employer permission required

## 11. Baseline Boundary

This document records third-party components and rights considerations associated with the pre-challenge system. It does not claim that any new challenge-period component has already been added.
