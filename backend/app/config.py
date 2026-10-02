from functools import lru_cache

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    device_token: str = Field(min_length=32)
    analysis_provider: str = "heuristic"
    openai_api_key: str | None = None
    openai_model: str | None = None
    gemini_api_key: str | None = None
    gemini_model: str | None = None
    cors_origins: str = ""
    max_requests_per_minute: int = 20

    @model_validator(mode="after")
    def validate_provider(self) -> "Settings":
        if self.analysis_provider not in {"heuristic", "openai", "gemini"}:
            raise ValueError("ANALYSIS_PROVIDER must be heuristic, openai, or gemini")
        if self.analysis_provider == "openai" and not (self.openai_api_key and self.openai_model):
            raise ValueError("OPENAI_API_KEY and OPENAI_MODEL are required for the openai provider")
        if self.analysis_provider == "gemini" and not (self.gemini_api_key and self.gemini_model):
            raise ValueError("GEMINI_API_KEY and GEMINI_MODEL are required for the gemini provider")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
