from pathlib import Path

import pytest

from tvtool import cli, provision as pv
from tvtool.adb import AdbError

HOST = "192.168.1.50"
TCL = pv.load_provision_profiles()


def tcl_profile():
    return pv.match_provision_profile(TCL, "TCL", "32S5403AF")


def probe(**kw):
    base = dict(sdk=30, manufacturer="TCL", model="32S5403AF",
                installed={"com.google.android.tvlauncher", "com.android.tv.settings"},
                disabled=set(),
                secure={"enabled_notification_listeners": None, "enabled_accessibility_services": None,
                        "accessibility_enabled": None, "tv_user_setup_complete": "0"},
                home_holder="com.google.android.tvlauncher")
    base.update(kw)
    return pv.Probe(**base)


ALL_APKS = {"dev.tvlauncher": "app-debug.apk", "dev.tvlauncher.helper": "helper-debug.apk",
            "dev.tvlauncher.store": "store-debug.apk"}


# ------------------------------------------------------------- profiles and APKs

def test_profiles_tclExactMatch_andGenericFallback():
    assert tcl_profile().name.startswith("TCL 32S5403AF")
    g = pv.match_provision_profile(TCL, "Xiaomi", "MiBox")
    assert g.fallback and "com.amazon.tv.launcher" in g.stock_launchers


def test_profile_rejectsOurOwnAppAsStockLauncher(tmp_path):
    f = tmp_path / "bad.json"
    f.write_text('{"name":"x","match":{"fallback":true},"stock_launchers":["dev.tvlauncher"]}')
    with pytest.raises(ValueError):
        pv.load_provision_profile(f)


def test_matchApks_knownNamesOnly():
    m = pv.match_apks(["app-debug.apk", "helper-release.apk", "dev.tvlauncher.weather-1.0.apk",
                       "store-debug.apk", "browser-debug.apk", "random-game.apk", "notes.txt"])
    assert m == {"dev.tvlauncher": "app-debug.apk", "dev.tvlauncher.helper": "helper-release.apk",
                 "dev.tvlauncher.weather": "dev.tvlauncher.weather-1.0.apk", "dev.tvlauncher.store": "store-debug.apk",
                 "dev.tvlauncher.browser": "browser-debug.apk"}


# ------------------------------------------------------------- planner

def cmds(steps):
    return [" ".join(s.argv) if s.apk is None else f"install {s.apk}" for s in steps]


def test_freshTcl_fullPlan_inOrder_withoutTakeover():
    steps = pv.plan_steps(probe(), ALL_APKS, tcl_profile(), take_over_home=False)
    c = cmds(steps)
    assert c[:3] == ["install app-debug.apk", "install helper-debug.apk", "install store-debug.apk"]
    assert "pm grant dev.tvlauncher android.permission.WRITE_SECURE_SETTINGS" in c
    assert "pm grant dev.tvlauncher android.permission.READ_EXTERNAL_STORAGE" in c
    assert "cmd notification allow_listener dev.tvlauncher/dev.tvlauncher.ambient.AmbientNotificationService" in c
    assert ("settings put secure enabled_accessibility_services "
            "dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService") in c
    assert "settings put secure accessibility_enabled 1" in c
    assert "settings put secure tv_user_setup_complete 1" in c
    assert c[-1] == "cmd role add-role-holder android.app.role.HOME dev.tvlauncher"
    assert not any("disable-user" in x for x in c)
    assert all(s.reason for s in steps)


def test_takeover_disablesOnlyInstalledStockLaunchers_withUndo():
    p = probe(installed={"com.google.android.tvlauncher", "com.amazon.tv.launcher", "dev.tvlauncher"})
    steps = pv.plan_steps(p, {}, tcl_profile(), take_over_home=True)
    dis = [s for s in steps if s.takeover]
    assert [s.argv for s in dis] == [("pm", "disable-user", "--user", "0", "com.google.android.tvlauncher")]
    assert dis[0].undo == (("pm", "enable", "com.google.android.tvlauncher"),)
    # Already disabled: nothing to do.
    p2 = probe(installed={"com.google.android.tvlauncher", "dev.tvlauncher"}, disabled={"com.google.android.tvlauncher"})
    assert not any(s.takeover for s in pv.plan_steps(p2, {}, tcl_profile(), True))


