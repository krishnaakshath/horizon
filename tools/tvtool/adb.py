"""ADB access. Commands are always passed as argv lists, never via a shell string."""
from __future__ import annotations

import subprocess
import time
from typing import Callable, Protocol

TIMEOUT_S = 30
INSTALL_TIMEOUT_S = 180
# While an install runs, the focused window is checked this often for Google Play Protect's dialog.
PLAY_PROTECT_POLL_S = 3.0
PLAY_PROTECT_MSG = (
    "Google Play Protect is holding the install on the TV ('Unsafe app blocked' or 'Blocked by Play Protect'). "
    "On the TV, open 'More details' and choose 'Install anyway', then run this command again. "
    "See docs/PROVISIONING.md, Troubleshooting."
)


def play_protect_in_front(dumpsys_window: str) -> bool:
    """True when the focused window is Play Protect's install dialog (from `dumpsys window`)."""
    for line in dumpsys_window.splitlines():
        if "mCurrentFocus" in line or "mFocusedApp" in line:
            low = line.lower()
            if "com.android.vending" in low and ("protect" in low or "verify" in low):
                return True
    return False


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

    def install(self, apk_path: str, clock: Callable[[], float] = time.monotonic) -> str:
        """Installs or updates (-r keeps data) one APK from this computer.

        Stops early with [PLAY_PROTECT_MSG] when Play Protect's dialog holds the install on the TV
        (a fresh install of an app it flags waits for a choice there, and adb would just hang).
        """
        argv = ["adb", "-s", self.serial, "install", "-r", apk_path]
        try:
            proc = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        except OSError as e:
            raise AdbError(f"cannot run adb: {e}") from e
        deadline = clock() + INSTALL_TIMEOUT_S
        while True:
            try:
                out, err = proc.communicate(timeout=PLAY_PROTECT_POLL_S)
                break
            except subprocess.TimeoutExpired:
                pass
            try:
                focus = self.shell("dumpsys", "window")
            except AdbError:
                focus = ""
            if play_protect_in_front(focus) or clock() >= deadline:
                proc.kill()
                proc.communicate()
                if play_protect_in_front(focus):
                    raise AdbError(PLAY_PROTECT_MSG)
                raise AdbError(f"timeout after {INSTALL_TIMEOUT_S}s: {' '.join(argv)}")
        if proc.returncode != 0:
            raise AdbError(f"adb exited {proc.returncode}: {(err or out).strip()}")
        return out
