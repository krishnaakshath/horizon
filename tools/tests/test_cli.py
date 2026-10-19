import json
import re
from pathlib import Path

import pytest

from tvtool import cli
from tvtool.adb import AdbError
from tvtool.profile import GLOBAL_NEVER

REPO = Path(__file__).resolve().parents[2]
PROFILE_PATH = REPO / "profiles" / "tcl-32S5403AF.json"
HOST = "192.168.1.50"
FP = "TCL/C06_2K_GB/C06:11/RP1A/abc:user/release-keys"
MEM = "MemTotal:        1000000 kB\nMemFree: 1 kB\nMemAvailable:     400000 kB\n"


class FakeAdb:
    def __init__(self, packages, disabled=(), manufacturer="TCL", fingerprint=FP,
                 fail_on=None, scales=None, on_call=None):
        self.packages = list(packages)
        self.disabled = set(disabled)
        self.manufacturer = manufacturer
        self.fingerprint = fingerprint
        self.fail_on = fail_on
        self.scales = scales or {}
        self.on_call = on_call
        self.calls = []
        self.mem = 400000

    def connect(self, host):
        self.calls.append(("connect", host))

    def shell(self, *args):
        self.calls.append(args)
        if self.on_call:
            self.on_call(args, len(self.calls))
        if self.fail_on is not None and len(self.calls) == self.fail_on:
            raise AdbError("device offline")
        a = list(args)
        if a[:1] == ["getprop"]:
            return {"ro.product.manufacturer": self.manufacturer,
                    "ro.product.model": "32S5403AF",
                    "ro.build.fingerprint": self.fingerprint,
                    "ro.build.version.release": "11"}.get(a[1], "") + "\n"
        if a[:2] == ["cat", "/proc/meminfo"]:
            return f"MemTotal: 1000000 kB\nMemAvailable: {self.mem} kB\n"
        if a[:3] == ["pm", "list", "packages"]:
            pk = sorted(self.disabled) if "-d" in a else self.packages
            return "".join(f"package:{p}\n" for p in pk)
        if a[:3] == ["pm", "disable-user", "--user"]:
            self.disabled.add(a[4])
            self.mem += 1000
            return f"Package {a[4]} new state: disabled-user\n"
        if a[:2] == ["pm", "enable"]:
            return f"Package {a[2]} new state: enabled\n"
        if a[:3] == ["settings", "get", "global"]:
            return self.scales.get(a[3], "null") + "\n"
        if a[:3] == ["settings", "put", "global"]:
            return ""
        return ""

    def writes(self):
        return [c for c in self.calls if c and c[0] in ("pm", "settings")
                and not (c[0] == "pm" and c[1] == "list") and not (c[0] == "settings" and c[1] == "get")]

    def disables(self):
        return [c for c in self.calls if c[:3] == ("pm", "disable-user", "--user")]


def run(args, adb, tmp_path, answers=()):
    it = iter(answers)
    out = []
    code = cli.main(args + ["--host", HOST, "--out", str(tmp_path), "--profile", str(PROFILE_PATH)],
                    adb=adb, input_fn=lambda prompt="": next(it, ""), print_fn=lambda *a: out.append(" ".join(map(str, a))))
    return code, "\n".join(out)


def profile_pkgs():
    p = json.loads(PROFILE_PATH.read_text())
    return p["safe"], [o["pkg"] for o in p["optional"]]


def rollback_files(tmp_path):
    return sorted(tmp_path.glob("rollback_*.sh"))


def test_scan_makesNoWriteCalls(tmp_path):
    safe, _ = profile_pkgs()
    adb = FakeAdb(safe + ["com.netflix.ninja"])
    code, _ = run(["scan"], adb, tmp_path)
    assert code == 0
    assert adb.writes() == []
    allowed = {"getprop", "cat", "pm", "settings", "dumpsys", "connect"}
    assert all(c[0] in allowed for c in adb.calls)
    reports = list(tmp_path.glob("*.json"))
    assert len(reports) == 1
    rep = json.loads(reports[0].read_text())
    assert rep["model"] == "32S5403AF" and "com.netflix.ninja" in rep["packages"]
    assert rep["memAvailableKb"] == 400000


def test_apply_writesRollbackBeforeFirstDisable(tmp_path):
    safe, _ = profile_pkgs()
    seen = {}

    def hook(args, n):
        if args[:2] == ("pm", "disable-user") and "snap" not in seen:
            files = rollback_files(tmp_path)
            seen["snap"] = files[0].read_text() if files else None

    adb = FakeAdb(safe, on_call=hook)
    code, _ = run(["apply", "--yes"], adb, tmp_path)
    assert code == 0
    assert seen["snap"] is not None
    assert f"adb -s {HOST}:5555 shell pm enable {safe[0]}" in seen["snap"]
    assert seen["snap"].startswith("#!/bin/sh")


