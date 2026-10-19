"""ADB access. Commands are always passed as argv lists, never via a shell string."""
from __future__ import annotations

import subprocess
from typing import Protocol

TIMEOUT_S = 30


class AdbError(Exception):
    pass


class Adb(Protocol):
    def shell(self, *args: str) -> str: ...
    def connect(self, host: str) -> None: ...


class SubprocessAdb:
    def __init__(self, host: str):
        self.host = host
        self.serial = f"{host}:5555"

    def _run(self, argv: list[str]) -> str:
        try:
            r = subprocess.run(argv, capture_output=True, text=True, timeout=TIMEOUT_S)
        except subprocess.TimeoutExpired as e:
            raise AdbError(f"timeout after {TIMEOUT_S}s: {' '.join(argv)}") from e
        except OSError as e:
            raise AdbError(f"cannot run adb: {e}") from e
        if r.returncode != 0:
            raise AdbError(f"adb exited {r.returncode}: {(r.stderr or r.stdout).strip()}")
        return r.stdout

    def connect(self, host: str) -> None:
        self._run(["adb", "connect", f"{host}:5555"])

    def shell(self, *args: str) -> str:
        return self._run(["adb", "-s", self.serial, "shell", *args])
