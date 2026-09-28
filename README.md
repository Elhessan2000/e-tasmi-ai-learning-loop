# 🕌 e-Tasmi AI Learning Loop

<p align="center">
  <img
    src="documentation/assets/e-tasmi-ai-loop.gif"
    width="100%"
    alt="e-Tasmi AI Learning Loop"
  />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/AI%20CHALLENGE-IN%20SERVICE%20OF%20ISLAMIC%20CONTENT%202026-2563EB?style=for-the-badge&logo=openai&logoColor=white" alt="AI Challenge in Service of Islamic Content 2026">
  <img src="https://img.shields.io/badge/TRACK%2003-INTERACTIVE%20EXPERIENCES-0F766E?style=for-the-badge" alt="Track 03">
</p>

<h2 align="center">
  From AI-Assisted Recitation Analysis<br>
  to Instructor-Verified Learning
</h2>

<p align="center">
  <em>Turning verified findings into the learner's next practice step.</em>
</p>

<p align="center">
  <a href="https://e-tasmi.com">
    <img src="https://img.shields.io/badge/🌐%20LIVE%20SYSTEM-e--tasmi.com-16A34A?style=for-the-badge&logo=googlechrome&logoColor=white" alt="Live System">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-F97316?style=flat-square&logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/MySQL-8.4-2563EB?style=flat-square&logo=mysql&logoColor=white" alt="MySQL 8.4">
  <img src="https://img.shields.io/badge/Tomcat-9-F97316?style=flat-square&logo=apachetomcat&logoColor=white" alt="Tomcat 9">
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker Compose">
</p>

<p align="center">
  <a href="#-what-is-e-tasmi">What is e-Tasmi</a>
  •
  <a href="#-the-learning-loop">Learning Loop</a>
  •
  <a href="#-ai--trust">AI & Trust</a>
  •
  <a href="#-technology-stack">Technology</a>
  •
  <a href="#-project-status">Status</a>
</p>

---

## 🏆 AI Challenge in Service of Islamic Content 2026

### Track 03 — Interactive Experiences & Learning Journeys for Islam

**e-Tasmi AI Learning Loop** is an e-Tasmi project developed for the **AI Challenge in Service of Islamic Content 2026**.

The project focuses on transforming AI-assisted Qur'an recitation analysis into an **instructor-verified learning experience**.

> **AI analyzes. Instructors verify. Learners improve.**

---

## 🌐 Live Product

<p align="center">

<a href="https://e-tasmi.com">
  <img
    src="https://img.shields.io/badge/OPEN%20e--TASMI-🌐%20LIVE%20SYSTEM-16A34A?style=for-the-badge&logo=googlechrome&logoColor=white"
    alt="Open e-Tasmi"
  />
</a>

</p>

<p align="center">
  <strong>https://e-tasmi.com</strong>
</p>

---

## 🕌 What is e-Tasmi?

**e-Tasmi** is a web-based Qur'an recitation learning and assessment platform designed to support structured interaction between learners and instructors.

Students can participate in recitation activities, submit their recitations, receive instructor evaluations, review feedback, and continue their learning journey.

### 👥 Platform Roles

| Role | Responsibility |
|:---:|---|
| 👨‍🎓 **Student** | Participate in recitation sessions, submit recitations, and view results and feedback |
| 👨‍🏫 **Instructor** | Manage recitation activities, review submissions, evaluate students, and provide feedback |
| 👨‍💼 **Administrator** | Manage users, instructors, and platform administration |

---

## 🔄 The Learning Loop

The existing e-Tasmi workflow provides the foundation:

```mermaid
flowchart LR

    S["👨‍🎓 Student<br/><br/>Select / Join Session<br/>View Qur'an Passage"]

    --> R["🎙️ Recitation<br/><br/>Record Audio<br/>Submit Recitation"]

    --> I["👨‍🏫 Instructor<br/><br/>Review Submission<br/>Assess Recitation"]

    --> E["📊 Evaluation<br/><br/>Score<br/>Feedback<br/>Progress"]

    --> S2["👨‍🎓 Student<br/><br/>View Result<br/>Continue Learning"]

    S2 -.-> S
```

### 🚀 Challenge Learning Loop

The Challenge extends this workflow with an AI-assisted verification and targeted-practice cycle:

```mermaid
flowchart LR

    A["👨‍🎓 Student<br/><br/>Submit Recitation"]

    --> B["🤖 AI Analysis<br/><br/>Transcription<br/>Structured Findings"]

    --> C["📖 Trusted Qur'an<br/><br/>Reference"]

    --> D["👨‍🏫 Instructor<br/><br/>Verify Findings"]

    --> E["🎯 Verified<br/>Learning Focus"]

    --> F["📚 Targeted<br/>Practice"]

    --> G["🎙️ Practice Again"]

    --> A
```

<p align="center">
  <strong>AI analyzes → Instructor verifies → Learner practices → Recitation improves</strong>
</p>

---

## 🧠 AI & Trust

The system is designed around a simple principle:

> **AI assists the instructor. It does not replace the instructor.**

The Qur'an reference is obtained from a trusted source, while AI-generated findings remain subject to instructor verification before becoming part of the learner's guided practice.

```mermaid
flowchart LR

    AUDIO["🎙️ Student Audio"]
    --> ASR["🗣️ Speech<br/>Recognition"]

    ASR --> TRANSCRIPT["📝 Arabic<br/>Transcript"]

    QF["📖 Trusted Qur'an<br/>Reference"]
    --> COMPARE["🔍 Recitation<br/>Comparison"]

    TRANSCRIPT --> COMPARE

    COMPARE --> AI["🤖 AI Findings"]

    AI --> HUMAN["👨‍🏫 Instructor<br/>Verification"]

    HUMAN --> VERIFIED["✅ Verified<br/>Learning Focus"]

    VERIFIED --> PRACTICE["📚 Targeted<br/>Practice"]
```

---

## ⚡ Core Principle

<table>
<tr>
<td align="center" width="25%">

### 🎙️
**RECITE**

Student submits a Qur'an recitation.

</td>

<td align="center" width="25%">

### 🤖
**ANALYZE**

AI identifies potential findings.

</td>

<td align="center" width="25%">

### 👨‍🏫
**VERIFY**

Instructor accepts, edits, or rejects findings.

</td>

<td align="center" width="25%">

### 🎯
**IMPROVE**

Verified findings guide the next practice.

</td>
</tr>
</table>

---

## 📖 Why This Matters

The goal is not simply to generate an AI score.

The goal is to create a continuous learning cycle:

**Recitation → Analysis → Verification → Learning Focus → Practice → Recitation**

---

## ✨ Project Identity

<p align="center">

**e-Tasmi AI Learning Loop**

<br>

<em>AI Challenge in Service of Islamic Content 2026</em>

<br>

Track 03 — Interactive Experiences & Learning Journeys for Islam

</p>
