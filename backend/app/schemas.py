from datetime import datetime
from enum import StrEnum
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Category(StrEnum):
    IRRELEVANT = "IRRELEVANT"
    EVENT_INFORMATION = "EVENT_INFORMATION"
    DEADLINE = "DEADLINE"
    LAB_INFORMATION = "LAB_INFORMATION"
    CLASS_INFORMATION = "CLASS_INFORMATION"
    COURSE_QUESTION = "COURSE_QUESTION"
    COURSE_ANSWER = "COURSE_ANSWER"
    PROGRAMMING_PROBLEM = "PROGRAMMING_PROBLEM"
    RESOURCE = "RESOURCE"
    SPECULATION = "SPECULATION"
    CONFIRMATION = "CONFIRMATION"
    CORRECTION = "CORRECTION"


class Urgency(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class EventAction(StrEnum):
    CREATE = "CREATE"
    UPDATE = "UPDATE"
    NO_CHANGE = "NO_CHANGE"


class EventStatus(StrEnum):
    QUESTION = "QUESTION"
    SPECULATION = "SPECULATION"
    CONFIRMED = "CONFIRMED"
    CORRECTION = "CORRECTION"
    RESOURCE = "RESOURCE"


class MessageInput(StrictModel):
    id: int = Field(gt=0)
    sender: str = Field(max_length=200)
    text: str = Field(min_length=1, max_length=4000)
    timestamp: int = Field(gt=0)
    local_category: str = Field(max_length=80)


class ConversationInput(StrictModel):
    id: int = Field(gt=0)
    platform: str = Field(min_length=1, max_length=80)
    name: str = Field(min_length=1, max_length=300)
    course_code: str | None = Field(default=None, max_length=80)


class KnownEventInput(StrictModel):
    event_key: str = Field(min_length=1, max_length=100)
    category: Category
    course_code: str | None = Field(default=None, max_length=80)
    title: str = Field(max_length=200)
    summary: str = Field(max_length=1000)
    starts_at: int | None = Field(default=None, gt=0)
    due_at: int | None = Field(default=None, gt=0)
    location: str | None = Field(default=None, max_length=200)
    details: str | None = Field(default=None, max_length=2000)
    status: EventStatus
    confidence: float = Field(ge=0, le=1)
    updated_at: int = Field(gt=0)


class AnalyzeRequest(StrictModel):
    request_id: UUID
    timezone: str = Field(min_length=1, max_length=80)
    conversation: ConversationInput
    messages: list[MessageInput] = Field(min_length=1, max_length=20)
    known_events: list[KnownEventInput] = Field(default_factory=list, max_length=30)


class SignalDraft(StrictModel):
    event_key: str | None = Field(max_length=100)
    action: EventAction
    category: Category
    course_code: str | None = Field(max_length=80)
    title: str = Field(min_length=1, max_length=200)
    summary: str = Field(min_length=1, max_length=1000)
    starts_at: str | None
    due_at: str | None
    location: str | None = Field(max_length=200)
    details: str | None = Field(max_length=2000)
    status: EventStatus
    confidence: float = Field(ge=0, le=1)
    urgency: Urgency
    should_notify: bool
    change_summary: str | None = Field(max_length=500)
    evidence_message_ids: list[int] = Field(min_length=1, max_length=20)

    @field_validator("starts_at", "due_at")
    @classmethod
    def validate_timestamp(cls, value: str | None) -> str | None:
        if value is None:
            return None
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError("timestamps must include a UTC offset")
        return value


class AnalysisPayload(StrictModel):
    signals: list[SignalDraft] = Field(max_length=10)


class SignalOutput(SignalDraft):
    id: UUID
    event_key: str


class AnalyzeResponse(StrictModel):
    request_id: UUID
    signals: list[SignalOutput]
