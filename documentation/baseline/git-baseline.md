# Pre-Challenge Git Baseline

## 1. Purpose

This document identifies the Git source snapshot used as the pre-challenge baseline for e-Tasmi. It records that snapshot. It does not change Git history.

## 2. Repository

Repository: https://github.com/Elhessan2000/e-tasmi-ai-learning-loop

Branch: `master`

## 3. Baseline Commit

| Item | Value |
|---|---|
| Short commit | `a358719` |
| Full commit | `a35871918907d7a403c9469537c25f940009b340` |
| Commit message | `chore: establish pre-challenge baseline` |

## 4. Baseline Tag

Tag: `baseline-pre-ai-challenge-2026`

The tag points to commit `a358719`.

## 5. What the Baseline Represents

This commit represents the pre-challenge source snapshot of the existing e-Tasmi system.

The baseline source includes the pre-existing application, the existing database schema and setup files, and the other tracked project files included in that snapshot.

Later local inspection configuration is not part of this source snapshot. Host-port and container isolation used to run this copy beside the original installation was made after commit `a358719`.

## 6. Later Inspection Environment

### Later Local Inspection Environment — Not Part of Baseline Commit

| Item | Value |
|---|---|
| Application | http://localhost:8084 |
| phpMyAdmin | http://localhost:8083 |
| MySQL host port | 3317 |

These changes were made after the baseline commit only to isolate the challenge copy from the original local e-Tasmi installation.

These later local Docker/host-port changes are environment isolation changes and are not part of the pre-challenge source snapshot identified by commit a358719.

## 7. Baseline Evidence

These documents describe the baseline state:

- [baseline-declaration.md](baseline-declaration.md)
- [existing-system.md](existing-system.md)
- [existing-ai-capabilities.md](existing-ai-capabilities.md)
- [existing-vs-challenge.md](existing-vs-challenge.md)
- [technical-environment.md](technical-environment.md)
- [third-party-rights.md](third-party-rights.md)
- [data-privacy.md](data-privacy.md)
- [screenshots/](screenshots/README.md)

Together they document the baseline state. They do not replace the Git commit and tag.

## 8. Challenge-Period Git Evidence

During the official challenge development period, new work will be associated with subsequent Git commits. Those commits will be documented in [../challenge/contribution-log.md](../challenge/contribution-log.md).

No challenge commit is created by this document. No later commit hash is recorded here.

## 9. Integrity / History Boundary

The baseline commit and tag are preserved as the reference point for the pre-challenge source. Challenge-period work will be represented by later commits rather than by rewriting, amending, or moving the baseline history.

## 10. Verification Record

Checked against the local repository. No timestamp is recorded. The baseline snapshot and the current working tree are different states.

**Baseline snapshot**

| Check | Result |
|---|---|
| Commit | `a358719` |
| Tag | `baseline-pre-ai-challenge-2026`, pointing at that commit |
| Source snapshot when created | Clean |
| Baseline commit history rewritten | No |
| Baseline tag moved | No |

The source snapshot itself was clean at the time it was created. `HEAD` is still commit `a358719`.

**Current working tree**

The working tree today is not clean. It contains:

- later uncommitted Docker and environment isolation changes
- untracked documentation files

Those files are not part of commit `a358719`.

## 11. Boundary Statement

This document identifies the pre-challenge Git source snapshot. It does not attribute later environment isolation changes or future challenge-period implementation to the baseline.
