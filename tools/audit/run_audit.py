#!/usr/bin/env python3
"""Drive the launcher's debug-only layout auditor on a TV over adb and collect a report.

    python3 -I tools/audit/run_audit.py --host <tv-ip> \
        --scenario tools/audit/scenarios/home.json --out ./audit-out

Python 3 standard library only. Every adb call is an argv list with a timeout (never a shell
string); every value that reaches the device shell (keys, labels, package, intent names, paths)
is validated against a strict allow-list first, because `adb shell` joins its arguments into one
remote command line. The driver only presses keys, starts the scenario's package and (with
"restart": true) force-stops that same package. It never changes TV settings.

Exit codes: 1 = at least one HIGH finding; otherwise 3 = at least one audit returned no data (or the
TV is unreachable, or the time budget ran out); otherwise 0 = every audit measured, no HIGH finding.
2 = usage/scenario error (nothing is sent to the TV).
"""
from __future__ import annotations

import argparse
import json
import os
import re
import secrets
import subprocess
import sys
import time
from dataclasses import dataclass, field
from typing import Callable

# Same rule as tools/tvtool/cli.py: hostname or IPv4 only; no port, no leading '-', no metacharacters.
# All validators use fullmatch: with re.match a '$' anchor would also accept a trailing newline.
HOST_RE = re.compile(r'^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$')
# Navigation keys only: never POWER, SLEEP, WAKEUP, TV_POWER, SETTINGS or anything that changes the TV.
KEYS = frozenset({'DPAD_UP', 'DPAD_DOWN', 'DPAD_LEFT', 'DPAD_RIGHT', 'DPAD_CENTER', 'ENTER', 'BACK', 'HOME', 'MENU',
                  'SEARCH', 'PAGE_UP', 'PAGE_DOWN', 'MOVE_HOME', 'MOVE_END', 'MEDIA_PLAY_PAUSE', 'TAB'})
# The launcher and its companion packages; the only packages the driver may start or force-stop.
PACKAGES = frozenset({'dev.tvlauncher', 'dev.tvlauncher.browser', 'dev.tvlauncher.store', 'dev.tvlauncher.weather',
                      'dev.tvlauncher.helper'})
BASENAME_RE = re.compile(r'^[A-Za-z0-9_.-]{1,80}\.json$')
LABEL_RE = re.compile(r'^[A-Za-z0-9_.-]{1,40}$')
PKG_RE = re.compile(r'^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')
INTENT_RE = re.compile(r'^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$')
DEVICE_PATH_RE = re.compile(r'^/[A-Za-z0-9_./-]+\.json$')
NONCE_RE = re.compile(r'^[A-Za-z0-9]{1,16}$')
LOG_RE = re.compile(
    r'LAYOUT_AUDIT(?P<tree>_TREE)? label=(?P<label>\S+)'
    r'(?: findings=(?P<findings>\d+) high=(?P<high>\d+))?(?: nodes=(?P<nodes>\d+))?'
    r' file=(?P<file>\S+)(?: internal=(?P<internal>\S+))?(?: nonce=(?P<nonce>\S+))?(?: error=(?P<error>\S+))?')

RECEIVER = 'dev.tvlauncher.audit.AuditReceiver'
ACTION_AUDIT = 'dev.tvlauncher.debug.AUDIT'
ACTION_TREE = 'dev.tvlauncher.debug.AUDIT_DUMP_TREE'
LOG_TAG = 'LayoutAudit'
SHOT_REMOTE = '/sdcard/tvaudit-shot.png'

MAX_WAIT_S = 30.0
MAX_REPEAT = 20
MAX_STEPS = 300
AUDIT_TIMEOUT_S = 10.0
START_WAIT_S = 3.0
KEY_GAP_S = 0.4
ADB_TIMEOUT_S = 20
MAX_SCENARIO_BYTES = 256 * 1024
DEFAULT_MAX_SECONDS = 900

STEP_KINDS = ('key', 'audit', 'screenshot', 'dump_tree', 'settings')
STEP_EXTRAS = {'wait', 'repeat', 'note'}
TOP_KEYS = {'package', 'start', 'steps', 'restart', 'description'}


class ScenarioError(ValueError):
    pass


