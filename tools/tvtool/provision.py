"""One-command setup of Horizon (H02) and its companion apps on an Android TV over adb.

Flow: probe the TV -> plan (pure, see `plan_steps`) -> print every command with a reason ->
with --apply only: write an undo script, run each step and verify it. Nothing outside
OUR_PACKAGES and the profile's stock-launcher table is ever touched.
"""
from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path

from .adb import Adb, AdbError
from .profile import PKG_RE

LAUNCHER = "dev.tvlauncher"
HELPER = "dev.tvlauncher.helper"
STORE = "dev.tvlauncher.store"
OUR_PACKAGES = (LAUNCHER, HELPER, "dev.tvlauncher.browser", STORE, "dev.tvlauncher.weather")
TITLES = {LAUNCHER: "H02 (Horizon launcher)", HELPER: "H02 Helper", "dev.tvlauncher.browser": "H02 Browser",
          STORE: "H02 Store", "dev.tvlauncher.weather": "H02 Weather"}
# File-name stems (first token, lower case) that identify each APK, e.g. app-debug.apk, helper-release.apk.
APK_STEMS = {LAUNCHER: ("app", "launcher", "tvlauncher"), HELPER: ("helper",), "dev.tvlauncher.browser": ("browser",),
             STORE: ("store", "getapps"), "dev.tvlauncher.weather": ("weather",)}

LISTENER = f"{LAUNCHER}/{LAUNCHER}.ambient.AmbientNotificationService"
HELPER_SERVICE = f"{HELPER}/{HELPER}.HelperService"
HOME_ACTIVITY = f"{LAUNCHER}/{LAUNCHER}.HomeActivity"
HOME_ROLE = "android.app.role.HOME"
WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"
RUNTIME_PERMS = ("android.permission.READ_EXTERNAL_STORAGE", "android.permission.ACCESS_FINE_LOCATION")
SECURE_KEYS = ("enabled_notification_listeners", "enabled_accessibility_services", "accessibility_enabled",
               "tv_user_setup_complete")

PROVISION_DIR = Path(__file__).resolve().parents[2] / "profiles" / "provision"
VALUE_RE = re.compile(r"^[A-Za-z0-9_.:/=-]+$")


# ------------------------------------------------------------------ profiles


@dataclass(frozen=True)
class ProvisionProfile:
    name: str
    manufacturer: str | None
    model: str | None
    fallback: bool
    stock_launchers: tuple[str, ...]
    notes: tuple[str, ...] = ()


