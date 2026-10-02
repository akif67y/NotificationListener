import json
from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from app.config import Settings
from app.providers import GeminiProvider, provider_for
from app.schemas import AnalyzeRequest


TOKEN = "test-device-token-that-is-at-least-32-characters"


def gemini_settings(**overrides: object) -> Settings:
    values: dict[str, object] = {
        "device_token": TOKEN,
        "analysis_provider": "gemini",
        "gemini_api_key": "test-gemini-key",
        "gemini_model": "gemini-3.5-flash-lite",
        "_env_file": None,
    }
    values.update(overrides)
    return Settings(**values)  # type: ignore[arg-type]


def analyze_request() -> AnalyzeRequest:
    return AnalyzeRequest.model_validate({
        "request_id": "3ef81bed-2396-4e2f-ab0d-c18d6dcb1779",
        "timezone": "Asia/Dhaka",
        "conversation": {
            "id": 1,
            "platform": "Messenger",
            "name": "CSE 221",
            "course_code": "CSE 221",
        },
        "messages": [{
            "id": 10,
            "sender": "Rafi",
            "text": "CSE 221 CT tomorrow at 11",
            "timestamp": 1789300000000,
            "local_category": "EVENT_INFORMATION",
        }],
    })


def test_gemini_requires_key_and_model() -> None:
    with pytest.raises(ValidationError, match="GEMINI_API_KEY and GEMINI_MODEL"):
        gemini_settings(gemini_api_key=None)


def test_gemini_provider_returns_validated_payload(monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict[str, object] = {}
    model_output = {
        "signals": [{
            "event_key": None,
            "action": "CREATE",
            "category": "EVENT_INFORMATION",
            "course_code": "CSE 221",
            "title": "CSE 221 CT",
            "summary": "A CT may be held tomorrow at 11.",
            "starts_at": None,
            "due_at": None,
            "location": None,
            "details": None,
            "status": "SPECULATION",
            "confidence": 0.72,
            "urgency": "MEDIUM",
            "should_notify": False,
            "change_summary": None,
            "evidence_message_ids": [10],
        }]
    }

    def generate_content(**kwargs: object) -> SimpleNamespace:
        captured.update(kwargs)
        return SimpleNamespace(text=json.dumps(model_output))

    fake_client = SimpleNamespace(
        models=SimpleNamespace(generate_content=generate_content)
    )
    monkeypatch.setattr("app.providers.genai.Client", lambda **_: fake_client)

    provider = provider_for(gemini_settings())
    assert isinstance(provider, GeminiProvider)
    payload = provider.analyze(analyze_request())

    assert payload.signals[0].category.value == "EVENT_INFORMATION"
    assert payload.signals[0].evidence_message_ids == [10]
    assert captured["model"] == "gemini-3.5-flash-lite"
    config = captured["config"]
    assert config.response_mime_type == "application/json"  # type: ignore[union-attr]
    assert config.response_schema is None  # type: ignore[union-attr]
    assert config.response_json_schema is None  # type: ignore[union-attr]
    serialized_schema = config.system_instruction  # type: ignore[union-attr]
    assert "Return only JSON matching this schema exactly" in serialized_schema
    assert '"$defs"' not in serialized_schema
    assert '"$ref"' not in serialized_schema
    assert "additionalProperties" not in serialized_schema
    assert "minLength" not in serialized_schema
    assert "maxLength" not in serialized_schema
    assert config.automatic_function_calling.disable is True  # type: ignore[union-attr]


def test_gemini_provider_rejects_invalid_model_output(monkeypatch: pytest.MonkeyPatch) -> None:
    fake_client = SimpleNamespace(
        models=SimpleNamespace(
            generate_content=lambda **_: SimpleNamespace(text='{"signals":[{"category":"INVALID"}]}')
        )
    )
    monkeypatch.setattr("app.providers.genai.Client", lambda **_: fake_client)

    provider = provider_for(gemini_settings())
    with pytest.raises(ValidationError):
        provider.analyze(analyze_request())


def test_gemini_provider_drops_malformed_optional_timestamp(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    model_output = {
        "signals": [{
            "event_key": None,
            "action": "CREATE",
            "category": "EVENT_INFORMATION",
            "course_code": "CSE 221",
            "title": "CSE 221 CT",
            "summary": "A CT may be held tomorrow at 11.",
            "starts_at": "1791032400+06:00",
            "due_at": None,
            "location": None,
            "details": None,
            "status": "SPECULATION",
            "confidence": 0.72,
            "urgency": "MEDIUM",
            "should_notify": False,
            "change_summary": None,
            "evidence_message_ids": [10],
        }]
    }
    fake_client = SimpleNamespace(
        models=SimpleNamespace(
            generate_content=lambda **_: SimpleNamespace(text=json.dumps(model_output))
        )
    )
    monkeypatch.setattr("app.providers.genai.Client", lambda **_: fake_client)

    payload = provider_for(gemini_settings()).analyze(analyze_request())
    assert payload.signals[0].starts_at is None