# --- Settings safety -------------------------------------------------------------------------------
# A scenario drives the owner's real TV. Settings reopens on the LAST page with other groups folded,
# so blind key presses after opening it from the top bar once landed on Volume (LEFT/RIGHT moved the
# volume) and on Routines (OK switched Bedtime on). Rules, checked when a scenario is loaded:
#  - Settings is opened only with a {"settings": "<page>"} step (HOME, then the debug OPEN_SETTINGS
#    broadcast), and only on a page from SETTINGS_LANDING: pages whose first row is navigation, never a
#    live control. Opening it from the top-bar pill (RIGHT x5+ then OK) is refused.
#  - Inside Settings, OK is accepted only on home_layout's first row (RIGHT then OK opens Layout style).
#  - On Layout style, LEFT/RIGHT may step the Style row (the first row) until UP/DOWN moves focus away;
#    after that only UP/DOWN/BACK/HOME. In any other page content, LEFT/RIGHT/OK are refused.
SETTINGS_LANDING = frozenset({'home_layout'})
SECTION_RE = re.compile(r'^[a-z][a-z0-9_]{0,39}$')
OK_KEYS = frozenset({'DPAD_CENTER', 'ENTER'})
# Pages that hold live controls (volume, sleep, power, bedtime, sound): never a landing page.
LIVE_PAGE_HINTS = ('volume', 'sound', 'audio', 'power', 'sleep', 'bedtime', 'routine', 'timer', 'reset')
OPEN_SETTINGS_ACTION = 'dev.tvlauncher.debug.OPEN_SETTINGS'
DEBUG_RECEIVER = 'dev.tvlauncher.debug.DebugReceiver'


def check_safety(steps: list) -> list[str]:
    """Problems that could change a real TV setting (empty when the scenario is safe)."""
    problems: list[str] = []
    where = 'home'        # home | sidebar | page | style | style_moved
    page = None
    prev = None           # previous key step
    keys_in_page = 0
    for i, st in enumerate(steps):
        n = f'step {i + 1}'
        if st.kind == 'settings':
            if st.value not in SETTINGS_LANDING:
                problems.append(f'{n}: settings page {st.value!r} is not a safe landing page {sorted(SETTINGS_LANDING)}')
            where, page, prev = 'sidebar', st.value, None
            continue
        if st.kind != 'key':
            continue
        k = st.value
        if k == 'HOME':
            where, page = 'home', None
        elif where == 'home':
            if k in OK_KEYS and prev is not None and prev.value == 'DPAD_RIGHT' and prev.repeat >= 5:
                problems.append(f'{n}: OK on the top-bar Settings pill; open Settings with a "settings" step')
        elif where == 'sidebar':
            if k == 'DPAD_RIGHT':
                where = 'page'
                keys_in_page = 0
            elif k in OK_KEYS:
                problems.append(f'{n}: OK in the Settings sidebar (folds/unfolds groups; focus becomes unknown)')
        elif where == 'page':
            if k in OK_KEYS:
                if page == 'home_layout' and keys_in_page == 0:
                    where = 'style'
                else:
                    problems.append(f'{n}: OK inside a Settings page (only home_layout RIGHT, OK is allowed)')
            elif k in ('DPAD_LEFT', 'DPAD_RIGHT') and keys_in_page > 0:
                problems.append(f'{n}: {k} on a Settings row could change its value')
            elif k == 'DPAD_LEFT':
                where = 'sidebar'
            keys_in_page += 1
        elif where in ('style', 'style_moved'):
            if k in OK_KEYS:
                problems.append(f'{n}: OK on the Layout style page')
            elif k in ('DPAD_UP', 'DPAD_DOWN', 'PAGE_UP', 'PAGE_DOWN'):
                where = 'style_moved'
            elif k in ('DPAD_LEFT', 'DPAD_RIGHT') and where == 'style_moved':
                problems.append(f'{n}: {k} after leaving the Style row could change another layout setting')
            elif k == 'BACK':
                where = 'page'
                keys_in_page = 1
        prev = st
    return problems


def valid_host(host: str) -> bool:
    return isinstance(host, str) and bool(HOST_RE.fullmatch(host))


def valid_label(label: object) -> bool:
    return isinstance(label, str) and bool(LABEL_RE.fullmatch(label)) and not set(label) <= {'.'}


def normalize_key(key: object) -> str:
    if not isinstance(key, str):
        raise ScenarioError(f'key must be a string, got {key!r}')
    k = key[len('KEYCODE_'):] if key.startswith('KEYCODE_') else key
    if k not in KEYS:
        raise ScenarioError(f'key {key!r} not allowed: navigation keys only ({", ".join(sorted(KEYS))})')
    return k


