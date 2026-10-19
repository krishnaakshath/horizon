from pathlib import Path

import pytest

from tvtool import cli, provision as pv
from tvtool.adb import AdbError

HOST = "192.168.1.50"
TCL = pv.load_provision_profiles()


@pytest.fixture(autouse=True)
def no_sleep(monkeypatch):
    monkeypatch.setattr(pv, "sleep", lambda s: None)


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
    installs = [x for x in c if x.startswith("install ")]
    assert installs == ["install helper-debug.apk", "install app-debug.apk", "install store-debug.apk"]
    assert "pm grant dev.tvlauncher android.permission.WRITE_SECURE_SETTINGS" in c
    assert "pm grant dev.tvlauncher android.permission.READ_EXTERNAL_STORAGE" in c
    assert "cmd notification allow_listener dev.tvlauncher/dev.tvlauncher.ambient.AmbientNotificationService" in c
    assert ("settings put secure enabled_accessibility_services "
            "dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService") in c
    assert "settings put secure accessibility_enabled 1" in c
    assert "settings put secure tv_user_setup_complete 1" in c
    assert "appops set dev.tvlauncher SYSTEM_ALERT_WINDOW allow" in c
    assert c[-4] == "cmd role add-role-holder android.app.role.HOME dev.tvlauncher"
    assert c[-3:] == ["am start -a android.settings.SETTINGS",
                      "am broadcast -a tcl.sys.intent.action.STR_RESUME_COMPLETED -n dev.tvlauncher/.power.PowerOnReceiver",
                      "run-as dev.tvlauncher rm -f shared_prefs/power_on.xml"]
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
              granted={pv.WRITE_SECURE, *pv.RUNTIME_PERMS}, usage_allowed=True, helper_granted={pv.WRITE_SECURE},
              ops={**{(k, op): "allow" for k in (pv.LAUNCHER, pv.HELPER) for op in pv.VENDOR_OPS},
                   (pv.LAUNCHER, pv.OVERLAY_OP): "allow"}, helper_bound=True)
    assert pv.plan_steps(p, {}, tcl_profile(), take_over_home=False) == []
    # The self-test can still be asked for on its own.
    assert [s.selftest for s in pv.plan_steps(p, {}, tcl_profile(), False, self_test=True)] == [True, True, True]


def test_tvUserSetupComplete_onlyWhenPresentAndZero():
    for v in (None, "1"):
        p = probe(secure={**probe().secure, "tv_user_setup_complete": v})
        assert not any("tv_user_setup_complete" in " ".join(s.argv) for s in pv.plan_steps(p, ALL_APKS, tcl_profile(), False))


def test_accessibility_keepsOtherServices_andUndoRestoresThem():
    p = probe(installed={"dev.tvlauncher", "dev.tvlauncher.helper"},
              secure={**probe().secure, "enabled_accessibility_services": "com.vendor.tts/com.vendor.tts.S",
                      "accessibility_enabled": "1"})
    s = next(s for s in pv.plan_steps(p, {}, tcl_profile(), False) if s.argv[:4] == ("settings", "put", "secure", "enabled_accessibility_services"))
    assert s.argv[-1] == "com.vendor.tts/com.vendor.tts.S:dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService"
    assert s.undo == (("settings", "put", "secure", "enabled_accessibility_services", "com.vendor.tts/com.vendor.tts.S"),)


def test_accessibility_exactOrder_clearsCrashedMark_thenChecksBound():
    p = probe(installed={"dev.tvlauncher", "dev.tvlauncher.helper"},
              secure={**probe().secure, "enabled_accessibility_services": "com.vendor.tts/com.vendor.tts.S:" + pv.HELPER_SERVICE,
                      "accessibility_enabled": "1"}, helper_bound=False)
    c = cmds(pv.plan_steps(p, {}, tcl_profile(), False))
    i = c.index("settings put secure accessibility_enabled 0")
    assert c[i:i + 5] == [
        "settings put secure accessibility_enabled 0",
        "settings delete secure enabled_accessibility_services",
        "settings put secure enabled_accessibility_services com.vendor.tts/com.vendor.tts.S:" + pv.HELPER_SERVICE,
        "settings put secure accessibility_enabled 1",
        "dumpsys accessibility"]
    # Bound and enabled: nothing to do for the Helper.
    p.helper_bound = True
    assert "dumpsys accessibility" not in cmds(pv.plan_steps(p, {}, tcl_profile(), False))


