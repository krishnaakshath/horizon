"""ADB access. Commands are always passed as argv lists, never via a shell string."""
from __future__ import annotations

import subprocess
from typing import Protocol

TIMEOUT_S = 30
INSTALL_TIMEOUT_S = 180


class AdbError(Exception):
    pass


class Adb(Protocol):
    def shell(self, *args: str) -> str: ...
    def connect(self, host: str) -> None: ...
    def install(self, apk_path: str) -> str: ...


class SubprocessAdb:
    def __init__(self, host: str):
        self.host = host
        self.serial = f"{host}:5555"

    def _run(self, argv: list[str]) -> str:
        limit = INSTALL_TIMEOUT_S if "install" in argv[3:4] else TIMEOUT_S
        try:
            r = subprocess.run(argv, capture_output=True, text=True, timeout=limit)
        except subprocess.TimeoutExpired as e:
            raise AdbError(f"timeout after {limit}s: {' '.join(argv)}") from e
        except OSError as e:
            raise AdbError(f"cannot run adb: {e}") from e
        if r.returncode != 0:
            raise AdbError(f"adb exited {r.returncode}: {(r.stderr or r.stdout).strip()}")
        return r.stdout

    def connect(self, host: str) -> None:
        self._run(["adb", "connect", f"{host}:5555"])

    def shell(self, *args: str) -> str:
        return self._run(["adb", "-s", self.serial, "shell", *args])

    def install(self, apk_path: str) -> str:
        """Installs or updates (-r keeps data) one APK from this computer."""
        return self._run(["adb", "-s", self.serial, "install", "-r", apk_path])