@dataclass
class Step:
    kind: str            # key | audit | screenshot | dump_tree | wait
    value: str | None    # key name or label
    wait: float = 0.0
    repeat: int = 1


@dataclass
class Scenario:
    name: str
    package: str
    action: str | None
    category: str | None
    restart: bool
    steps: list[Step] = field(default_factory=list)


def _num(v: object, what: str, lo: float, hi: float) -> float:
    if isinstance(v, bool) or not isinstance(v, (int, float)) or not (lo <= float(v) <= hi):
        raise ScenarioError(f'{what} must be a number in [{lo:g}, {hi:g}], got {v!r}')
    return float(v)


def parse_scenario(obj: object, name: str = 'scenario') -> Scenario:
    """Validates a scenario dict; raises ScenarioError on anything unexpected or unsafe."""
    if not isinstance(obj, dict):
        raise ScenarioError('scenario must be a JSON object')
    unknown = set(obj) - TOP_KEYS
    if unknown:
        raise ScenarioError(f'unknown top-level keys: {sorted(unknown)}')
    pkg = obj.get('package')
    if not isinstance(pkg, str) or not PKG_RE.fullmatch(pkg) or pkg not in PACKAGES:
        raise ScenarioError(f'package {pkg!r} not allowed: one of {", ".join(sorted(PACKAGES))}')
    action = category = None
    start = obj.get('start')
    if start is not None:
        if not isinstance(start, dict) or set(start) - {'action', 'category'}:
            raise ScenarioError('start must be {"action": ..., "category": ...}')
        action, category = start.get('action'), start.get('category')
        for what, v in (('start.action', action), ('start.category', category)):
            if v is not None and (not isinstance(v, str) or not INTENT_RE.fullmatch(v)):
                raise ScenarioError(f'invalid {what} {v!r}')
    restart = obj.get('restart', False)
    if not isinstance(restart, bool):
        raise ScenarioError('restart must be true or false')
    raw_steps = obj.get('steps')
    if not isinstance(raw_steps, list) or len(raw_steps) > MAX_STEPS:
        raise ScenarioError(f'steps must be a list of at most {MAX_STEPS} steps')
    steps: list[Step] = []
    for i, s in enumerate(raw_steps):
        where = f'step {i + 1}'
        if not isinstance(s, dict):
            raise ScenarioError(f'{where}: must be an object')
        kinds = [k for k in STEP_KINDS if k in s]
        unknown = set(s) - set(STEP_KINDS) - STEP_EXTRAS
        if unknown:
            raise ScenarioError(f'{where}: unknown keys {sorted(unknown)}')
        if len(kinds) > 1:
            raise ScenarioError(f'{where}: one of {STEP_KINDS} per step, got {kinds}')
        if 'note' in s and not isinstance(s['note'], str):
            raise ScenarioError(f'{where}: note must be a string')
        wait = _num(s['wait'], f'{where}: wait', 0, MAX_WAIT_S) if 'wait' in s else 0.0
        repeat = 1
        if 'repeat' in s:
            r = s['repeat']
            if isinstance(r, bool) or not isinstance(r, int) or not 1 <= r <= MAX_REPEAT:
                raise ScenarioError(f'{where}: repeat must be an integer in [1, {MAX_REPEAT}]')
            if kinds != ['key']:
                raise ScenarioError(f'{where}: repeat only applies to key steps')
            repeat = r
        if not kinds:
            if 'wait' not in s:
                raise ScenarioError(f'{where}: empty step')
            steps.append(Step('wait', None, wait))
            continue
        kind = kinds[0]
        if kind == 'key':
            steps.append(Step('key', normalize_key(s['key']), wait, repeat))
        elif kind == 'settings':
            sec = s['settings']
            if not isinstance(sec, str) or not SECTION_RE.fullmatch(sec):
                raise ScenarioError(f'{where}: invalid settings page {sec!r}')
            steps.append(Step('settings', sec, wait))
        else:
            label = s[kind]
            if not valid_label(label):
                raise ScenarioError(f'{where}: invalid {kind} label {label!r} (use [A-Za-z0-9_.-], 1-40 chars)')
            steps.append(Step(kind, label, wait))
    problems = check_safety(steps)
    if problems:
        raise ScenarioError('unsafe for a real TV: ' + '; '.join(problems))
    return Scenario(name, pkg, action, category, restart, steps)


