# e-Tasmi AI Learning Loop

<p align="center">
  <strong>From AI-Assisted Recitation Analysis to Instructor-Verified Learning</strong>
</p>

<p align="center">
  Turning verified findings into the learner's next practice step.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/AI%20Challenge-2026-blue" alt="AI Challenge 2026">
  <img src="https://img.shields.io/badge/Track-03%20Interactive%20Learning-blue" alt="Track 03">
  <img src="https://img.shields.io/badge/Java-17-orange" alt="Java 17">
  <img src="https://img.shields.io/badge/MySQL-8.4-blue" alt="MySQL 8.4">
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED" alt="Docker">
  <img src="https://img.shields.io/badge/AI-OpenAI-black" alt="OpenAI">
</p>

---

## About the Project

**e-Tasmi AI Learning Loop** is an AI-assisted Qur'an recitation learning concept built on the existing **e-Tasmi** platform.

The project explores how artificial intelligence can support Qur'an recitation learning while maintaining a clear **human verification layer** between automated analysis and learner-facing guidance.

The central idea is simple:

> **AI analyzes. Instructors verify. Learners improve.**

Rather than treating AI output as an authoritative teaching decision, the proposed learning loop uses AI to identify possible recitation findings, allows an instructor to review those findings, and then transforms verified findings into a focused next practice step for the learner.

---

## Project Track

**AI Challenge in Service of Islamic Content — 2026**

**Track 03: Interactive Experiences & Learning Journeys for Islam**

**Project:** e-Tasmi AI Learning Loop

**Participant:** Elhessan Abdelrahman Adam

**Role:** Founder & Developer

---

# The Problem

AI can assist with speech recognition and automated recitation analysis, but identifying a possible issue is only one part of the learning process.

A useful educational system must also answer:

- What exactly did the learner recite?
- What reference passage should be used for comparison?
- Which findings are actually valid?
- Who verifies the AI's findings?
- What should the learner practise next?
- How does the learner submit another attempt?

The project therefore focuses on moving beyond a simple:

**Recitation → AI Result**

towards a structured:

**Recitation → Analysis → Verification → Learning Focus → Practice → Recitation Again**

---

# Learning Loop

The proposed learning experience is built around a continuous feedback loop:

```text
       ┌─────────────────┐
       │     STUDENT     │
       │    Recitation   │
       └────────┬────────┘
                │
                ▼
       ┌─────────────────┐
       │   AI ANALYSIS   │
       │ Transcription + │
       │    Findings     │
       └────────┬────────┘
                │
                ▼
       ┌─────────────────┐
       │   INSTRUCTOR    │
       │     REVIEW      │
       │ Accept / Edit / │
       │     Reject      │
       └────────┬────────┘
                │
                ▼
       ┌─────────────────┐
       │    VERIFIED     │
       │  LEARNING FOCUS │
       └────────┬────────┘
                │
                ▼
       ┌─────────────────┐
       │     STUDENT     │
       │   Practice Again│
       └────────┬────────┘
                │
                ▼
          Next Recitation