def test_vendorOps_reappliedAfterEveryInstall_forBothApps_withUndoOfPreviousMode():
    p = probe(installed={"dev.tvlauncher", "dev.tvlauncher.helper"},
              ops={("dev.tvlauncher", "APP_AUTO_START"): "ignore", ("dev.tvlauncher", "APP_ASSOC_START"): "default",
                   ("dev.tvlauncher.helper", "APP_AUTO_START"): "allow", ("dev.tvlauncher.helper", "APP_ASSOC_START"): "allow",
                   ("dev.tvlauncher", "SYSTEM_ALERT_WINDOW"): "default"})
    steps = pv.plan_steps(p, ALL_APKS, tcl_profile(), False)
    c = cmds(steps)
    ops = [f"appops set {pkg} {op} allow" for pkg in ("dev.tvlauncher", "dev.tvlauncher.helper") for op in pv.VENDOR_OPS]
    for n, apk in enumerate(("install helper-debug.apk", "install app-debug.apk", "install store-debug.apk")):
        i = c.index(apk)
        assert sorted(c[i + 1:i + 5]) == sorted(ops), (apk, c[i + 1:i + 5])
    undos = [u for s in steps for u in s.undo if u[:2] == ("appops", "set") and u[3] in pv.VENDOR_OPS]
    assert sorted(undos) == sorted([("appops", "set", "dev.tvlauncher", "APP_AUTO_START", "ignore"),
                                    ("appops", "set", "dev.tvlauncher", "APP_ASSOC_START", "default"),
                                    ("appops", "set", "dev.tvlauncher.helper", "APP_AUTO_START", "allow"),
                                    ("appops", "set", "dev.tvlauncher.helper", "APP_ASSOC_START", "allow")])
    saw = next(s for s in steps if s.argv[3:4] == ("SYSTEM_ALERT_WINDOW",))
    assert saw.undo == (("appops", "set", "dev.tvlauncher", "SYSTEM_ALERT_WINDOW", "default"),)
    # Without installs only the ops that are not "allow" yet.
    c2 = cmds(pv.plan_steps(p, {}, tcl_profile(), False))
    assert [x for x in c2 if "APP_" in x] == ["appops set dev.tvlauncher APP_AUTO_START allow",
                                             "appops set dev.tvlauncher APP_ASSOC_START allow"]


def test_vendorOps_leftOut_whenTheTvDoesNotHaveThem():
    p = probe(installed={"dev.tvlauncher"}, ops={("dev.tvlauncher", "APP_AUTO_START"): pv.UNSUPPORTED,
                                                 ("dev.tvlauncher", "APP_ASSOC_START"): pv.UNSUPPORTED})
    assert not any("APP_" in x for x in cmds(pv.plan_steps(p, ALL_APKS, tcl_profile(), False)))


def test_parseAppopMode_andBound():
    assert pv.parse_appop_mode("APP_AUTO_START: allow; time=+1m\n", "APP_AUTO_START") == "allow"
    assert pv.parse_appop_mode("No operations.\n", "SYSTEM_ALERT_WINDOW") == "default"
    assert pv.parse_appop_mode("Error: Unknown operation string: APP_AUTO_START\n", "APP_AUTO_START") == pv.UNSUPPORTED
    dump = ("User state[\n  Bound services:{Service[label=Launcher Helper, feedbackType[FEEDBACK_GENERIC]]}\n"
            "  Enabled services:{{dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService}}\n  Crashed services:{}\n")
    assert pv.parse_bound(dump)  # pre-rename label, still on TVs with an older Helper build
    assert pv.parse_bound(dump.replace("label=Launcher Helper", "label=H02 Helper"))
    crashed = ("  Bound services:{}\n  Enabled services:{{dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService}}\n"
               "  Crashed services:{dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService}\n")
    assert not pv.parse_bound(crashed)


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
    assert lines[-1] == f"adb -s {HOST}:5555 shell pm uninstall dev.tvlauncher.helper"
    assert f"adb -s {HOST}:5555 shell pm uninstall dev.tvlauncher" in lines
    assert all(pv.undo_allowed(pv.UNDO_LINE.match(l)["cmd"]) for l in lines)
    with pytest.raises(ValueError):
        pv.undo_lines([pv.Step(("x",), "x", undo=(("rm", "-rf", "/"),))], HOST)


# ------------------------------------------------------------- end to end with a fake TV