def check_unique_labels(scenarios: list[Scenario]) -> None:
    for kind in ('audit', 'screenshot', 'dump_tree'):
        seen: set[str] = set()
        for sc in scenarios:
            for st in sc.steps:
                if st.kind == kind:
                    if st.value in seen:
                        raise ScenarioError(f'duplicate {kind} label {st.value!r} (labels name output files)')
                    seen.add(st.value)


def load_scenario(path: str) -> Scenario:
    try:
        with open(path, 'rb') as f:
            raw = f.read(MAX_SCENARIO_BYTES + 1)
        if len(raw) > MAX_SCENARIO_BYTES:
            raise ScenarioError(f'{path} is larger than {MAX_SCENARIO_BYTES // 1024} KB')
        obj = json.loads(raw.decode('utf-8'))
    except (OSError, ValueError) as e:
        raise ScenarioError(f'cannot read {path}: {e}') from e
    return parse_scenario(obj, os.path.splitext(os.path.basename(path))[0])


def parse_log(text: str, label: str, nonce: str | None, tree: bool = False) -> dict | None:
    """Finds the receiver's line for this label (and nonce, when given). Newest match wins."""
    found = None
    for line in text.splitlines():
        m = LOG_RE.search(line)
        if not m or m.group('label') != label or bool(m.group('tree')) != tree:
            continue
        if nonce is not None and m.group('nonce') != nonce:
            continue
        found = m.groupdict()
    return found


def summarize_audit(label: str, data: dict | None, error: str | None = None) -> dict:
    """Reduces one audit JSON (as written by AuditReport.toJson) to a report row."""
    row = {'label': label, 'findings': None, 'high': None, 'medium': None, 'low': None, 'top': [], 'error': error}
    if not isinstance(data, dict):
        return row
    s = data.get('summary') if isinstance(data.get('summary'), dict) else {}
    fs = data.get('findings') if isinstance(data.get('findings'), list) else []
    for k in ('high', 'medium', 'low'):
        row[k] = _int(s.get(k), sum(1 for f in fs if isinstance(f, dict) and f.get('severity') == k.upper()))
    row['findings'] = _int(s.get('total'), len(fs))
    row['top'] = [str(f.get('severity', '?')) + ' ' + str(f.get('kind', '?')) + ': ' + str(f.get('message', ''))
                  for f in fs[:3] if isinstance(f, dict)]
    meta = data.get('meta') if isinstance(data.get('meta'), dict) else {}
    if meta.get('error') and not row['error']:
        row['error'] = str(meta['error'])
    row['activity'] = meta.get('activity')
    return row


def _int(v: object, default: int) -> int:
    return v if isinstance(v, int) and not isinstance(v, bool) else default


def _cell(v: object) -> str:
    return '-' if v is None else str(v)


def format_table(rows: list[dict], width: int = 100) -> str:
    head = ['label', 'findings', 'high', 'top findings']
    lw = max([len(head[0])] + [len(r['label']) for r in rows])
    out = [f"{head[0]:<{lw}}  {head[1]:>8}  {head[2]:>4}  {head[3]}"]
    out.append('-' * min(width + lw, 160))
    for r in rows:
        tops = list(r.get('top') or [])
        if r.get('error'):
            tops = [f"[{r['error']}]"] + tops
        first = tops[0] if tops else ''
        out.append(f"{r['label']:<{lw}}  {_cell(r['findings']):>8}  {_cell(r['high']):>4}  {_clip(first, width)}")
        for t in tops[1:3]:
            out.append(f"{'':<{lw}}  {'':>8}  {'':>4}  {_clip(t, width)}")
    return '\n'.join(out)


def _clip(s: str, n: int) -> str:
    s = ' '.join(s.split())
    return s if len(s) <= n else s[: n - 1] + '…'


def _md(s: str) -> str:
    return ' '.join(str(s).split()).replace('|', '\\|')