def test_apply_adbFailureMidBatch_stopsAndRollbackCoversAppliedPackages(tmp_path):
    safe, _ = profile_pkgs()
    adb = FakeAdb(safe)
    state = {"n": 0}
    orig = adb.shell

    def failing(*args):
        if args[:2] == ("pm", "disable-user"):
            state["n"] += 1
            if state["n"] == 3:
                adb.calls.append(args)
                raise AdbError("device offline")
        return orig(*args)

    adb.shell = failing
    code, out = run(["apply", "--yes"], adb, tmp_path)
    assert code != 0
    assert len(adb.disables()) == 3  # stopped immediately, no 4th attempt
    text = rollback_files(tmp_path)[0].read_text()
    for pkg in safe[:2]:
        assert f"shell pm enable {pkg}" in text


def test_apply_unexpectedOutput_stops(tmp_path):
    safe, _ = profile_pkgs()
    adb = FakeAdb(safe)
    orig = adb.shell
    def bad(*a):
        if a[:2] == ("pm", "disable-user"):
            adb.calls.append(a)
            return "Failure\n"
        return orig(*a)
    adb.shell = bad
    code, _ = run(["apply", "--yes"], adb, tmp_path)
    assert code != 0
    assert len(adb.disables()) == 1


def test_unknownModel_runsAnalyzeOnly_noDisable(tmp_path):
    adb = FakeAdb(["com.foo", "com.bar"], manufacturer="Sony", fingerprint="Sony/x/y:12")
    for cmd in (["apply", "--yes"], ["tune"], ["plan"]):
        code, out = run(cmd, adb, tmp_path)
        assert adb.writes() == []
        assert adb.disables() == []
    code, out = run(["apply", "--yes"], adb, tmp_path)
    assert "analyze" in out.lower()
    assert rollback_files(tmp_path) == []


def test_apply_neverIssuesPmUninstall(tmp_path):
    safe, opt = profile_pkgs()
    adb = FakeAdb(safe + opt)
    run(["apply", "--yes", "--tiers", "safe,optional"], adb, tmp_path)
    run(["tune", "--yes"], adb, tmp_path)
    assert adb.disables()
    for c in adb.calls:
        line = " ".join(c)
        assert "uninstall" not in line and "clear" not in line
        assert not re.search(r"\bdisable\b(?!-user)", line)


def test_apply_skipsAlreadyDisabled(tmp_path):
    safe, _ = profile_pkgs()
    adb = FakeAdb(safe, disabled=[safe[0]])
    run(["apply", "--yes"], adb, tmp_path)
    assert all(c[4] != safe[0] for c in adb.disables())
    assert len(adb.disables()) == len(safe) - 1
    assert f"pm enable {safe[0]}" not in rollback_files(tmp_path)[0].read_text()


def test_apply_dryRun_writesNothing(tmp_path):
    safe, _ = profile_pkgs()
    adb = FakeAdb(safe)
    code, out = run(["apply", "--dry-run"], adb, tmp_path)
    assert code == 0 and adb.writes() == [] and rollback_files(tmp_path) == []


