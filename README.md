# Notification Collector

An Android app that captures selected Messenger and WhatsApp notification
previews and turns computer-science course chat into structured academic
events. Milestones 1–7 cover collection, normalization, local relevance
filtering, bounded context, structured extraction, event memory, and alerts.

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
- Groups up to 12 relevant messages from the same conversation for context.
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