def test_alreadyProvisioned_planIsEmpty():
    p = probe(installed={"dev.tvlauncher", "dev.tvlauncher.helper"}, home_holder="dev.tvlauncher",
              secure={"enabled_notification_listeners": "dev.tvlauncher/.ambient.AmbientNotificationService",
                      "enabled_accessibility_services": "dev.tvlauncher.helper/.HelperService",
                      "accessibility_enabled": "1", "tv_user_setup_complete": "1"},
              granted={pv.WRITE_SECURE, *pv.RUNTIME_PERMS}, usage_allowed=True, helper_granted={pv.WRITE_SECURE})
    assert pv.plan_steps(p, {}, tcl_profile(), take_over_home=False) == []


def test_tvUserSetupComplete_onlyWhenPresentAndZero():
    for v in (None, "1"):
        p = probe(secure={**probe().secure, "tv_user_setup_complete": v})
        assert not any("tv_user_setup_complete" in " ".join(s.argv) for s in pv.plan_steps(p, ALL_APKS, tcl_profile(), False))


def test_accessibility_keepsOtherServices_andUndoRestoresThem():
    p = probe(installed={"dev.tvlauncher", "dev.tvlauncher.helper"},
              secure={**probe().secure, "enabled_accessibility_services": "com.vendor.tts/com.vendor.tts.S",
                      "accessibility_enabled": "1"})
    s = next(s for s in pv.plan_steps(p, {}, tcl_profile(), False) if "enabled_accessibility_services" in s.argv)
    assert s.argv[-1] == "com.vendor.tts/com.vendor.tts.S:dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService"
    assert s.undo == (("settings", "put", "secure", "enabled_accessibility_services", "com.vendor.tts/com.vendor.tts.S"),)


def test_legacyAndroid_usesSetHomeActivity():
    steps = pv.plan_steps(probe(sdk=26), ALL_APKS, tcl_profile(), False)
    assert "cmd package set-home-activity dev.tvlauncher/dev.tvlauncher.HomeActivity" in cmds(steps)


def test_noLauncher_noApk_onlyNothingPlanned():
    assert pv.plan_steps(probe(), {}, tcl_profile(), True) == []


def test_scopeGuard_refusesForeignPackages():
    bad = pv.Step(("pm", "disable-user", "--user", "0", "com.netflix.ninja"), "x", takeover=True)
    with pytest.raises(ValueError):
        pv.check_scope([bad], tcl_profile())


def test_undoLines_reverseOrder_andValidated():
    steps = pv.plan_steps(probe(installed={"com.google.android.tvlauncher"}), ALL_APKS, tcl_profile(), True)
    lines = pv.undo_lines(steps, HOST)
    assert lines[0] == f"adb -s {HOST}:5555 shell pm enable com.google.android.tvlauncher"
    assert lines[-1] == f"adb -s {HOST}:5555 shell pm uninstall dev.tvlauncher"
    assert all(pv.undo_allowed(pv.UNDO_LINE.match(l)["cmd"]) for l in lines)
    with pytest.raises(ValueError):
        pv.undo_lines([pv.Step(("x",), "x", undo=(("rm", "-rf", "/"),))], HOST)


# ------------------------------------------------------------- end to end with a fake TV

