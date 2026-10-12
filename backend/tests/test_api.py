from uuid import uuid4

from fastapi.testclient import TestClient

from app.main import app

TOKEN = "test-device-token-that-is-at-least-32-characters"
client = TestClient(app)


def request_body(text: str = "CSE 221 CT tomorrow at 11") -> dict:
    return {
        "request_id": str(uuid4()),
        "timezone": "Asia/Dhaka",
        "conversation": {"id": 1, "platform": "Messenger", "name": "CSE 221", "course_code": "CSE 221"},
        "messages": [{"id": 10, "sender": "Rafi", "text": text, "timestamp": 1789300000000, "local_category": "EVENT_INFORMATION"}],
    }


def test_requires_authentication() -> None:
    assert client.post("/v1/analyze", json=request_body()).status_code == 401


def test_returns_schema_valid_signal() -> None:
    response = client.post("/v1/analyze", json=request_body(), headers={"Authorization": f"Bearer {TOKEN}"})
    assert response.status_code == 200
    body = response.json()
    assert body["signals"][0]["category"] == "EVENT_INFORMATION"
    assert body["signals"][0]["action"] == "CREATE"
    assert body["signals"][0]["event_key"].startswith("event-")
    assert body["signals"][0]["status"] == "SPECULATION"
    assert body["signals"][0]["evidence_message_ids"] == [10]


def test_reconciles_with_known_event() -> None:
    body = request_body("Sir confirmed the CT")
    body["known_events"] = [{
        "event_key": "event-existing",
        "category": "EVENT_INFORMATION",
        "course_code": "CSE 221",
        "title": "CT",
        "summary": "CT may be tomorrow",
        "starts_at": None,
        "due_at": None,
        "location": None,
        "details": None,
        "status": "SPECULATION",
        "confidence": 0.5,
        "updated_at": 1789300000000,
    }]
    response = client.post(
        "/v1/analyze",
        json=body,
        headers={"Authorization": f"Bearer {TOKEN}"},
    )
    assert response.status_code == 200
    signal = response.json()["signals"][0]
    assert signal["event_key"] == "event-existing"
    assert signal["action"] == "UPDATE"
    assert signal["status"] == "CONFIRMED"


def multi_course_body(*texts: str) -> dict:
    body = request_body()
    body["conversation"] = {
        "id": 1,
        "platform": "Messenger",
        "name": "CSE batch 22",
        "courses": [
            {"code": "CSE 221", "title": "Algorithms", "aliases": ["algo"]},
            {"code": "CSE 220", "title": "Data Structures", "aliases": ["ds"]},
        ],
    }
    body["messages"] = [
        {"id": 10 + index, "sender": "Rafi", "text": text, "timestamp": 1789300000000 + index, "local_category": "EVENT_INFORMATION"}
        for index, text in enumerate(texts)
    ]
    return body


def test_attributes_each_event_to_the_mentioned_course() -> None:
    response = client.post(
        "/v1/analyze",
        json=multi_course_body("CSE221 CT tomorrow", "ds lab report deadline friday", "exam routine out"),
        headers={"Authorization": f"Bearer {TOKEN}"},
    )
    assert response.status_code == 200
    courses = [signal["course_code"] for signal in response.json()["signals"]]
    assert courses == ["CSE 221", "CSE 220", None]


def test_same_category_in_another_course_creates_a_new_event() -> None:
    body = multi_course_body("Algorithms CT on Sunday")
    body["known_events"] = [{
        "event_key": "event-ds-ct",
        "category": "EVENT_INFORMATION",
        "course_code": "CSE 220",
        "title": "CT",
        "summary": "DS CT next week",
        "starts_at": None,
        "due_at": None,
        "location": None,
        "details": None,
        "status": "SPECULATION",
        "confidence": 0.5,
        "updated_at": 1789300000000,
    }]
    response = client.post("/v1/analyze", json=body, headers={"Authorization": f"Bearer {TOKEN}"})
    signal = response.json()["signals"][0]
    assert signal["action"] == "CREATE"
    assert signal["course_code"] == "CSE 221"


def test_drops_course_codes_outside_the_chat(monkeypatch) -> None:
    from app.schemas import AnalysisPayload

    def fake_analyze(request):
        return AnalysisPayload.model_validate({"signals": [{
            "event_key": None, "action": "CREATE", "category": "EVENT_INFORMATION",
            "course_code": code, "title": "CT", "summary": "CT", "starts_at": None, "due_at": None,
            "location": None, "details": None, "status": "SPECULATION", "confidence": 0.6,
            "urgency": "LOW", "should_notify": False, "change_summary": None,
            "evidence_message_ids": [10],
        } for code in ("cse 221", "MAT 101")]})

    monkeypatch.setattr("app.main.provider_for", lambda _settings: type("P", (), {"analyze": staticmethod(fake_analyze)})())
    response = client.post("/v1/analyze", json=multi_course_body("CT soon"), headers={"Authorization": f"Bearer {TOKEN}"})
    assert [signal["course_code"] for signal in response.json()["signals"]] == ["CSE 221", None]


def test_rejects_oversized_message() -> None:
    response = client.post(
        "/v1/analyze",
        json=request_body("x" * 4001),
        headers={"Authorization": f"Bearer {TOKEN}"},
    )
    assert response.status_code == 422
