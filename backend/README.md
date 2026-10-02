# Academic Signal API

The backend accepts bounded context windows from explicitly enabled chats and
returns validated, structured academic events. The request may include a
bounded list of known events so the model can return CREATE, UPDATE, or
NO_CHANGE while preserving stable event keys. The backend does not persist
request bodies or event state; Android remains the source of truth.

## Local setup

```powershell
python -m venv .venv
.venv\Scripts\pip install -e ".[test]"
Copy-Item .env.example .env
.venv\Scripts\uvicorn app.main:app --reload
```

Set a random `DEVICE_TOKEN` of at least 32 characters in `.env`, then enter the
same token and the deployed HTTPS base URL in the Android app.

`ANALYSIS_PROVIDER=heuristic` runs without an external API. To use Gemini, set
`ANALYSIS_PROVIDER=gemini`, `GEMINI_API_KEY`, and `GEMINI_MODEL`. To use OpenAI,
set `ANALYSIS_PROVIDER=openai`, `OPENAI_API_KEY`, and `OPENAI_MODEL`. Both hosted
providers return JSON that is strictly validated against the backend schema.
OpenAI additionally constrains output with its structured-output API; Gemini
uses JSON mode with the schema in trusted system instructions for compatibility.
The OpenAI request uses the Responses API with `store=false`.
`store=false` disables application-state storage by OpenAI, but it does not itself
override any provider abuse-monitoring retention that applies to your account.

For Gemini, create an API key in Google AI Studio and keep it only in the backend
`.env`. The Gemini free tier has limited quota and Google states that free-tier
prompts and responses may be used to improve its products, so review that policy
before sending chat context. Paid-tier handling differs.

Deploy behind HTTPS. The Android release intentionally rejects cleartext HTTP.

The OpenAI key is created in the OpenAI API Platform, not in Gmail or Google
account settings. It belongs only in the backend `.env`; never paste it into the
Android app's device-token field. API usage and ChatGPT subscriptions are billed
separately, so configure API billing/limits for the project that owns the key.

After changing provider settings, fully restart Uvicorn. The Android application
continues to use the same backend URL and device token; changing AI providers
does not require rebuilding or reinstalling the APK.

The structured response includes category, course, title/summary, start and due
timestamps, location, details/syllabus, confirmation state, confidence,
urgency, notification eligibility, change summary, and evidence message IDs.
Questions and speculation are retained as events but cannot request immediate
alerts.