def test_apply_waitsForEnterBetweenBatchesUnlessYes(tmp_path):
    safe, opt = profile_pkgs()
    adb = FakeAdb(safe + opt)
    prompts = []
    cli.main(["apply", "--host", HOST, "--out", str(tmp_path), "--profile", str(PROFILE_PATH),
              "--tiers", "safe,optional"], adb=adb,
             input_fn=lambda p="": prompts.append(p) or "", print_fn=lambda *a: None)
    assert len(prompts) == -(-len(safe + opt) // 10)


def test_apply_globalNeverInProfileIsNotDisabled(tmp_path):
    prof = json.loads(PROFILE_PATH.read_text())
    victim = sorted(GLOBAL_NEVER)[0]
    prof["safe"].append(victim)
    p = tmp_path / "evil.json"
    p.write_text(json.dumps(prof))
    adb = FakeAdb(prof["safe"])
    out_dir = tmp_path / "o"
    cli.main(["apply", "--yes", "--host", HOST, "--out", str(out_dir), "--profile", str(p)],
             adb=adb, input_fn=lambda p="": "", print_fn=lambda *a: None)
    assert adb.disables()
    assert all(c[4] != victim for c in adb.disables())
    assert not any(l.endswith(" " + victim) for l in next(out_dir.glob("rollback_*.sh")).read_text().splitlines())


def test_tune_recordsOriginalValuesInRollbackFirst(tmp_path):
    seen = {}

    def hook(args, n):
        if args[:3] == ("settings", "put", "global") and "snap" not in seen:
            seen["snap"] = rollback_files(tmp_path)[0].read_text()

    adb = FakeAdb([], scales={"window_animation_scale": "0.8", "transition_animation_scale": "null",
                              "animator_duration_scale": "1.5"}, on_call=hook)
    code, _ = run(["tune"], adb, tmp_path)
    assert code == 0
    snap = seen["snap"]
    assert "settings put global window_animation_scale 0.8" in snap
    assert "settings put global transition_animation_scale 1.0" in snap
    assert "settings put global animator_duration_scale 1.5" in snap
    puts = [c for c in adb.calls if c[:3] == ("settings", "put", "global")]
    assert [c[4] for c in puts] == ["0.5"] * 3


@pytest.mark.parametrize("scale", ["0", "-1", "2.5", "abc"])
def test_tune_rejectsScaleOutsideZeroToTwo(tmp_path, scale):
    adb = FakeAdb([])
    code, out = run(["tune", "--scale", scale], adb, tmp_path)
    assert code != 0
    assert adb.calls == []
    assert "scale" in out.lower()


def write_script(tmp_path, body):
    p = tmp_path / "r.sh"
    p.write_text(body)
    return p


def test_rollback_runsEnableLines(tmp_path):
    p = write_script(tmp_path, f"#!/bin/sh\n# hdr\nadb -s {HOST}:5555 shell pm enable com.a.b\n"
                               f"adb -s {HOST}:5555 shell settings put global window_animation_scale 1.0\n")
    adb = FakeAdb([])
    code, _ = run(["rollback", "--script", str(p)], adb, tmp_path)
    assert code == 0
    assert ("pm", "enable", "com.a.b") in adb.calls
    assert ("settings", "put", "global", "window_animation_scale", "1.0") in adb.calls


def test_rollback_refusesUnexpectedLines(tmp_path):
    p = write_script(tmp_path, f"#!/bin/sh\nadb -s {HOST}:5555 shell pm enable com.a.b\n"
                               f"adb -s {HOST}:5555 shell pm uninstall com.x; rm -rf /\n")
    adb = FakeAdb([])
    code, out = run(["rollback", "--script", str(p)], adb, tmp_path)
    assert code != 0
    assert adb.writes() == []


def test_host_with_semicolon_rejected_before_any_adb_call(tmp_path):
    adb = FakeAdb([])
    out = []
    code = cli.main(["scan", "--host", "1.2.3.4;rm -rf ~", "--out", str(tmp_path)], adb=adb, print_fn=lambda *a: out.append(a))
    assert code == 2 and adb.calls == []


@pytest.mark.parametrize("h", ["-oProxy", "1.2.3.4:5555", "$(x)", ""])
def test_host_leading_dash_rejected(tmp_path, h):
    adb = FakeAdb([])
    code = cli.main(["scan", f"--host={h}", "--out", str(tmp_path)], adb=adb, print_fn=lambda *a: None)
    assert code == 2 and adb.calls == []


def test_package_name_with_shell_metachar_from_device_aborts_apply_and_writes_nothing(tmp_path):
    safe, _ = profile_pkgs()
    adb = FakeAdb(safe + ["com.x;reboot"])
    code, out = run(["apply", "--yes"], adb, tmp_path)
    assert code != 0 and adb.disables() == [] and rollback_files(tmp_path) == []


def test_profile_rejects_whitespace_padded_package(tmp_path):
    from tvtool.profile import load_profile
    prof = json.loads(PROFILE_PATH.read_text())
    prof["safe"].append(" com.x")
    p = tmp_path / "p.json"
    p.write_text(json.dumps(prof))
    with pytest.raises(ValueError):
        load_profile(p)


def test_tune_nonnumeric_original_aborts_before_any_change(tmp_path):
    adb = FakeAdb([], scales={"window_animation_scale": "1.0E-4"})
    code, _ = run(["tune"], adb, tmp_path)
    assert code != 0 and adb.writes() == [] and rollback_files(tmp_path) == []


def test_unwritable_out_dir_exits_cleanly_without_changes(tmp_path):
    safe, _ = profile_pkgs()
    blocker = tmp_path / "f"
    blocker.write_text("x")
    adb = FakeAdb(safe)
    code = cli.main(["apply", "--yes", "--host", HOST, "--out", str(blocker / "sub"), "--profile", str(PROFILE_PATH)],
                    adb=adb, input_fn=lambda p="": "", print_fn=lambda *a: None)
    assert code == 1 and adb.disables() == []


def test_rollback_file_not_overwritten_on_same_second_rerun(tmp_path, monkeypatch):
    class D:
        @staticmethod
        def now():
            import datetime
            return datetime.datetime(2026, 1, 1, 0, 0, 0)
    monkeypatch.setattr(cli, "datetime", D)
    for _ in range(2):
        run(["tune"], FakeAdb([]), tmp_path)
    assert len(rollback_files(tmp_path)) == 2
