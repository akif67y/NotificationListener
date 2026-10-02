import hmac
import time
from collections import defaultdict, deque

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from .config import Settings, get_settings

bearer = HTTPBearer(auto_error=False)
request_times: dict[str, deque[float]] = defaultdict(deque)


def require_device(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    settings: Settings = Depends(get_settings),
) -> str:
    if credentials is None or credentials.scheme.lower() != "bearer" or not hmac.compare_digest(
        credentials.credentials, settings.device_token
    ):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid device token")

    now = time.monotonic()
    history = request_times[credentials.credentials]
    while history and history[0] < now - 60:
        history.popleft()
    if len(history) >= settings.max_requests_per_minute:
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail="Rate limit exceeded")
    history.append(now)
    return credentials.credentials
