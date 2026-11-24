from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

from app.models import MovieEvent
from app.producer import session_events
from app import producer


def sample(**changes):
    event = dict(user_id='u', movie_id='m', event_type='VIEW_STARTED', timestamp='2020-01-01T12:00:00Z',
                 device_type='TV', session_id='s', progress_seconds=0)
    return event | changes


@pytest.mark.parametrize('changes', [
    {'progress_seconds': -1}, {'progress_seconds': 2.5}, {'progress_seconds': '3'},
    {'timestamp': '2020-01-01T12:00:00'}, {'event_type': 'BOUGHT'}, {'device_type': 'WATCH'},
    {'event_id': 'not-uuid'}, {'movie_id': ''}, {'progress_seconds': 3},
])
def test_invalid_events(changes):
    with pytest.raises(ValidationError):
        MovieEvent(**sample(**changes))


def test_utc_and_search():
    event = MovieEvent(**sample(timestamp='2020-01-01T15:00:00+03:00'))
    assert event.timestamp.hour == 12
    search = MovieEvent(**sample(event_type='SEARCHED', movie_id='', search_query='comedy'))
    assert search.search_query == 'comedy'


def test_generated_session_order():
    events = session_events('u', 'm', datetime(2020, 1, 1, tzinfo=timezone.utc))
    assert events[0].event_type == 'VIEW_STARTED'
    assert events[-2].event_type == 'VIEW_FINISHED'
    views = [event for event in events if event.event_type.startswith('VIEW_')]
    assert [event.progress_seconds for event in views] == sorted(event.progress_seconds for event in views)
    assert [event.timestamp for event in events] == sorted(event.timestamp for event in events)


def test_demo_events_are_immutable_when_date_changes(monkeypatch):
    class FrozenDateTime(datetime):
        current_day = 20

        @classmethod
        def now(cls, tz=None):
            return datetime(2026, 11, cls.current_day, 12, tzinfo=timezone.utc)

    class Collector:
        def __init__(self):
            self.events = {}

        def publish(self, event):
            self.events[event.event_id] = event.model_dump(mode='json')

    monkeypatch.setattr(producer, 'datetime', FrozenDateTime)
    first, second = Collector(), Collector()
    producer.generate_history(first, users=2, days=8, prefix='stable-demo')
    FrozenDateTime.current_day = 21
    producer.generate_history(second, users=2, days=8, prefix='stable-demo')
    shared = first.events.keys() & second.events.keys()
    assert shared
    assert all(first.events[key] == second.events[key] for key in shared)