def report_markdown(rows: list[dict], meta: dict) -> str:
    total_high = sum(r['high'] or 0 for r in rows)
    lines = [
        '# Layout audit report', '',
        f"- host: `{meta.get('host')}`",
        f"- scenarios: {', '.join(meta.get('scenarios', []))}",
        f"- started: {meta.get('started')}",
        f"- audits: {len(rows)}, with data: {sum(1 for r in rows if r['findings'] is not None)}, HIGH findings: {total_high}",
        '', '| label | findings | high | medium | low | top findings |', '|---|---:|---:|---:|---:|---|',
    ]
    for r in rows:
        tops = ([f"**{r['error']}**"] if r.get('error') else []) + [_md(t) for t in r.get('top') or []]
        lines.append(f"| {r['label']} | {_cell(r['findings'])} | {_cell(r['high'])} | {_cell(r['medium'])} | "
                     f"{_cell(r['low'])} | {'<br>'.join(tops)} |")
    shots = meta.get('screenshots') or []
    if shots:
        lines += ['', '## Screenshots', ''] + [f'- `{s}`' for s in shots]
    lines += ['', 'Full findings per audit: `audits/<label>.json`. Read `summary`, then `findings` '
              '(sorted HIGH first; `a`/`b` are node ids, `rect` is [left, top, right, bottom] in screen px).', '']
    return '\n'.join(lines)


def exit_code(rows: list[dict]) -> int:
    """1 if any HIGH; else 3 if ANY audit has no data (partial runs are never clean); else 0."""
    if any((r.get('high') or 0) > 0 for r in rows):
        return 1
    if any(r.get('findings') is None for r in rows):
        return 3
    return 0


Runner = Callable[..., subprocess.CompletedProcess]