class FakeTv:
    def __init__(self, sdk=30, secure=None, installed=None, ignore_listener=False, fail_on=None):
        self.sdk = sdk
        self.secure = {"tv_user_setup_complete": "0", **(secure or {})}
        self.installed = set(installed or {"com.google.android.tvlauncher", "com.android.tv.settings"})
        self.disabled = set()
        self.granted = set()
        self.ops = {}
        self.home = "com.google.android.tvlauncher"
        self.ignore_listener = ignore_listener
        self.fail_on = fail_on
        self.calls = []

    def connect(self, host):
        pass

    def install(self, path):
        self.calls.append(("install", path))
        self.installed.update(pv.match_apks([Path(path).name]))
        return "Performing Streamed Install\nSuccess\n"

    def shell(self, *a):
        self.calls.append(a)
        if self.fail_on and " ".join(a).startswith(self.fail_on):
            raise AdbError("device offline")
        a = list(a)
        if a[0] == "getprop":
            return {"ro.product.manufacturer": "TCL", "ro.product.model": "32S5403AF",
                    "ro.build.version.sdk": str(self.sdk)}.get(a[1], "") + "\n"
        if a[:3] == ["pm", "list", "packages"]:
            pk = self.disabled if "-d" in a else self.installed
            return "".join(f"package:{p}\n" for p in sorted(pk))
        if a[:2] == ["pm", "path"]:
            return f"package:/data/app/{a[2]}/base.apk\n" if a[2] in self.installed else ""
        if a[:3] == ["settings", "get", "secure"]:
            return (self.secure.get(a[3]) or "null") + "\n"
        if a[:3] == ["settings", "put", "secure"]:
            self.secure[a[3]] = a[4]
            return ""
        if a[:3] == ["settings", "delete", "secure"]:
            self.secure.pop(a[3], None)
            return ""
        if a[:2] == ["dumpsys", "package"]:
            return "".join(f"      {p}: granted=true\n" for (pkg, p) in sorted(self.granted) if pkg == a[2])
        if a[:2] == ["pm", "grant"]:
            self.granted.add((a[2], a[3]))
            return ""
        if a[:2] == ["pm", "revoke"]:
            self.granted.discard((a[2], a[3]))
            return ""
        if a[:2] == ["pm", "uninstall"]:
            self.installed.discard(a[2])
            return "Success\n"
        if a[:3] == ["cmd", "notification", "disallow_listener"]:
            self.secure.pop("enabled_notification_listeners", None)
            return ""
        if a[:3] == ["cmd", "role", "remove-role-holder"]:
            return ""
        if a[:2] == ["appops", "get"]:
            return f"{a[3]}: {self.ops.get((a[2], a[3]), 'default')}\n"
        if a[:2] == ["appops", "set"]:
            self.ops[(a[2], a[3])] = a[4]
            return ""
        if a[:3] == ["cmd", "notification", "allow_listener"]:
            if not self.ignore_listener:
                self.secure["enabled_notification_listeners"] = a[3]
            return ""
        if a[:3] == ["cmd", "role", "get-role-holders"]:
            return self.home + "\n"
        if a[:3] == ["cmd", "role", "add-role-holder"]:
            self.home = a[4]
            return ""
        if a[:3] == ["cmd", "package", "query-activities"]:
            return "dev.tvlauncher/.HomeActivity\ncom.google.android.tvlauncher/.MainActivity\n"
        if a[:2] == ["pm", "disable-user"]:
            self.disabled.add(a[4])
            return f"Package {a[4]} new state: disabled-user\n"
        if a[:2] == ["pm", "enable"]:
            self.disabled.discard(a[2])
            return f"Package {a[2]} new state: enabled\n"
        raise AssertionError(f"unexpected command {a}")


def apk_dir(tmp_path):
    d = tmp_path / "apks"
    d.mkdir()
    for n in ("app-debug.apk", "helper-debug.apk", "unrelated.apk"):
        (d / n).write_bytes(b"x")
    return d


def run(tmp_path, tv, *extra):
    out = []
    code = cli.main(["provision", "--host", HOST, "--apks", str(apk_dir(tmp_path)), "--out", str(tmp_path / "out"), *extra],
                    adb=tv, print_fn=out.append)
    return code, "\n".join(out)


def mutations(tv):
    return [c for c in tv.calls if c[0] == "install" or c[:2] in (("pm", "grant"), ("pm", "disable-user"))
            or c[:3] in (("settings", "put", "secure"), ("cmd", "role", "add-role-holder"))
            or c[:2] == ("appops", "set") or c[:3] == ("cmd", "notification", "allow_listener")]


def test_dryRunByDefault_printsCommandsAndReasons_changesNothing(tmp_path):
    tv = FakeTv()
    code, text = run(tmp_path, tv, "--take-over-home")
    assert code == 0
    assert mutations(tv) == []
    assert "Dry run: nothing was changed" in text
    assert f"adb -s {HOST}:5555 install -r" in text and "why:" in text
    assert "pm disable-user --user 0 com.google.android.tvlauncher" in text
    assert "unrelated.apk" not in text
    assert not (tmp_path / "out").exists()