class FakeTv:
    """Behaves like the measured TCL TV: installs reset the vendor app ops, a stopped Helper stays unbound."""

    def __init__(self, sdk=30, secure=None, installed=None, ignore_listener=False, fail_on=None, binds_after=1,
                 debuggable=True, vendor_ops=True):
        self.sdk = sdk
        self.secure = {"tv_user_setup_complete": "0", **(secure or {})}
        self.installed = set(installed or {"com.google.android.tvlauncher", "com.android.tv.settings"})
        self.disabled = set()
        self.granted = set()
        self.ops = {}
        self.home = "com.google.android.tvlauncher"
        self.ignore_listener = ignore_listener
        self.fail_on = fail_on
        self.binds_after = binds_after  # how many accessibility off/on toggles until the Helper binds (0: never)
        self.toggles = 0
        self.debuggable = debuggable
        self.vendor_ops = vendor_ops
        self.front = "com.google.android.tvlauncher"
        self.log = []
        self.prefs_file = True
        self.calls = []

    def bound(self):
        return (self.binds_after > 0 and self.toggles >= self.binds_after and self.secure.get("accessibility_enabled") == "1"
                and pv.HELPER_SERVICE in (self.secure.get("enabled_accessibility_services") or ""))

    def connect(self, host):
        pass

    def install(self, path):
        self.calls.append(("install", path))
        self.installed.update(pv.match_apks([Path(path).name]))
        for k in list(self.ops):  # TCL: every install -r switches these back to ignore
            if k[1] in pv.VENDOR_OPS:
                self.ops[k] = "ignore"
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
            if a[3] == "accessibility_enabled" and a[4] == "1" and self.secure.get(a[3]) == "0" \
                    and "enabled_accessibility_services" in self.secure:
                self.toggles += 1
            self.secure[a[3]] = a[4]
            return ""
        if a == ["dumpsys", "accessibility"]:
            if self.bound():
                return "  Bound services:{Service[label=H02 Helper]}\n  Enabled services:{}\n"
            return "  Bound services:{}\n  Crashed services:{dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService}\n"
        if a == ["date", "+%s"]:
            return "1760950000\n"
        if a[:2] == ["am", "start"]:
            self.front = "settings" if "android.settings.SETTINGS" in a else "dev.tvlauncher"
            return "Starting: Intent { act=... }\n"
        if a[:2] == ["am", "broadcast"]:
            if self.ops.get(("dev.tvlauncher", "SYSTEM_ALERT_WINDOW")) == "allow" and self.front != "dev.tvlauncher":
                self.log.append("I/PowerOn: home in front via DIRECT")
                self.front = "dev.tvlauncher"
            return "Broadcasting: Intent { ... }\nBroadcast completed: result=0\n"
        if a[0] == "logcat":
            return "".join(l + "\n" for l in self.log)
        if a[0] == "run-as":
            if not self.debuggable:
                raise AdbError("adb exited 1: run-as: package not debuggable: dev.tvlauncher")
            self.prefs_file = False
            return ""
        if a[:2] == ["am", "force-stop"]:
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
            if a[3] in pv.VENDOR_OPS and not self.vendor_ops:
                raise AdbError("adb exited 255: Error: Unknown operation string: " + a[3])
            return f"{a[3]}: {self.ops.get((a[2], a[3]), 'default')}\n"
        if a[:2] == ["appops", "set"]:
            if a[3] in pv.VENDOR_OPS and not self.vendor_ops:
                raise AdbError("adb exited 255: Error: Unknown operation string: " + a[3])
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
    return [c for c in tv.calls if c[0] in ("install", "run-as") or c[:2] in (("pm", "grant"), ("pm", "disable-user"))
            or c[:3] in (("settings", "put", "secure"), ("settings", "delete", "secure"), ("cmd", "role", "add-role-holder"))
            or c[:2] in (("appops", "set"), ("am", "start"), ("am", "broadcast"), ("am", "force-stop"))
            or c[:3] == ("cmd", "notification", "allow_listener")]


def test_dryRun_showsSelfTestWithReasons(tmp_path):
    tv = FakeTv()
    code, text = run(tmp_path, tv)
    assert code == 0
    assert "(after 15 s) adb" in text and "Self-test" in text
    assert "settings put secure accessibility_enabled 0" in text
    assert tv.log == [] and tv.prefs_file


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
    for pkg in ("dev.tvlauncher", "dev.tvlauncher.helper"):
        for op in pv.VENDOR_OPS:
            assert tv.ops[(pkg, op)] == "allow", (pkg, op)
    assert tv.ops[("dev.tvlauncher", "SYSTEM_ALERT_WINDOW")] == "allow"
    assert tv.bound()
    # Helper first, then the launcher.
    installs = [c[1] for c in tv.calls if c[0] == "install"]
    assert [Path(x).name for x in installs] == ["helper-debug.apk", "app-debug.apk"]
    # Self-test: another app in front, a 15 s pause, the simulated wake, the counters forgotten.
    assert "home in front via DIRECT" in tv.log[0] and not tv.prefs_file
    assert tv.calls.index(("am", "start", "-a", "android.settings.SETTINGS")) < tv.calls.index(
        ("am", "broadcast", "-a", pv.TCL_RESUME, "-n", pv.POWER_RECEIVER))
    assert "waiting 15 s" in text
    assert "warning" not in text, text