class Adb:
    """argv-list adb wrapper bound to <host>:5555. Never raises; returns (rc, output)."""

    def __init__(self, host: str, adb: str = 'adb', runner: Runner = subprocess.run, log: Callable[[str], None] = print):
        if not valid_host(host):
            raise ValueError(f'invalid host {host!r}')
        self.serial = f'{host}:5555'
        self.adb = adb
        self.runner = runner
        self.log = log

    def _run(self, argv: list[str], timeout: float, binary: bool = False):
        """Always captures bytes; text output is decoded as UTF-8 with replacement (logcat may hold anything)."""
        try:
            r = self.runner(argv, capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            self.log(f'  ! timeout after {timeout:g}s: {" ".join(argv[3:])[:120]}')
            return 124, b'' if binary else ''
        except OSError as e:
            self.log(f'  ! cannot run adb: {e}')
            return 127, b'' if binary else ''
        out = r.stdout if r.stdout is not None else b''
        if isinstance(out, str):
            out = out.encode('utf-8', errors='replace')
        return r.returncode, out if binary else out.decode('utf-8', errors='replace')

    def connect(self) -> int:
        return self._run([self.adb, 'connect', self.serial], ADB_TIMEOUT_S)[0]

    def reachable(self) -> bool:
        """`adb connect` exits 0 even when it fails; a real shell round-trip proves the device answers."""
        return self.shell('true', timeout=15)[0] == 0

    def cmd(self, *args: str, timeout: float = ADB_TIMEOUT_S, binary: bool = False):
        return self._run([self.adb, '-s', self.serial, *args], timeout, binary)

    def shell(self, *args: str, timeout: float = ADB_TIMEOUT_S):
        return self.cmd('shell', *args, timeout=timeout)


class Driver:
    def __init__(self, adb: Adb, out_dir: str, log: Callable[[str], None] = print,
                 sleep: Callable[[float], None] = time.sleep, clock: Callable[[], float] = time.monotonic,
                 nonce: Callable[[], str] = lambda: secrets.token_hex(6), max_seconds: float = DEFAULT_MAX_SECONDS):
        self.adb, self.out, self.log, self.sleep, self.clock, self.nonce = adb, out_dir, log, sleep, clock, nonce
        self.rows: list[dict] = []
        self.screenshots: list[str] = []
        self.deadline = clock() + max_seconds
        self.out_of_time = False

    def _skip_rest(self, steps: list[Step]) -> None:
        for st in steps:
            if st.kind == 'audit':
                self.rows.append(summarize_audit(st.value, None, 'time_budget'))

    def run(self, sc: Scenario) -> None:
        if self.out_of_time or self.clock() >= self.deadline:
            self.out_of_time = True
            self._skip_rest(sc.steps)
            return
        self.log(f'== scenario {sc.name} ({sc.package})')
        if sc.restart:
            self.adb.shell('am', 'force-stop', sc.package)
        if sc.action or sc.category:
            argv = ['am', 'start']
            if sc.action:
                argv += ['-a', sc.action]
            if sc.category:
                argv += ['-c', sc.category]
            argv += ['-p', sc.package]
            rc, _ = self.adb.shell(*argv)
            if rc != 0:
                self.log(f'  ! start returned {rc}; continuing')
            self.sleep(START_WAIT_S)
        for i, st in enumerate(sc.steps):
            if self.clock() >= self.deadline:
                self.log('  ! --max-seconds reached; remaining steps skipped')
                self.out_of_time = True
                self._skip_rest(sc.steps[i:])
                return
            if st.kind == 'key':
                for i in range(st.repeat):
                    if i:
                        self.sleep(KEY_GAP_S)
                    self.adb.shell('input', 'keyevent', f'KEYCODE_{st.value}')
                self.sleep(st.wait)
                continue
            if st.kind == 'settings':
                self.open_settings(sc.package, st.value)
            elif st.kind == 'audit':
                self.rows.append(self.audit(sc.package, st.value))
            elif st.kind == 'dump_tree':
                self.audit(sc.package, st.value, tree=True)
            elif st.kind == 'screenshot':
                self.screenshot(st.value)
            self.sleep(st.wait)

    def open_settings(self, pkg: str, section: str) -> None:
        """HOME (clears a Settings left open above home), then Settings on [section] via the debug receiver."""
        if not (PKG_RE.fullmatch(pkg) and SECTION_RE.fullmatch(section) and section in SETTINGS_LANDING):
            raise ValueError('unsafe settings arguments')
        self.adb.shell('input', 'keyevent', 'KEYCODE_HOME')
        self.sleep(2.0)
        self.adb.shell('am', 'broadcast', '-a', OPEN_SETTINGS_ACTION, '-n', f'{pkg}/{DEBUG_RECEIVER}',
                       '--es', 'section', section)
        self.sleep(START_WAIT_S)

    def audit(self, pkg: str, label: str, tree: bool = False) -> dict:
        nonce = self.nonce()
        if not (NONCE_RE.fullmatch(nonce) and valid_label(label) and PKG_RE.fullmatch(pkg)):
            raise ValueError('unsafe audit arguments')
        self.adb.shell('am', 'broadcast', '-a', ACTION_TREE if tree else ACTION_AUDIT, '-n', f'{pkg}/{RECEIVER}',
                       '--es', 'label', label, '--es', 'nonce', nonce)
        deadline = self.clock() + AUDIT_TIMEOUT_S
        hit = None
        while True:
            _, out = self.adb.cmd('logcat', '-d', '-s', LOG_TAG)
            hit = parse_log(out or '', label, nonce, tree)
            if hit or self.clock() >= deadline:
                break
            self.sleep(0.5)
        if not hit:
            self.log(f'  ? {label}: no LAYOUT_AUDIT line within {AUDIT_TIMEOUT_S:g}s (debug build installed?)')
            return summarize_audit(label, None, 'timeout')
        sub = 'trees' if tree else 'audits'
        local = os.path.join(self.out, sub, f'{label}.json')
        if hit['file'] == '-':  # the app wrote nothing: its own error is the useful one
            row = summarize_audit(label, None, hit.get('error') or 'no_file')
        else:
            data, err = self.fetch_json(pkg, hit['file'], local, hit.get('internal'))
            row = summarize_audit(label, data, err or hit.get('error'))
        if not tree:
            self.log(f"  {label}: findings={_cell(row['findings'])} high={_cell(row['high'])}"
                     + (f" error={row['error']}" if row['error'] else ''))
        return row

    def fetch_json(self, pkg: str, remote: str, local: str, internal: str | None = None):
        """adb pull of the external copy; on failure `run-as <pkg> cat files/audit/<name>` (debug builds)."""
        ok_remote = bool(DEVICE_PATH_RE.fullmatch(remote)) and '..' not in remote
        ok_internal = internal is not None and bool(DEVICE_PATH_RE.fullmatch(internal)) and '..' not in internal
        if not ok_remote and not ok_internal:
            return None, 'bad_path'
        os.makedirs(os.path.dirname(local), exist_ok=True)
        if os.path.exists(local):
            os.remove(local)
        pulled = False
        if ok_remote and not remote.startswith('/data/'):
            rc, _ = self.adb.cmd('pull', remote, local)
            pulled = rc == 0 and os.path.exists(local)
        if not pulled:
            name = os.path.basename(internal if ok_internal else remote)
            if not BASENAME_RE.fullmatch(name):
                return None, 'bad_path'
            rc, blob = self.adb.cmd('exec-out', 'run-as', pkg, 'cat', f'files/audit/{name}', binary=True)
            if rc == 0 and blob:
                with open(local, 'wb') as f:
                    f.write(blob)
                pulled = True
                self.log(f'  (pull failed; read {name} via run-as)')
        if not pulled:
            return None, 'pull_failed'
        try:
            with open(local, encoding='utf-8') as f:
                return json.load(f), None
        except (OSError, ValueError):
            return None, 'bad_json'

    def screenshot(self, label: str) -> None:
        local = os.path.join(self.out, 'shots', f'{label}.png')
        os.makedirs(os.path.dirname(local), exist_ok=True)
        rc, _ = self.adb.shell('screencap', '-p', SHOT_REMOTE, timeout=30)
        if rc == 0:
            rc, _ = self.adb.cmd('pull', SHOT_REMOTE, local, timeout=60)
        self.adb.shell('rm', '-f', SHOT_REMOTE)
        if rc == 0 and os.path.exists(local):
            self.screenshots.append(os.path.relpath(local, self.out))
        else:
            self.log(f'  ? screenshot {label} failed')


def has_previous_output(out: str) -> bool:
    if os.path.exists(os.path.join(out, 'report.json')):
        return True
    d = os.path.join(out, 'audits')
    return os.path.isdir(d) and any(n.endswith('.json') for n in os.listdir(d))


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description='Run layout-audit scenarios against the debug launcher on a TV.')
    p.add_argument('--host', required=True, help='TV IPv4 address or hostname (adb over TCP, port 5555)')
    p.add_argument('--scenario', required=True, action='append', help='scenario JSON (repeatable)')
    p.add_argument('--out', required=True, help='output directory')
    p.add_argument('--allow-overwrite', action='store_true', help='reuse an --out directory that already has a report')
    p.add_argument('--adb', default='adb', help='adb binary (default: adb on PATH)')
    p.add_argument('--max-seconds', type=float, default=DEFAULT_MAX_SECONDS,
                   help=f'total time budget; remaining audits are recorded as time_budget (default {DEFAULT_MAX_SECONDS})')
    return p


