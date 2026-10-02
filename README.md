# Notification Collector

Notification Collector is an Android academic helper for Messenger and WhatsApp
chats. It saves notification previews on your phone, removes repeated copies,
and labels messages that look relevant to coursework. With an optional backend,
it can organize those messages into academic events and alert you to important
updates.

## What you can do

- **Review chat messages in one place.** The Inbox shows captured messages,
  their source chat, and an on-device academic relevance label. It works without
  an internet connection or backend.
- **Track course information.** Optional analysis can identify tests, exams,
  deadlines, labs, class changes, shared resources, course questions, and
  programming problems. It can include dates, locations, and details when the
  messages provide them.
- **See the latest information.** The Events screen keeps the current version
  of an item when later messages confirm or correct it. Activity records when
  an event was created or materially updated. You can archive an event to hide
  it from the active Events list.
- **Choose which chats get analyzed.** Add courses and aliases, assign them to
  discovered chats, and enable only the chats you want to send to your backend.
- **Opt in to alerts.** Receive eligible, high-confidence updates for confirmed
  or corrected events, plus an optional daily summary of confirmed events in
  the next seven days. The summary is scheduled around 8:00 AM local time.

## Getting started

1. Install and open the Android app, then grant **notification access** during
   onboarding. The app needs a Messenger or WhatsApp notification preview before
   it can capture a message; it cannot read older chats.
2. In **Settings**, choose which apps to collect from. Messenger collection is
   on by default and WhatsApp collection is off. You can pause collection at
   any time.
3. After a message notification arrives, open **Inbox** to see its local label
   and **Chats** to see the discovered conversation. Add your subjects in
   **Courses** and assign a subject to a chat if useful.
4. To create structured **Events**, configure your own HTTPS backend and its
   device token in **Settings**, save the configuration, then turn on cloud
   analysis. Enable the desired conversation in **Chats** and tap **Analyze
   pending messages now**, or wait for automatic analysis. See
   [backend setup](backend/README.md) for server instructions.
5. To receive this app's alerts, allow Android notifications and turn on the
   immediate alerts or daily digest switches in **Settings**.

| Screen | What it shows |
| --- | --- |
| **Events** | Current academic items, including dates, details, confidence, and source chat when available. |
| **Activity** | A history of new events and meaningful changes to them. |
| **Inbox** | Recent captured messages and their local relevance labels, including messages labeled irrelevant. |
| **Chats** | Discovered conversations; enable a chat here to allow cloud analysis and assign a course. |
| **Courses** | Course codes, names, sections, and optional aliases. |
| **Raw** | Original notification snapshots for troubleshooting capture and parsing. |
| **Settings** | Collection, retention, backend, alert, and delete-data controls. |

**What the app does not do:** It does not silence, cancel, or remove Messenger
or WhatsApp notifications. It builds a separate view from the previews Android
delivers. Enabling a chat in **Chats** controls cloud analysis, not local
collection. The Inbox may therefore contain ordinary chat messages; the local
label is a first-pass filter, not a guarantee of importance.

Without a backend, Inbox classification still works, but Events and Activity
will not receive new structured results. The backend's default `heuristic`
provider is useful for a basic test; it does not extract dates or reach the
app's immediate-alert confidence threshold. An AI provider can produce richer
results, but its interpretations should be checked against the original chat.

## Current pipeline

```text
Android notification -> normalized/deduplicated message
                     -> on-device academic pre-filter
                     -> enabled chat + explicit cloud consent
                     -> HTTPS FastAPI backend
                     -> structured extraction + known-event reconciliation
                     -> Events + Activity screens
                     -> opt-in immediate alert / daily digest
```

- Detects CT/exam, deadline, lab, class, resource, course-question, and
  programming-problem language, including selected Bangla/Banglish terms.
- Sends up to 12 recent messages from the same enabled conversation as context
  when a locally relevant message triggers analysis.
- Lets the user enable individual chats and assign courses/aliases.
- Extracts schedule/deadline, room, details/syllabus, confirmation state,
  evidence IDs, confidence, urgency, and notification decisions.
- Maintains stable events using CREATE, UPDATE, and NO_CHANGE decisions;
  every material change receives a durable revision record.
- Shows current event memory separately from its revision history.
- Supports opt-in high-confidence alerts and an 8:00 AM local seven-day digest.
- Uses WorkManager for network-constrained immediate and periodic analysis,
  with visible completion, no-pending-message, authentication, and retry status.
- Preserves the original prototype data through Room migrations 1→2→3→4.

## Privacy and security

- Cloud analysis is off by default. A locally relevant trigger can upload a
  context window of up to 12 messages from an individually enabled chat, and
  only after the explicit consent switch is enabled.
- Up to 30 locally stored event summaries from that chat may also be sent so
  the backend can identify confirmations, corrections, and duplicate updates.
- Raw notification snapshots are never included in analysis requests.
- The backend URL must use HTTPS; Android cleartext traffic is disabled.
- The device bearer token is encrypted with Android Keystore. The AI provider
  key belongs only in the backend environment and is never placed in the APK.
- Neither app nor backend logs message bodies, and the backend does not persist
  request bodies. An external AI provider may have its own data-retention rules.
- The backend supports heuristic, OpenAI, and Gemini analysis. Gemini free-tier
  prompts and responses may be used by Google to improve its products.
- Android backup/device-transfer backup is disabled.

Notification-listener access is powerful: Android can deliver notifications
from other apps before this app's package filter rejects them. Install only an
APK built from source you trust.

## Build and test

```powershell
gradlew.bat testDebugUnitTest lintDebug assembleDebug
cd backend
.venv\Scripts\python.exe -m pytest -q
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Version 0.8.0
targets Android 15/API 35 and supports Android 8/API 26 or newer. Install it over
the previous debug build—do not uninstall first if you want to keep captures.

Backend setup and deployment configuration are documented in
[`backend/README.md`](backend/README.md). A deployed HTTPS backend and matching
device token are required before enabling cloud analysis on the phone.
