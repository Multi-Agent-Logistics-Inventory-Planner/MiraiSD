# events.py
import json
import time
from collections.abc import Iterator
from pathlib import Path


class FileEventSource:
    """Yields events appended to NDJSON file (simulates Kafka)."""

    def __init__(self, path="events/inventory-changes.ndjson"):
        self.path = Path(path)

    def stream(self, poll_interval=1.0) -> Iterator[dict]:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.touch(exist_ok=True)
        with self.path.open("r", encoding="utf-8") as f:
            f.seek(0, 2)  # go to end
            while True:
                line = f.readline()
                if not line:
                    time.sleep(poll_interval)
                    continue
                try:
                    yield json.loads(line)
                except json.JSONDecodeError:
                    continue
