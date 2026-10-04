# Pre-Challenge Baseline Documentation

## Purpose

This directory records the documented state of the existing e-Tasmi system before the official challenge development period.

The baseline is supported by:

- the Git source snapshot
- the baseline declaration
- existing system documentation
- existing AI documentation
- screenshots
- architecture documentation
- technical environment documentation
- rights and licence documentation
- data and privacy documentation

Work from the official challenge development period is documented separately in [../challenge/](../challenge/README.md). The current architecture is in [../architecture/learning-loop.md](../architecture/learning-loop.md).

## Baseline Source Snapshot

| Item | Value |
|---|---|
| Commit | `a358719` |
| Tag | `baseline-pre-ai-challenge-2026` |
| Repository | `https://github.com/Elhessan2000/e-tasmi-ai-learning-loop` |

The Git commit and tag identify the pre-challenge source snapshot. Details are in [git-baseline.md](git-baseline.md).

## Documents

| Document | Purpose |
|---|---|
| [baseline-declaration.md](baseline-declaration.md) | Formal declaration of the pre-challenge starting state |
| [existing-system.md](existing-system.md) | Existing e-Tasmi functionality and workflows |
| [existing-ai-capabilities.md](existing-ai-capabilities.md) | AI capabilities already present before the challenge |
| [existing-vs-challenge.md](existing-vs-challenge.md) | Boundary between existing functionality and planned challenge contribution |
| [technical-environment.md](technical-environment.md) | Pre-challenge technology and environment information |
| [third-party-rights.md](third-party-rights.md) | Third-party services, components, licences, and permissions |
| [data-privacy.md](data-privacy.md) | Data, privacy, secrets, and public repository handling |
| [git-baseline.md](git-baseline.md) | Git source baseline and history boundary |

## Evidence

### Screenshots

[screenshots/](screenshots/README.md)

These images were recaptured with synthetic demo accounts. Earlier screenshots that showed a real account were removed.

A packaged Word/PDF export of this baseline was also removed. It embedded those earlier screenshots, including a personal email address and personal display names. The Markdown declaration in this folder is the public record.

### Supporting Evidence

[evidence/](evidence/README.md)

This folder is reserved for additional baseline evidence.

## Architecture

[architecture/](architecture/README.md)

This folder contains the pre-challenge system architecture and workflow diagrams.

## Baseline Scope

This directory documents the pre-challenge state only. Challenge-period work is documented under [../challenge/](../challenge/README.md).

## Important Boundary

Pre-existing work is not attributed to the challenge period.

Later local Docker isolation is not part of the baseline source commit.

Challenge-period implementation will be associated with later commits and evidence.
