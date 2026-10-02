from uuid import uuid4

from fastapi import Depends, FastAPI

from .auth import require_device
from .config import Settings, get_settings
from .providers import provider_for
from .schemas import AnalyzeRequest, AnalyzeResponse, SignalOutput

app = FastAPI(title="Academic Signal API", version="1.0.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/v1/analyze", response_model=AnalyzeResponse, dependencies=[Depends(require_device)])
def analyze(request: AnalyzeRequest, settings: Settings = Depends(get_settings)) -> AnalyzeResponse:
    payload = provider_for(settings).analyze(request)
    allowed_ids = {message.id for message in request.messages}
    known_keys = {event.event_key for event in request.known_events}
    output: list[SignalOutput] = []
    for draft in payload.signals:
        evidence = [message_id for message_id in draft.evidence_message_ids if message_id in allowed_ids]
        if not evidence:
            continue
        if draft.action.value in {"UPDATE", "NO_CHANGE"}:
            if draft.event_key not in known_keys:
                continue
            event_key = draft.event_key
        else:
            event_key = f"event-{uuid4()}"
        output.append(SignalOutput(
            id=uuid4(),
            event_key=event_key,
            **draft.model_dump(exclude={"event_key", "evidence_message_ids"}),
            evidence_message_ids=evidence,
        ))
    return AnalyzeResponse(request_id=request.request_id, signals=output)
