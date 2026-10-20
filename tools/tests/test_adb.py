import subprocess

import pytest

from tvtool import adb as A

FOCUS_PP = ("  mCurrentFocus=Window{fbff3ee u0 com.android.vending/"
            "com.google.android.finsky.protectdialogs.activity.PlayProtectDialogsActivity}\n")
FOCUS_HOME = "  mCurrentFocus=Window{f119356 u0 dev.tvlauncher/dev.tvlauncher.HomeActivity}\n"


def test_play_protect_detected_only_when_focused():
    assert A.play_protect_in_front(FOCUS_PP)
    assert not A.play_protect_in_front(FOCUS_HOME)
    assert not A.play_protect_in_front("")
    # The Play Store itself in front is not the dialog.
    assert not A.play_protect_in_front("  mCurrentFocus=Window{1 u0 com.android.vending/com.google.android.finsky.activities.MainActivity}")


class HangingProc:
    returncode = None

    def __init__(self, *a, **k):
        self.killed = False

    def communicate(self, timeout=None):
        if self.killed:
            return "", ""
        raise subprocess.TimeoutExpired("adb", timeout)

    def kill(self):
        self.killed = True


def test_install_stops_with_clear_message_when_play_protect_holds_it(monkeypatch):
    monkeypatch.setattr(A.subprocess, "Popen", HangingProc)
    tv = A.SubprocessAdb("192.168.1.50")
    monkeypatch.setattr(tv, "shell", lambda *a: FOCUS_PP)
    with pytest.raises(A.AdbError) as e:
        tv.install("/x/link-debug.apk", clock=lambda: 0.0)
    assert "Install anyway" in str(e.value) and "PROVISIONING.md" in str(e.value)


def test_install_times_out_without_the_dialog(monkeypatch):
    monkeypatch.setattr(A.subprocess, "Popen", HangingProc)
    tv = A.SubprocessAdb("192.168.1.50")
    monkeypatch.setattr(tv, "shell", lambda *a: FOCUS_HOME)
    t = iter([0.0, 0.0, A.INSTALL_TIMEOUT_S + 1, A.INSTALL_TIMEOUT_S + 1])
    with pytest.raises(A.AdbError) as e:
        tv.install("/x/app-debug.apk", clock=lambda: next(t))
    assert "timeout" in str(e.value)
