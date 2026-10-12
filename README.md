# Class Chat Digest

Class Chat Digest is an Android academic helper for Messenger and WhatsApp
chats. It saves notification previews on your phone, removes repeated copies,
and labels messages that look relevant to coursework. With an optional backend,
it can organize those messages into academic events and alert you to important
updates.

This is a personal project. The app works on its own: capture, deduplication,
and the on-device relevance labels need no server or account. The AI-powered
**Events** feature needs a backend, and there is no shared public server—if you
want it, you run your own copy with your own AI API key (see
[Run your own backend](#optional-run-your-own-backend)).

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
- **Choose which chats get analyzed.** Add courses and aliases, assign one or
  more of them to each discovered chat, and enable only the chats you want to
  send to your backend. In a chat that covers several courses, each event is
  matched to the course its messages mention.
- **Opt in to alerts.** Receive eligible, high-confidence updates for confirmed
  or corrected events, plus an optional daily summary of confirmed events in
  the next seven days. The summary is scheduled around 8:00 AM local time.

## Install

1. Download the latest `app-debug.apk` from
   [GitHub Releases](https://github.com/akif67y/class-chat-digest/releases).
   The app supports Android 8 (API 26) or newer.
2. Open the APK on your phone. If Android asks, allow your browser or file
   manager to **install unknown apps**.
3. To update, install the newer APK over the existing app. Do not uninstall
   first, or your captured messages, chats, courses, and events are deleted.

> **Notification access is greyed out?** On Android 13 and newer, apps installed
> from an APK file are blocked from sensitive permissions by default. Open
> **Settings → Apps → Class Chat Digest**, tap the **⋮** menu, choose
> **Allow restricted settings**, then grant notification access again.

## Getting started

1. Open the app and grant **notification access** during
   onboarding. The app needs a Messenger or WhatsApp notification preview before
   it can capture a message; it cannot read older chats.
2. In **Settings**, choose which apps to collect from. Messenger collection is
   on by default and WhatsApp collection is off. You can pause collection at
   any time.
3. After a message notification arrives, open **Inbox** to see its local label
   and **Chats** to see the discovered conversation. Add your subjects in
   **Courses** and assign every subject a chat discusses. Assigned courses
   make messages that mention them more likely to be analyzed.
4. *Optional:* to create structured **Events**, set up
   [your own backend](#optional-run-your-own-backend), enter its URL and device
   token in **Settings**, save the configuration, then turn on cloud analysis.
   Enable the desired conversation in **Chats** and tap **Analyze pending
   messages now**, or wait for automatic analysis.
5. To receive this app's alerts, allow Android notifications and turn on the
   immediate alerts or daily digest switches in **Settings**.

| Screen | What it shows |
| --- | --- |
| **Events** | Current academic items, including dates, details, confidence, and source chat when available. |
| **Activity** | A history of new events and meaningful changes to them. |
| **Inbox** | Recent captured messages and their local relevance labels, including messages labeled irrelevant. |
| **Chats** | Discovered conversations; enable a chat here to allow cloud analysis and assign its courses. |
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

## Optional: run your own backend

The backend is a small FastAPI server in [`backend/`](backend). Each person who
wants Events runs their own copy, so you choose the AI provider, pay for (or use
the free tier of) your own API key, and your chats go only to your server.
Never share your device token or API key with anyone.

You need:

- **A device token**: a random secret of at least 32 characters that the app
  uses to prove it is yours. Generate one with:

  ```bash
  python -c "import secrets; print(secrets.token_urlsafe(32))"
  ```

- **An analysis provider**: `heuristic` (keyword rules, no key needed—good for
  a first test), `gemini` (a key from [Google AI Studio](https://aistudio.google.com/apikey),
  with a free tier), or `openai` (a key from the
  [OpenAI API Platform](https://platform.openai.com/api-keys), billed separately
  from ChatGPT).
- **An HTTPS URL.** The app refuses plain `http://` addresses, including your
  PC's local network address.

### Option A: host it on Render (always available)

1. Fork this repository on GitHub.
2. In [Render](https://render.com), create a **New → Web Service** and connect
   your fork.
3. Use these settings:

   | Setting | Value |
   | --- | --- |
   | Root Directory | `backend` |
   | Runtime | Python |
   | Build Command | `pip install .` |
   | Start Command | `uvicorn app.main:app --host 0.0.0.0 --port $PORT` |
   | Health Check Path | `/health` |

4. Under **Environment**, add `DEVICE_TOKEN`, `ANALYSIS_PROVIDER`, and the key
   and model for your provider (`GEMINI_API_KEY` and `GEMINI_MODEL`, or
   `OPENAI_API_KEY` and `OPENAI_MODEL`). [`backend/.env.example`](backend/.env.example)
   lists every variable. The backend needs Python 3.11 or newer; if the build
   uses an older version, also set `PYTHON_VERSION` (for example `3.12.8`).
5. Deploy, then open `https://<your-service>.onrender.com/health`. It should
   return `{"status":"ok"}`.

Render's free instances sleep when idle, so the first analysis after a pause can
take up to a minute; the app retries automatically. With auto-deploy on, Render
redeploys whenever you push to your fork.

### Option B: run it on your own computer

1. Install Python 3.11 or newer and set up the backend as described in
   [`backend/README.md`](backend/README.md#local-setup). Put your device token
   and provider settings in `backend/.env`.
2. Start it with `uvicorn app.main:app --port 8000` from the `backend` folder.
3. Give it an HTTPS address with a tunnel, for example
   [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/do-more-with-tunnels/trycloudflare/):

   ```bash
   cloudflared tunnel --url http://localhost:8000
   ```

   Use the printed `https://….trycloudflare.com` address in the app.

The computer must stay on and connected for analysis to work, and a quick tunnel
gets a new address every time it starts, so you must update the URL in the app
after each restart.

### Connect the app

1. In the app's **Settings**, enter the backend's base URL (for example
   `https://<your-service>.onrender.com`) and your device token, then tap
   **Save cloud configuration**.
2. Turn on **I consent to send filtered chat context**.
3. In **Chats**, enable the conversations you want analyzed and assign their
   courses, then tap **Analyze pending messages now** in **Settings**.

The status line under the button explains failures: a token mismatch means the
app and `DEVICE_TOKEN` differ, and a wrong URL returns "API route is missing".
Changing the AI provider only needs a backend restart or redeploy, not a new APK.

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
- Lets the user enable individual chats and assign several courses/aliases to
  each; the backend attributes every event to one of the chat's courses, or none
  when the messages do not say which.
- Extracts schedule/deadline, room, details/syllabus, confirmation state,
  evidence IDs, confidence, urgency, and notification decisions.
- Maintains stable events using CREATE, UPDATE, and NO_CHANGE decisions;
  every material change receives a durable revision record.
- Shows current event memory separately from its revision history.
- Supports opt-in high-confidence alerts and an 8:00 AM local seven-day digest.
- Uses WorkManager for network-constrained immediate and periodic analysis,
  with visible completion, no-pending-message, authentication, and retry status.
- Preserves the original prototype data through Room migrations 1→2→3→4→5.

## Privacy and security

- Without a backend, nothing leaves the phone. With one, messages go only to
  the backend you configured and the AI provider it uses.
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

## Build from source

```powershell
gradlew.bat testDebugUnitTest lintDebug assembleDebug
cd backend
.venv\Scripts\python.exe -m pytest -q
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Version 0.8.0
targets Android 15/API 35 and supports Android 8/API 26 or newer. An APK you
build yourself is signed with your own debug key, so Android will not install it
over a release APK (or the reverse); uninstall first, which deletes local data.

Provider details for the backend are documented in
[`backend/README.md`](backend/README.md).
