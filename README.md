# 🕌 e-Tasmi AI Learning Loop

<p align="center">
  <img src="https://img.shields.io/badge/AI%20Challenge-2026-2563EB?style=for-the-badge&logo=openai&logoColor=white" alt="AI Challenge 2026">
  <img src="https://img.shields.io/badge/Track-03-0F766E?style=for-the-badge" alt="Track 03">
  <img src="https://img.shields.io/badge/Java-17-F97316?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/MySQL-8.4-2563EB?style=for-the-badge&logo=mysql&logoColor=white" alt="MySQL">
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker">
</p>

<p align="center">
  <a href="https://e-tasmi.com">
    <img src="https://img.shields.io/badge/🌐%20LIVE%20WEBSITE-e--tasmi.com-16A34A?style=for-the-badge" alt="Live Website">
  </a>
</p>

<p align="center">
  <strong>From AI-Assisted Recitation Analysis to Instructor-Verified Learning</strong>
</p>

<p align="center">
  Turning verified findings into the learner's next practice step.
</p>

<p align="center">
  <a href="https://e-tasmi.com">Live Website</a>
  •
  <a href="#-the-learning-loop">Learning Loop</a>
  •
  <a href="#-ai--trust">AI & Trust</a>
  •
  <a href="#-technology-stack">Technology</a>
  •
  <a href="#-project-status">Project Status</a>
</p>

---

## 🌐 Live Product

### Experience e-Tasmi

<p align="center">
  <a href="https://e-tasmi.com">
    <img src="https://img.shields.io/badge/OPEN%20e--TASMI-LIVE%20SYSTEM-16A34A?style=for-the-badge&logo=googlechrome&logoColor=white" alt="Open e-Tasmi">
  </a>
</p>

**Live:** https://e-tasmi.com

e-Tasmi is a web-based Qur'an recitation learning platform designed to connect learners and instructors through structured recitation sessions, assessment, feedback, and progress tracking.

> **The existing e-Tasmi platform is the foundation.  
> The AI Learning Loop is the challenge contribution built on top of it.**

---

# 🕌 What is e-Tasmi?

**e-Tasmi** is a web-based Qur'an recitation learning and assessment platform designed to support structured interaction between learners and instructors.

The system provides a digital environment where students can participate in recitation activities, submit their recitations, receive instructor evaluations, review feedback, and continue their learning journey.

The platform is organized around three primary roles:

| Role | Main Responsibility |
|---|---|
| 👨‍🎓 Student | Participate in recitation sessions, submit recitations, view results and feedback |
| 👨‍🏫 Instructor | Manage recitation activities, review submissions, evaluate students and provide feedback |
| 👨‍💼 Administrator | Manage the platform, users, instructors and administrative operations |

---

## 🔄 e-Tasmi Learning Workflow

```mermaid
flowchart LR
    S["👨‍🎓 Student<br/><br/>Select / Join Session<br/>View Qur'an Passage"] 
    --> R["🎙️ Recitation<br/><br/>Record Audio<br/>Submit Recitation"]

    R --> I["👨‍🏫 Instructor<br/><br/>Review Submission<br/>Assess Recitation"]

    I --> E["📊 Evaluation<br/><br/>Score<br/>Feedback<br/>Progress"]

    E --> S2["👨‍🎓 Student<br/><br/>View Result<br/>Read Feedback<br/>Continue Learning"]

    S2 -. "Continue Learning" .-> S
```