def test_apply_runsVerifiesAndWritesUndo(tmp_path):
    tv = FakeTv()
    code, text = run(tmp_path, tv, "--apply")
    assert code == 0, text
    assert tv.home == "dev.tvlauncher"
    assert tv.secure["tv_user_setup_complete"] == "1"
    assert ("dev.tvlauncher", pv.WRITE_SECURE) in tv.granted
    assert ("dev.tvlauncher.helper", pv.WRITE_SECURE) in tv.granted
    assert tv.secure["enabled_accessibility_services"] == pv.HELPER_SERVICE
    assert tv.secure["accessibility_enabled"] == "1"
    assert tv.disabled == set()  # no --take-over-home
    undo = list((tmp_path / "out").glob("provision_undo_*.sh"))
    assert len(undo) == 1 and "pm uninstall dev.tvlauncher" in undo[0].read_text()
    assert "Provisioning complete" in text


def test_apply_takeover_thenRollbackRestores(tmp_path):
    tv = FakeTv()
    code, text = run(tmp_path, tv, "--apply", "--take-over-home")
    assert code == 0, text
    assert tv.disabled == {"com.google.android.tvlauncher"}
    undo = next((tmp_path / "out").glob("provision_undo_*.sh"))
    out = []
    assert cli.main(["rollback", "--host", HOST, "--script", str(undo)], adb=tv, print_fn=out.append) == 0, out
    assert tv.disabled == set()
    assert tv.secure["tv_user_setup_complete"] == "0"
    assert "enabled_accessibility_services" not in tv.secure
    assert tv.home == "com.google.android.tvlauncher"
    assert "dev.tvlauncher" not in tv.installed


def test_apply_listenerNotReported_isAWarningNotAFailure(tmp_path):
    tv = FakeTv(ignore_listener=True)
    code, text = run(tmp_path, tv, "--apply")
    assert code == 0
    assert "does not report it yet" in text


def test_apply_stopsOnAdbError_andPointsToUndo(tmp_path):
    tv = FakeTv(fail_on="settings put secure tv_user_setup_complete")
    code, text = run(tmp_path, tv, "--apply", "--take-over-home")
    assert code == 1
    assert "STOPPED" in text and "provision_undo_" in text
    assert tv.disabled == set()  # later steps never ran


def test_missingApkFolder_isAnError(tmp_path):
    out = []
    assert cli.main(["provision", "--host", HOST, "--apks", str(tmp_path / "nope")], adb=FakeTv(), print_fn=out.append) == 2


def test_rollback_refusesForeignLines(tmp_path):
    f = tmp_path / "evil.sh"
    f.write_text(f"adb -s {HOST}:5555 shell pm uninstall com.android.vending\nadb -s {HOST}:5555 shell rm -rf /\n")
    out = []
    tv = FakeTv()
    assert cli.main(["rollback", "--host", HOST, "--script", str(f)], adb=tv, print_fn=out.append) == 1
    assert tv.calls == []


@pytest.mark.parametrize("flat,expected", [
    (None, pv.HELPER_SERVICE),
    ("", pv.HELPER_SERVICE),
    ("null", pv.HELPER_SERVICE),
    (pv.HELPER_SERVICE, pv.HELPER_SERVICE),
    ("dev.tvlauncher.helper/.HelperService", "dev.tvlauncher.helper/.HelperService"),
    ("a.b/a.b.S", "a.b/a.b.S:" + pv.HELPER_SERVICE),
    ("a.b/a.b.S:c.d/.T", "a.b/a.b.S:c.d/.T:" + pv.HELPER_SERVICE),
    (":a.b/a.b.S::", "a.b/a.b.S:" + pv.HELPER_SERVICE),
    ("a.b/a.b.S:" + pv.HELPER_SERVICE + ":c.d/c.d.T", "a.b/a.b.S:" + pv.HELPER_SERVICE + ":c.d/c.d.T"),
])
def test_mergeServices(flat, expected):
    assert pv.merge_services(flat, pv.HELPER_SERVICE) == expected