def test_apply_reappliesVendorOps_afterUpdateResetsThem_andRollbackRestoresThem(tmp_path):
    tv = FakeTv(installed={"com.google.android.tvlauncher", "dev.tvlauncher", "dev.tvlauncher.helper"})
    tv.ops = {("dev.tvlauncher", "APP_AUTO_START"): "default", ("dev.tvlauncher.helper", "APP_AUTO_START"): "allow",
              ("dev.tvlauncher", "SYSTEM_ALERT_WINDOW"): "ignore"}
    tv.secure.update({"enabled_accessibility_services": "com.vendor.tts/com.vendor.tts.S", "accessibility_enabled": "1"})
    code, text = run(tmp_path, tv, "--apply")
    assert code == 0, text
    assert all(tv.ops[(p, op)] == "allow" for p in ("dev.tvlauncher", "dev.tvlauncher.helper") for op in pv.VENDOR_OPS)
    assert tv.secure["enabled_accessibility_services"] == "com.vendor.tts/com.vendor.tts.S:" + pv.HELPER_SERVICE
    undo = next((tmp_path / "out").glob("provision_undo_*.sh"))
    out = []
    assert cli.main(["rollback", "--host", HOST, "--script", str(undo)], adb=tv, print_fn=out.append) == 0, out
    assert tv.ops[("dev.tvlauncher", "APP_AUTO_START")] == "default"
    assert tv.ops[("dev.tvlauncher.helper", "APP_AUTO_START")] == "allow"
    assert tv.ops[("dev.tvlauncher", "APP_ASSOC_START")] == "default"
    assert tv.ops[("dev.tvlauncher", "SYSTEM_ALERT_WINDOW")] == "ignore"
    assert tv.secure["enabled_accessibility_services"] == "com.vendor.tts/com.vendor.tts.S"
    assert tv.secure["accessibility_enabled"] == "1"


def test_apply_helperBindsOnlyOnSecondTry(tmp_path):
    tv = FakeTv(binds_after=2)
    code, text = run(tmp_path, tv, "--apply")
    assert code == 0, text
    assert "repeating the accessibility steps once" in text and tv.bound()
    assert pv.HELPER_NOT_RUNNING not in text


def test_apply_helperNeverBinds_isAWarningNotAnError(tmp_path):
    tv = FakeTv(binds_after=0)
    code, text = run(tmp_path, tv, "--apply")
    assert code == 0, text
    assert f"warning: {pv.HELPER_NOT_RUNNING}" in text
    assert tv.toggles == 2  # tried exactly twice
    assert "Provisioning complete" in text


def test_apply_tvWithoutVendorOps_andReleaseBuild_onlyWarns(tmp_path):
    tv = FakeTv(vendor_ops=False, debuggable=False)
    code, text = run(tmp_path, tv, "--apply")
    assert code == 0, text
    assert "this TV does not have this setting" in text
    assert "forgotten by itself" in text


def test_overlayNotTaking_stopsTheRun(tmp_path):
    tv = FakeTv()
    real = tv.shell

    def no_overlay(*a):
        if a[:4] == ("appops", "set", "dev.tvlauncher", "SYSTEM_ALERT_WINDOW"):
            return ""
        return real(*a)
    tv.shell = no_overlay
    code, text = run(tmp_path, tv, "--apply")
    assert code == 1 and "STOPPED" in text
    assert tv.log == []  # the self-test never ran


def test_selfTest_homeNotForward_isAWarning(tmp_path):
    tv = FakeTv()
    real = tv.shell

    def silent_broadcast(*a):
        if a[:2] == ("am", "broadcast"):
            tv.calls.append(a)
            return "Broadcast completed: result=0\n"
        return real(*a)
    tv.shell = silent_broadcast
    code, text = run(tmp_path, tv, "--apply")
    assert code == 0, text
    assert "warning: home did not come forward by itself" in text


def test_skipSelfTest(tmp_path):
    tv = FakeTv()
    code, text = run(tmp_path, tv, "--skip-self-test")
    assert code == 0 and "Self-test" not in text


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


def test_home_holder_from_dumpsys_role_when_cmd_role_lacks_get_role_holders():
    from tvtool.provision import parse_dumpsys_role_holder
    out = "  {\n      name=android.app.role.HOME\n      holders=dev.tvlauncher\n  }\n"
    assert parse_dumpsys_role_holder(out) == "dev.tvlauncher"
    assert parse_dumpsys_role_holder("name=android.app.role.DIALER\nholders=x.y\n") is None
