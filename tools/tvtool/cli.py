"""tvtool command line: scan | plan | apply | rollback | tune."""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime
from pathlib import Path

from .adb import Adb, AdbError, SubprocessAdb
from .planner import plan
from .profile import Profile, load_profile

PROFILES_DIR = Path(__file__).resolve().parents[2] / "profiles"
SCALE_KEYS = ("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
CHECKLIST = ["Remote control (all buttons)", "HDMI inputs", "Netflix", "YouTube",
             "Sound", "On-screen keyboard", "Play Store"]
ENABLE_RE = re.compile(r"^adb -s (?P<host>[A-Za-z0-9.\-]+):5555 shell pm enable (?P<pkg>[A-Za-z0-9_.]+)$")
PUT_RE = re.compile(r"^adb -s (?P<host>[A-Za-z0-9.\-]+):5555 shell settings put global "
                    r"(?P<key>window_animation_scale|transition_animation_scale|animator_duration_scale) "
                    r"(?P<val>[0-9]+(?:\.[0-9]+)?)$")


class Stop(Exception):
    pass


def _pkgs(out: str) -> set[str]:
    return {l[8:].strip() for l in out.splitlines() if l.startswith("package:") and l[8:].strip()}


def _meminfo(adb: Adb) -> dict[str, int]:
    res = {}
    for line in adb.shell("cat", "/proc/meminfo").splitlines():
        m = re.match(r"(MemTotal|MemAvailable):\s+(\d+)", line)
        if m:
            res[m.group(1)] = int(m.group(2))
    return res


def _identify(adb: Adb) -> dict:
    g = lambda k: adb.shell("getprop", k).strip()
    return {"manufacturer": g("ro.product.manufacturer"), "model": g("ro.product.model"),
            "fingerprint": g("ro.build.fingerprint"), "release": g("ro.build.version.release")}


def _load_profiles(path) -> list[Profile]:
    if path:
        return [load_profile(path)]
    return [load_profile(p) for p in sorted(PROFILES_DIR.glob("*.json"))]


def _match(profiles: list[Profile], info: dict) -> Profile | None:
    for p in profiles:
        if p.manufacturer == info["manufacturer"] and info["fingerprint"].startswith(p.fingerprint_prefix):
            return p
    return None


def _write_script(out_dir: Path, name: str, header: list[str], lines: list[str]) -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / name
    with open(path, "w", encoding="utf-8") as f:
        f.write("#!/bin/sh\n" + "".join(f"# {h}\n" for h in header) + "".join(l + "\n" for l in lines))
        f.flush()
        import os
        os.fsync(f.fileno())
    return path


def cmd_scan(a, adb, info, profile, say, **_):
    mem = _meminfo(adb)
    installed = sorted(_pkgs(adb.shell("pm", "list", "packages")))
    disabled = sorted(_pkgs(adb.shell("pm", "list", "packages", "-d")))
    report = {**info, "memTotalKb": mem.get("MemTotal"), "memAvailableKb": mem.get("MemAvailable"),
              "packages": installed, "disabled": disabled,
              "profileMatched": profile.model if profile else None}
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    path = out / f"scan_{info['model'] or 'unknown'}_{datetime.now():%Y%m%d-%H%M%S}.json"
    path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    say(f"{info['manufacturer']} {info['model']} Android {info['release']}: "
        f"{len(installed)} packages, {len(disabled)} disabled, "
        f"MemAvailable {mem.get('MemAvailable')} kB. Report: {path}")
    return 0


def _analyze_only(a, adb, info, say):
    installed = sorted(_pkgs(adb.shell("pm", "list", "packages")))
    say(f"Unknown TV ({info['manufacturer']} {info['model']}, {info['fingerprint']}): "
        "running in analyze-only mode. Nothing will be disabled or changed.")
    say("Proposed list (installed packages to review manually for a new profile):")
    for p in installed:
        say(f"  {p}")
    return 2


def _batches(a, adb, profile):
    tiers = {t.strip() for t in a.tiers.split(",") if t.strip()}
    installed = _pkgs(adb.shell("pm", "list", "packages"))
    already = _pkgs(adb.shell("pm", "list", "packages", "-d"))
    return plan(profile, installed - already, tiers), already


def cmd_plan(a, adb, info, profile, say, **_):
    batches, already = _batches(a, adb, profile)
    say(f"Plan for {profile.model} ({len(already)} already disabled):")
    for i, b in enumerate(batches, 1):
        say(f"  batch {i}:")
        for p in b:
            say(f"    {p}")
    if not batches:
        say("  nothing to do")
    return 0


def cmd_apply(a, adb, info, profile, say, input_fn, **_):
    batches, _already = _batches(a, adb, profile)
    flat = [p for b in batches for p in b]
    if not flat:
        say("Nothing to disable.")
        return 0
    if a.dry_run:
        say("Dry run: would disable (no changes made):")
        for p in flat:
            say(f"  {p}")
        return 0
    mem_before = _meminfo(adb).get("MemAvailable")
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    path = _write_script(Path(a.out), f"rollback_{profile.model}_{stamp}.sh",
                         [f"Rollback for {profile.model}, host {a.host}", "Re-enables every package apply will change."],
                         [f"adb -s {a.host}:5555 shell pm enable {p}" for p in flat])
    say(f"Rollback script written: {path}")
    code = 0
    try:
        for i, batch in enumerate(batches, 1):
            say(f"Batch {i}/{len(batches)}")
            for p in batch:
                out = adb.shell("pm", "disable-user", "--user", "0", p)
                if "new state: disabled-user" not in out:
                    raise Stop(f"unexpected output disabling {p}: {out.strip()!r}")
                say(f"  disabled {p}")
            say("Manual checklist - verify now:")
            for c in CHECKLIST:
                say(f"  [ ] {c}")
            if not a.yes:
                try:
                    input_fn("Press Enter to continue (Ctrl-C to stop): ")
                except EOFError:
                    raise Stop("no confirmation received")
    except (AdbError, Stop) as e:
        say(f"STOPPED: {e}. Packages already changed are covered by {path}")
        code = 1
    try:
        mem_after = _meminfo(adb).get("MemAvailable")
        if mem_before is not None and mem_after is not None:
            say(f"MemAvailable: {mem_before} kB -> {mem_after} kB (delta {mem_after - mem_before:+d} kB)")
    except AdbError:
        pass
    return code


def cmd_tune(a, adb, info, profile, say, **_):
    originals = {}
    for k in SCALE_KEYS:
        v = adb.shell("settings", "get", "global", k).strip()
        originals[k] = "1.0" if v in ("", "null") else v
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    path = _write_script(Path(a.out), f"rollback_tune_{profile.model}_{stamp}.sh",
                         [f"Restores animation scales for {profile.model}, host {a.host}"],
                         [f"adb -s {a.host}:5555 shell settings put global {k} {v}" for k, v in originals.items()])
    say(f"Rollback script written: {path}")
    try:
        for k in SCALE_KEYS:
            adb.shell("settings", "put", "global", k, a.scale_str)
            say(f"  {k}: {originals[k]} -> {a.scale_str}")
    except AdbError as e:
        say(f"STOPPED: {e}. Originals are in {path}")
        return 1
    return 0


def cmd_rollback(a, adb, say, **_):
    steps = []
    for n, raw in enumerate(Path(a.script).read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        m = ENABLE_RE.match(line)
        if m:
            steps.append((m["host"], ("pm", "enable", m["pkg"])))
            continue
        m = PUT_RE.match(line)
        if m:
            steps.append((m["host"], ("settings", "put", "global", m["key"], m["val"])))
            continue
        say(f"Refusing rollback script: unexpected line {n}: {raw!r}")
        return 1
    for h, _s in steps:
        if h != a.host:
            say(f"Refusing rollback script: it targets {h} but --host is {a.host}")
            return 1
    for _h, argv in steps:
        out = adb.shell(*argv)
        if argv[0] == "pm" and "enabled" not in out:
            say(f"STOPPED: unexpected output enabling {argv[2]}: {out.strip()!r}")
            return 1
        say(f"  ok: {' '.join(argv)}")
    say(f"Rollback complete ({len(steps)} steps).")
    return 0


def _parser():
    p = argparse.ArgumentParser(prog="tvtool")
    p.add_argument("command", choices=["scan", "plan", "apply", "rollback", "tune"])
    p.add_argument("--host", required=True)
    p.add_argument("--tiers", default="safe,optional")
    p.add_argument("--yes", action="store_true")
    p.add_argument("--scale", default="0.5")
    p.add_argument("--profile")
    p.add_argument("--out", default="./scans-out/")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--script")
    return p


def main(argv=None, adb: Adb | None = None, input_fn=input, print_fn=print) -> int:
    say = print_fn
    a = _parser().parse_args(argv)
    if a.command == "plan":
        a.dry_run = True
    if a.command == "tune":
        try:
            scale = float(a.scale)
        except ValueError:
            scale = -1.0
        if not (0 < scale <= 2):
            say(f"Invalid --scale {a.scale!r}: must satisfy 0 < scale <= 2")
            return 2
        a.scale_str = str(scale)
    if a.command == "rollback" and not a.script:
        say("rollback requires --script FILE")
        return 2
    try:
        if adb is None:
            adb = SubprocessAdb(a.host)
        adb.connect(a.host)
        if a.command == "rollback":
            return cmd_rollback(a, adb, say=say)
        info = _identify(adb)
        profile = _match(_load_profiles(a.profile), info)
        if a.command == "scan":
            return cmd_scan(a, adb, info, profile, say)
        if profile is None:
            return _analyze_only(a, adb, info, say)
        fn = {"plan": cmd_plan, "apply": cmd_apply, "tune": cmd_tune}[a.command]
        return fn(a, adb, info, profile, say, input_fn=input_fn)
    except AdbError as e:
        say(f"adb error: {e}")
        return 1
    except ValueError as e:
        say(f"error: {e}")
        return 2
