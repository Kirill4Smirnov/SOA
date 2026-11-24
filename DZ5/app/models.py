from datetime import datetime, timezone
from enum import Enum
from uuid import UUID, uuid4

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, field_validator, model_validator


class EventType(str, Enum):
    VIEW_STARTED = 'VIEW_STARTED'
    VIEW_FINISHED = 'VIEW_FINISHED'
    VIEW_PAUSED = 'VIEW_PAUSED'
    VIEW_RESUMED = 'VIEW_RESUMED'
    LIKED = 'LIKED'
    SEARCHED = 'SEARCHED'


class DeviceType(str, Enum):
    MOBILE = 'MOBILE'
    DESKTOP = 'DESKTOP'
    TV = 'TV'
    TABLET = 'TABLET'


class MovieEvent(BaseModel):
    model_config = ConfigDict(extra='forbid', json_schema_extra={'example': {
        'user_id': 'user-1', 'movie_id': 'movie-1', 'event_type': 'VIEW_STARTED',
        'timestamp': '2026-11-20T12:00:00Z', 'device_type': 'TV',
        'session_id': 'session-1', 'progress_seconds': 0,
    }})

    event_id: UUID = Field(default_factory=uuid4)
    user_id: str = Field(min_length=1, max_length=128)
    movie_id: str = Field(max_length=128)
    event_type: EventType
    timestamp: AwareDatetime
    device_type: DeviceType
    session_id: str = Field(min_length=1, max_length=128)
    progress_seconds: int = Field(ge=0, le=2_147_483_647, strict=True)
    search_query: str | None = Field(default=None, max_length=500)

    @field_validator('timestamp')
    @classmethod
    def utc_timestamp(cls, value):
        value = value.astimezone(timezone.utc)
        if value.year < 2000 or value > datetime.now(timezone.utc):
            raise ValueError('timestamp must be between 2000-01-01 and now')
        return value

    @model_validator(mode='after')
    def check_progress(self):
        if self.event_type != EventType.SEARCHED and not self.movie_id:
            raise ValueError('movie_id is required except for SEARCHED')
        if self.event_type in (EventType.VIEW_STARTED, EventType.SEARCHED, EventType.LIKED) and self.progress_seconds:
            raise ValueError('progress_seconds must be 0 for started/search/like events')
        return self

    def avro_record(self):
        record = self.model_dump(mode='json')
        record['timestamp'] = self.timestamp
        return record


class GenerateRequest(BaseModel):
    users: int = Field(default=8, ge=1, le=50)
    days: int = Field(default=10, ge=8, le=30)