def load_provision_profile(path) -> ProvisionProfile:
    try:
        d = json.loads(Path(path).read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        raise ValueError(f"provision profile {path}: invalid JSON: {e}") from e
    if not isinstance(d, dict) or not isinstance(d.get("name"), str) or not isinstance(d.get("match"), dict):
        raise ValueError(f"provision profile {path}: needs 'name' and 'match'")
    m = d["match"]
    stock = d.get("stock_launchers")
    if not isinstance(stock, list) or not all(isinstance(p, str) and PKG_RE.match(p) for p in stock):
        raise ValueError(f"provision profile {path}: 'stock_launchers' must be a list of package names")
    if any(p in OUR_PACKAGES for p in stock):
        raise ValueError(f"provision profile {path}: our own apps cannot be stock launchers")
    notes = d.get("notes", [])
    if not isinstance(notes, list) or not all(isinstance(n, str) for n in notes):
        raise ValueError(f"provision profile {path}: 'notes' must be a list of strings")
    fallback = m.get("fallback") is True
    if not fallback and not (isinstance(m.get("manufacturer"), str) and m["manufacturer"].strip()):
        raise ValueError(f"provision profile {path}: 'match' needs 'manufacturer' or 'fallback': true")
    return ProvisionProfile(d["name"], m.get("manufacturer"), m.get("model"), fallback, tuple(stock), tuple(notes))


def load_provision_profiles(directory: Path = PROVISION_DIR) -> list[ProvisionProfile]:
    return [load_provision_profile(p) for p in sorted(Path(directory).glob("*.json"))]


def match_provision_profile(profiles: list[ProvisionProfile], manufacturer: str, model: str) -> ProvisionProfile:
    """Model-specific profile first, then manufacturer-only, then the generic fallback."""
    man, mod = manufacturer.strip().lower(), model.strip().lower()
    exact = [p for p in profiles if not p.fallback and (p.manufacturer or "").lower() == man
             and p.model is not None and p.model.lower() == mod]
    by_maker = [p for p in profiles if not p.fallback and (p.manufacturer or "").lower() == man and p.model is None]
    fallback = [p for p in profiles if p.fallback]
    for group in (exact, by_maker, fallback):
        if group:
            return group[0]
    raise ValueError("no provisioning profile matches and there is no generic fallback profile")


# ------------------------------------------------------------------ APKs


def match_apks(filenames: list[str]) -> dict[str, str]:
    """Maps our package names to APK file names; unknown files are ignored (never installed)."""
    found: dict[str, str] = {}
    for name in sorted(filenames):
        if not name.lower().endswith(".apk"):
            continue
        stem = name[:-4]
        pkg = next((p for p in sorted(OUR_PACKAGES, key=len, reverse=True)
                    if stem == p or stem.startswith(p + "-") or stem.startswith(p + "_")), None)
        if pkg is None:
            first = re.split(r"[-_.]", stem.lower(), maxsplit=1)[0]
            pkg = next((p for p, stems in APK_STEMS.items() if first in stems), None)
        if pkg is not None and pkg not in found:
            found[pkg] = name
    return found


# ------------------------------------------------------------------ probe


@dataclass
class Probe:
    sdk: int
    manufacturer: str
    model: str
    installed: set[str]
    disabled: set[str]
    secure: dict[str, str | None]
    home_holder: str | None
    granted: set[str] = field(default_factory=set)
    usage_allowed: bool = False
    store_install_allowed: bool = False
    helper_granted: set[str] = field(default_factory=set)


def _none_if_null(v: str) -> str | None:
    v = v.strip()
    return None if v in ("", "null") else v


def parse_packages(out: str) -> set[str]:
    res = set()
    for line in out.splitlines():
        line = line.strip()
        if line.startswith("package:") and PKG_RE.match(line[8:]):
            res.add(line[8:])
    return res


def parse_granted(dumpsys: str) -> set[str]:
    """Permissions reported as granted=true in `dumpsys package <pkg>`."""
    return {m.group(1) for m in re.finditer(r"^\s*([A-Za-z0-9_.]+): granted=true", dumpsys, re.M)}


def parse_home_holder(out: str, sdk: int) -> str | None:
    if sdk >= 29:
        for line in out.splitlines():
            line = line.strip()
            if PKG_RE.match(line):
                return line
        return None
    lines = [l.strip() for l in out.splitlines() if "/" in l]
    if not lines:
        return None
    pkg = lines[-1].split("/", 1)[0]
    return pkg if PKG_RE.match(pkg) and pkg != "android" else None


def _home_query(sdk: int) -> tuple[str, ...]:
    if sdk >= 29:
        return ("cmd", "role", "get-role-holders", HOME_ROLE)
    return ("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.HOME")


def probe(adb: Adb) -> Probe:
    g = lambda k: adb.shell("getprop", k).strip()
    try:
        sdk = int(g("ro.build.version.sdk"))
    except ValueError:
        sdk = 0
    installed = parse_packages(adb.shell("pm", "list", "packages"))
    disabled = parse_packages(adb.shell("pm", "list", "packages", "-d"))
    secure = {k: _none_if_null(adb.shell("settings", "get", "secure", k)) for k in SECURE_KEYS}
    p = Probe(sdk, g("ro.product.manufacturer"), g("ro.product.model"), installed, disabled, secure,
              parse_home_holder(adb.shell(*_home_query(sdk)), sdk))
    if LAUNCHER in installed:
        p.granted = parse_granted(adb.shell("dumpsys", "package", LAUNCHER))
        p.usage_allowed = "allow" in adb.shell("appops", "get", LAUNCHER, "GET_USAGE_STATS").lower()
    if HELPER in installed:
        p.helper_granted = parse_granted(adb.shell("dumpsys", "package", HELPER))
    if STORE in installed:
        p.store_install_allowed = "allow" in adb.shell("appops", "get", STORE, "REQUEST_INSTALL_PACKAGES").lower()
    return p


# ------------------------------------------------------------------ plan


@dataclass(frozen=True)
class Step:
    """One change. `argv` runs through `adb shell` unless `apk` is set (an install from this computer)."""
    argv: tuple[str, ...]
    reason: str
    undo: tuple[tuple[str, ...], ...] = ()
    verify: str = ""          # name of the check in VERIFY
    soft: bool = False        # a failed check only warns (the TV may report it differently)
    apk: str | None = None
    takeover: bool = False

    def display(self, host: str) -> str:
        if self.apk is not None:
            return f"adb -s {host}:5555 install -r {self.apk}"
        return f"adb -s {host}:5555 shell {' '.join(self.argv)}"


def merge_services(flat: str | None, component: str) -> str:
    """Colon-separated component list with [component] added once; other entries kept as they were."""
    return _merge(flat, component)


def _entries(flat: str | None) -> list[str]:
    return [e.strip() for e in (flat or "").split(":") if e.strip() and e.strip() != "null"]


def _norm(e: str) -> str:
    pkg, _, cls = e.strip().partition("/")
    return f"{pkg}/{pkg}{cls}" if cls.startswith(".") else e.strip()


def _contains(flat: str | None, component: str) -> bool:
    return any(_norm(e) == _norm(component) for e in _entries(flat))


def _merge(flat: str | None, component: str) -> str:
    entries = _entries(flat)
    return ":".join(entries if _contains(flat, component) else entries + [component])


def _restore(key: str, previous: str | None) -> tuple[str, ...]:
    return ("settings", "put", "secure", key, previous) if previous else ("settings", "delete", "secure", key)


def plan_steps(p: Probe, apks: dict[str, str], profile: ProvisionProfile, take_over_home: bool) -> list[Step]:
    """Pure: the ordered changes for this TV. Steps already in place are left out."""
    steps: list[Step] = []
    after = set(p.installed) | set(apks)
    for pkg in OUR_PACKAGES:
        if pkg in apks:
            fresh = pkg not in p.installed
            steps.append(Step(("pm", "path", pkg), f"{'Install' if fresh else 'Update'} {TITLES[pkg]} from {apks[pkg]}.",
                              undo=((("pm", "uninstall", pkg),) if fresh else ()), verify="installed", apk=apks[pkg]))
    if LAUNCHER not in after:
        return steps
    fresh_launcher = LAUNCHER not in p.installed
    if fresh_launcher or WRITE_SECURE not in p.granted:
        steps.append(Step(("pm", "grant", LAUNCHER, WRITE_SECURE),
                          "Lets the launcher switch on Now Playing and the Helper by itself, now and after updates.",
                          undo=(("pm", "revoke", LAUNCHER, WRITE_SECURE),), verify="granted"))
    if HELPER in after and (HELPER not in p.installed or WRITE_SECURE not in p.helper_granted):
        steps.append(Step(("pm", "grant", HELPER, WRITE_SECURE),
                          "Lets the Helper app keep its own shortcuts switched on, even on TVs without an accessibility screen.",
                          undo=(("pm", "revoke", HELPER, WRITE_SECURE),), verify="granted_helper"))
    for perm in RUNTIME_PERMS:
        if fresh_launcher or perm not in p.granted:
            why = ("Lets you use your own photos as wallpapers." if perm.endswith("STORAGE")
                   else "Lets the Wi-Fi and Bluetooth panels list nearby networks and devices.")
            steps.append(Step(("pm", "grant", LAUNCHER, perm), why, undo=(("pm", "revoke", LAUNCHER, perm),),
                              verify="granted"))
    if fresh_launcher or not p.usage_allowed:
        steps.append(Step(("appops", "set", LAUNCHER, "GET_USAGE_STATS", "allow"),
                          "Lets Insights see which app is on screen (stays on the TV).",
                          undo=(("appops", "set", LAUNCHER, "GET_USAGE_STATS", "default"),), verify="usage"))
    if STORE in after and (STORE not in p.installed or not p.store_install_allowed):
        steps.append(Step(("appops", "set", STORE, "REQUEST_INSTALL_PACKAGES", "allow"),
                          "Lets H02 Store install the apps you pick (it still asks before each install).",
                          undo=(("appops", "set", STORE, "REQUEST_INSTALL_PACKAGES", "default"),), verify="store"))
    listeners = p.secure.get("enabled_notification_listeners")
    if not _contains(listeners, LISTENER):
        steps.append(Step(("cmd", "notification", "allow_listener", LISTENER),
                          "Turns on Now Playing and ambient notifications (this TV may have no screen for it).",
                          undo=(("cmd", "notification", "disallow_listener", LISTENER),), verify="listener", soft=True))
    if HELPER in after:
        services = p.secure.get("enabled_accessibility_services")
        if not _contains(services, HELPER_SERVICE):
            steps.append(Step(("settings", "put", "secure", "enabled_accessibility_services", _merge(services, HELPER_SERVICE)),
                              "Turns on the Helper's remote shortcuts; services already on stay on.",
                              undo=(_restore("enabled_accessibility_services", services),), verify="a11y"))
        if p.secure.get("accessibility_enabled") != "1":
            steps.append(Step(("settings", "put", "secure", "accessibility_enabled", "1"),
                              "Accessibility services only run while this is on.",
                              undo=(_restore("accessibility_enabled", p.secure.get("accessibility_enabled")),), verify="a11y_on"))
    if p.secure.get("tv_user_setup_complete") == "0":
        steps.append(Step(("settings", "put", "secure", "tv_user_setup_complete", "1"),
                          "This TV reports its first-time setup as unfinished, which makes the Home key do nothing.",
                          undo=(("settings", "put", "secure", "tv_user_setup_complete", "0"),), verify="tv_setup"))
    if p.home_holder != LAUNCHER:
        prev = p.home_holder if p.home_holder and p.home_holder not in OUR_PACKAGES else None
        if p.sdk >= 29:
            argv = ("cmd", "role", "add-role-holder", HOME_ROLE, LAUNCHER)
            undo = (("cmd", "role", "add-role-holder", HOME_ROLE, prev),) if prev else (("cmd", "role", "remove-role-holder", HOME_ROLE, LAUNCHER),)
        else:
            argv = ("cmd", "package", "set-home-activity", HOME_ACTIVITY)
            undo = ()
        steps.append(Step(argv, "Makes H02 the home app.", undo=undo, verify="home", soft=True))
    if take_over_home:
        for pkg in profile.stock_launchers:
            if pkg in p.installed and pkg not in p.disabled and pkg not in OUR_PACKAGES:
                steps.append(Step(("pm", "disable-user", "--user", "0", pkg),
                                  f"Turns off the stock launcher {pkg} so the Home key opens H02 (undo re-enables it).",
                                  undo=(("pm", "enable", pkg),), verify="disabled", takeover=True))
    check_scope(steps, profile)
    return steps


def check_scope(steps: list[Step], profile: ProvisionProfile) -> None:
    """Refuses any plan that would touch a package that is not ours or a listed stock launcher."""
    allowed = set(OUR_PACKAGES) | set(profile.stock_launchers)
    for s in steps:
        for argv in (s.argv, *s.undo):
            for tok in argv:
                if PKG_RE.match(tok) and tok.count(".") >= 2 and not tok.startswith("android.") and tok not in allowed:
                    raise ValueError(f"refusing plan: step would touch {tok!r}")
        if s.takeover and s.argv[-1] not in profile.stock_launchers:
            raise ValueError(f"refusing plan: {s.argv[-1]!r} is not a known stock launcher")


# ------------------------------------------------------------------ undo script


UNDO_SHAPES = [
    re.compile(r"^pm (uninstall|enable) [A-Za-z0-9_.]+$"),
    re.compile(r"^pm revoke dev\.tvlauncher(\.helper)? android\.permission\.[A-Z_]+$"),
    re.compile(r"^appops set dev\.tvlauncher(\.store)? (GET_USAGE_STATS|REQUEST_INSTALL_PACKAGES) default$"),
    re.compile(r"^cmd notification disallow_listener [A-Za-z0-9_.]+/[A-Za-z0-9_.]+$"),
    re.compile(r"^settings put secure (enabled_accessibility_services|accessibility_enabled|tv_user_setup_complete) [A-Za-z0-9_.:/=-]+$"),
    re.compile(r"^settings delete secure (enabled_accessibility_services|accessibility_enabled)$"),
    re.compile(r"^cmd role (add|remove)-role-holder android\.app\.role\.HOME [A-Za-z0-9_.]+$"),
]
UNDO_LINE = re.compile(r"^adb -s (?P<host>[A-Za-z0-9.\-]+):5555 shell (?P<cmd>.+)$")


def undo_allowed(cmd: str) -> bool:
    return any(r.match(cmd) for r in UNDO_SHAPES)


def undo_lines(steps: list[Step], host: str) -> list[str]:
    lines = []
    for s in reversed(steps):
        for argv in s.undo:
            cmd = " ".join(argv)
            if not undo_allowed(cmd) or not all(VALUE_RE.match(t) for t in argv):
                raise ValueError(f"refusing to write unsafe undo line {cmd!r}")
            lines.append(f"adb -s {host}:5555 shell {cmd}")
    return lines


def write_undo(out_dir: Path, model: str, host: str, steps: list[Step]) -> Path:
    lines = undo_lines(steps, host)
    out_dir.mkdir(parents=True, exist_ok=True)
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", model) or "tv"
    stem = f"provision_undo_{safe}_{datetime.now():%Y%m%d-%H%M%S}"
    path, n = out_dir / f"{stem}.sh", 1
    while path.exists():
        n += 1
        path = out_dir / f"{stem}_{n}.sh"
    with open(path, "x", encoding="utf-8") as f:
        f.write("#!/bin/sh\n# Undo for tvtool provision on " + host + ". Run: sh " + path.name +
                "  (or: python3 -m tvtool rollback --host " + host + " --script " + path.name + ")\n")
        f.write("".join(l + "\n" for l in lines))
        f.flush()
        os.fsync(f.fileno())
    return path


# ------------------------------------------------------------------ verify and run


def verify(kind: str, step: Step, adb: Adb, sdk: int) -> bool:
    if kind == "installed":
        return "package:" in adb.shell("pm", "path", step.argv[-1])
    if kind == "granted":
        return step.argv[-1] in parse_granted(adb.shell("dumpsys", "package", LAUNCHER))
    if kind == "granted_helper":
        return WRITE_SECURE in parse_granted(adb.shell("dumpsys", "package", HELPER))
    if kind == "usage":
        return "allow" in adb.shell("appops", "get", LAUNCHER, "GET_USAGE_STATS").lower()
    if kind == "store":
        return "allow" in adb.shell("appops", "get", STORE, "REQUEST_INSTALL_PACKAGES").lower()
    if kind == "listener":
        return _contains(_none_if_null(adb.shell("settings", "get", "secure", "enabled_notification_listeners")), LISTENER)
    if kind == "a11y":
        return _contains(_none_if_null(adb.shell("settings", "get", "secure", "enabled_accessibility_services")), HELPER_SERVICE)
    if kind == "a11y_on":
        return adb.shell("settings", "get", "secure", "accessibility_enabled").strip() == "1"
    if kind == "tv_setup":
        return adb.shell("settings", "get", "secure", "tv_user_setup_complete").strip() == "1"
    if kind == "home":
        return parse_home_holder(adb.shell(*_home_query(sdk)), sdk) == LAUNCHER
    if kind == "disabled":
        return step.argv[-1] in parse_packages(adb.shell("pm", "list", "packages", "-d"))
    raise ValueError(f"unknown check {kind!r}")


def run(a, adb: Adb, say) -> int:
    apks: dict[str, str] = {}
    apk_dir = Path(a.apks) if a.apks else None
    if apk_dir is not None:
        if not apk_dir.is_dir():
            say(f"APK folder not found: {apk_dir}")
            return 2
        apks = match_apks([p.name for p in apk_dir.iterdir() if p.is_file()])
    p = probe(adb)
    profile = match_provision_profile(load_provision_profiles(Path(a.provision_profiles) if a.provision_profiles else PROVISION_DIR),
                                      p.manufacturer, p.model)
    say(f"{p.manufacturer} {p.model} (Android SDK {p.sdk}); profile: {profile.name}")
    for n in profile.notes:
        say(f"  note: {n}")
    steps = plan_steps(p, {k: str(apk_dir / v) for k, v in apks.items()} if apk_dir else {}, profile, a.take_over_home)
    if LAUNCHER not in p.installed and LAUNCHER not in apks:
        say("H02 is not installed and no launcher APK was found; pass --apks FOLDER.")
        return 2
    if not steps:
        say("Everything is already set up. Nothing to do.")
        return 0
    say("Planned changes:")
    for i, s in enumerate(steps, 1):
        say(f"  [{i}] {s.display(a.host)}")
        say(f"      why: {s.reason}")
    if not a.take_over_home:
        stock = [x for x in profile.stock_launchers if x in p.installed and x not in p.disabled]
        if stock:
            say(f"  The stock launcher ({', '.join(stock)}) stays on; add --take-over-home to turn it off.")
    if not a.apply:
        say("Dry run: nothing was changed. Re-run with --apply to make these changes.")
        return 0
    undo = write_undo(Path(a.out), p.model, a.host, steps)
    say(f"Undo script written: {undo}")
    warnings = 0
    for i, s in enumerate(steps, 1):
        try:
            if s.takeover and f"{LAUNCHER}/" not in _resolvable_home(adb):
                say(f"  [{i}] STOPPED: H02 is not available as a home app, so the stock launcher stays on.")
                return 1
            out = adb.install(s.apk) if s.apk is not None else adb.shell(*s.argv)
            if s.apk is not None and "Success" not in out:
                raise AdbError(f"install did not report Success: {out.strip()!r}")
            ok = verify(s.verify, s, adb, p.sdk) if s.verify else True
        except AdbError as e:
            say(f"  [{i}] STOPPED: {e}. Changes so far are undone by {undo}")
            return 1
        if ok:
            say(f"  [{i}] ok")
        elif s.soft:
            warnings += 1
            say(f"  [{i}] done, but the TV does not report it yet (check on the TV)")
        else:
            say(f"  [{i}] STOPPED: the change did not take effect. Changes so far are undone by {undo}")
            return 1
    say(f"Provisioning complete ({len(steps)} steps, {warnings} to check on the TV). Undo: {undo}")
    return 0


def _resolvable_home(adb: Adb) -> str:
    """Every HOME activity the TV knows (so a disabled stock launcher never leaves it without one)."""
    return adb.shell("cmd", "package", "query-activities", "--brief", "-a", "android.intent.action.MAIN",
                     "-c", "android.intent.category.HOME")
