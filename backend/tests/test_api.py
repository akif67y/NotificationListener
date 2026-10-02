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


def test_rejects_oversized_message() -> None:
    response = client.post(
        "/v1/analyze",
        json=request_body("x" * 4001),
        headers={"Authorization": f"Bearer {TOKEN}"},
    )
    assert response.status_code == 422