def main(argv: list[str] | None = None, runner: Runner = subprocess.run, log: Callable[[str], None] = print,
         sleep: Callable[[float], None] = time.sleep) -> int:
    a = build_parser().parse_args(argv)
    if not valid_host(a.host):
        log(f"Invalid --host {a.host!r}: use an IPv4 address or hostname only (no port, no leading '-', no special characters)")
        return 2
    if a.adb.startswith('-'):
        log('Invalid --adb')
        return 2
    try:
        scenarios = [load_scenario(p) for p in a.scenario]
        check_unique_labels(scenarios)
    except ScenarioError as e:
        log(f'Scenario error: {e}')
        return 2
    if not 1 <= a.max_seconds <= 24 * 3600:
        log('Invalid --max-seconds')
        return 2
    out = os.path.abspath(a.out)
    if has_previous_output(out) and not a.allow_overwrite:
        log(f'{out} already holds audit output; pass --allow-overwrite or choose another --out')
        return 2
    os.makedirs(out, exist_ok=True)
    adb = Adb(a.host, a.adb, runner, log)
    started = time.strftime('%Y-%m-%dT%H:%M:%S')
    adb.connect()
    if not adb.reachable():
        log(f'TV {a.host}:5555 is not reachable over adb (is it on, and is network debugging allowed?)')
        return 3
    drv = Driver(adb, out, log, sleep, max_seconds=a.max_seconds)
    for sc in scenarios:
        drv.run(sc)
    meta = {'host': a.host, 'scenarios': [s.name for s in scenarios], 'started': started, 'screenshots': drv.screenshots}
    code = exit_code(drv.rows)
    with open(os.path.join(out, 'report.json'), 'w', encoding='utf-8') as f:
        json.dump({'meta': meta, 'audits': drv.rows, 'exit': code}, f, indent=2)
    with open(os.path.join(out, 'report.md'), 'w', encoding='utf-8') as f:
        f.write(report_markdown(drv.rows, meta))
    log('')
    log(format_table(drv.rows))
    log(f'\nreport: {os.path.join(out, "report.md")}  exit={code}')
    return code


if __name__ == '__main__':
    sys.exit(main())
