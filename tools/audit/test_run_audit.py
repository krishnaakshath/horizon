"""Unit tests for run_audit.py (stdlib unittest; no device needed).

    python3 -I tools/audit/test_run_audit.py      # or: cd tools/audit && python3 -m unittest
"""
import json
import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import run_audit as ra  # noqa: E402

BASE = {'package': 'dev.tvlauncher',
        'start': {'action': 'android.intent.action.MAIN', 'category': 'android.intent.category.LEANBACK_LAUNCHER'},
        'steps': []}


def scen(steps, **kw):
    d = dict(BASE, steps=steps)
    d.update(kw)
    return d


def audit_json(high=0, medium=0, low=0, msgs=()):
    fs = [{'kind': 'OVERLAP', 'severity': 'HIGH', 'a': 1, 'b': 2, 'message': m, 'rect': [0, 0, 1, 1]} for m in msgs]
    return {'meta': {'label': 'x', 'activity': 'dev.tvlauncher.HomeActivity', 'error': None},
            'summary': {'total': high + medium + low, 'high': high, 'medium': medium, 'low': low},
            'findings': fs}


class HostTest(unittest.TestCase):
    def test_valid(self):
        for h in ('192.168.1.38', 'tv', 'living-room.lan', 'a1'):
            self.assertTrue(ra.valid_host(h), h)

    def test_hostile(self):
        for h in ('', '-tv', 'tv;reboot', '$(reboot)', '`id`', '192.168.1.38:5555', 'tv ', 'tv\n', 'a b', '.tv', 'tv.',
                  'tv|x', 'tv&', '-s'):
            self.assertFalse(ra.valid_host(h), h)

    def test_main_rejects_bad_host_before_adb(self):
        calls = []
        rc = ra.main(['--host', 'tv;reboot', '--scenario', 'x.json', '--out', '/nonexistent'],
                     runner=lambda *a, **k: calls.append(a), log=lambda s: None)
        self.assertEqual(2, rc)
        self.assertEqual([], calls)


class ScenarioTest(unittest.TestCase):
    def test_parse_full(self):
        s = ra.parse_scenario(scen([
            {'key': 'DPAD_RIGHT', 'wait': 1.5, 'repeat': 3}, {'key': 'KEYCODE_BACK'}, {'audit': 'home-focus-2'},
            {'screenshot': 'home-focus-2', 'note': 'n'}, {'wait': 2}, {'dump_tree': 'tree.1'}], restart=True), 'home')
        self.assertTrue(s.restart)
        self.assertEqual('dev.tvlauncher', s.package)
        self.assertEqual([('key', 'DPAD_RIGHT', 1.5, 3), ('key', 'BACK', 0.0, 1), ('audit', 'home-focus-2', 0.0, 1),
                          ('screenshot', 'home-focus-2', 0.0, 1), ('wait', None, 2.0, 1), ('dump_tree', 'tree.1', 0.0, 1)],
                         [(x.kind, x.value, x.wait, x.repeat) for x in s.steps])

    def test_hostile_rejected(self):
        bad = [
            scen([{'key': 'DPAD_RIGHT; reboot'}]),
            scen([{'key': '$(reboot)'}]),
            scen([{'key': 'dpad_right'}]),
            scen([{'key': 'A' * 41}]),
            scen([{'key': 'POWER'}]),
            scen([{'key': 'KEYCODE_SLEEP'}]),
            scen([{'key': 'WAKEUP'}]),
            scen([{'key': 'TV_POWER'}]),
            scen([{'key': 'SETTINGS'}]),
            scen([{'key': 'VOLUME_UP'}]),
            scen([], package='com.android.tv.settings'),
            scen([], package='dev.tvlauncher.evil'),
            scen([{'key': 5}]),
            scen([{'audit': 'x; rm -rf /'}]),
            scen([{'audit': '../../etc'}]),
            scen([{'audit': '..'}]),
            scen([{'audit': '-n'}][:0] + [{'audit': ''}]),
            scen([{'audit': 'a' * 41}]),
            scen([{'screenshot': 'a b'}]),
            scen([{'audit': 'x', 'key': 'BACK'}]),
            scen([{'audit': 'x', 'shell': 'reboot'}]),
            scen([{'key': 'BACK', 'wait': 999}]),
            scen([{'key': 'BACK', 'wait': -1}]),
            scen([{'key': 'BACK', 'wait': True}]),
            scen([{'key': 'BACK', 'repeat': 0}]),
            scen([{'key': 'BACK', 'repeat': 100}]),
            scen([{'audit': 'x', 'repeat': 2}]),
            scen([{}]),
            scen(['BACK']),
            scen([], package='dev.tvlauncher; reboot'),
            scen([], package='-p'),
            scen([], start={'action': 'android.intent.action.MAIN $(x)'}),
            scen([], start={'action': 'a.b', 'extra': '--es x y'}),
            scen([], restart='yes'),
            scen([], shell='reboot'),
            scen([{'key': 'BACK'}] * (ra.MAX_STEPS + 1)),
            [],
        ]
        for b in bad:
            with self.assertRaises(ra.ScenarioError, msg=repr(b)[:200]):
                ra.parse_scenario(b)

    def test_duplicate_labels(self):
        s = ra.parse_scenario(scen([{'audit': 'a'}, {'audit': 'a'}]))
        with self.assertRaises(ra.ScenarioError):
            ra.check_unique_labels([s])
        ok = ra.parse_scenario(scen([{'audit': 'a'}, {'screenshot': 'a'}]))
        ra.check_unique_labels([ok])

    def test_shipped_scenarios_parse(self):
        d = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'scenarios')
        names = sorted(f for f in os.listdir(d) if f.endswith('.json'))
        self.assertEqual(['home-big-hero.json', 'home-grid.json', 'home-minimal.json', 'home-two-rows.json',
                          'home.json', 'panels.json', 'settings.json', 'topbar.json'], names)
        scs = [ra.load_scenario(os.path.join(d, f)) for f in names]
        ra.check_unique_labels(scs)
        for s in scs:
            self.assertTrue(any(x.kind == 'audit' for x in s.steps), s.name)
        home = names.index('home.json')
        self.assertTrue(scs[home].restart)  # home: cold start
        self.assertFalse(any(s.restart for i, s in enumerate(scs) if i != home))
        # Layout-style scenarios end by setting Single row again (Style row stepped LEFT to its start, then HOME).
        for i, s in enumerate(scs):
            if names[i].startswith('home-'):
                keys = [x.value for x in s.steps if x.kind == 'key']
                self.assertIn('HOME', keys[-3:], names[i])


class LogTest(unittest.TestCase):
    TEXT = '\n'.join([
        '10-20 10:00:00.000  1  1 I LayoutAudit: LAYOUT_AUDIT label=home findings=1 high=0 file=/a/home-1.json nonce=old',
        '10-20 10:00:01.000  1  1 I LayoutAudit: LAYOUT_AUDIT label=home-2 findings=9 high=9 file=/a/home-2-1.json nonce=n1',
        '10-20 10:00:02.000  1  1 I LayoutAudit: LAYOUT_AUDIT_TREE label=home nodes=40 file=/a/home-2-tree.json nonce=n1',
        '10-20 10:00:03.000  1  1 I LayoutAudit: LAYOUT_AUDIT label=home findings=3 high=2 file=/a/home-3.json nonce=n1',
    ])

    def test_matches_label_and_nonce_exactly(self):
        m = ra.parse_log(self.TEXT, 'home', 'n1')
        self.assertEqual(('3', '2', '/a/home-3.json'), (m['findings'], m['high'], m['file']))
        self.assertIsNone(ra.parse_log(self.TEXT, 'home', 'zzz'))
        self.assertEqual('/a/home-2-tree.json', ra.parse_log(self.TEXT, 'home', 'n1', tree=True)['file'])

    def test_error_field(self):
        m = ra.parse_log('LAYOUT_AUDIT label=x findings=0 high=0 file=- nonce=q error=no_resumed_activity', 'x', 'q')
        self.assertEqual(('-', 'no_resumed_activity'), (m['file'], m['error']))


class ReportTest(unittest.TestCase):
    def test_summarize_and_table(self):
        row = ra.summarize_audit('home', audit_json(high=2, low=1, msgs=['#1 a overlaps #2 b', 'two', 'three', 'four']))
        self.assertEqual((3, 2, 0, 1), (row['findings'], row['high'], row['medium'], row['low']))
        self.assertEqual(3, len(row['top']))
        self.assertTrue(row['top'][0].startswith('HIGH OVERLAP: #1 a overlaps'))
        missing = ra.summarize_audit('gone', None, 'timeout')
        t = ra.format_table([row, missing])
        self.assertIn('home', t)
        self.assertIn('[timeout]', t)
        self.assertIn('HIGH OVERLAP', t)
        lines = t.splitlines()
        self.assertTrue(lines[0].startswith('label'))

    def test_summarize_tolerates_garbage(self):
        for junk in ({}, {'summary': 'x', 'findings': 'y'}, {'findings': [1, None, {'severity': 'HIGH'}]}, []):
            row = ra.summarize_audit('j', junk)
            self.assertEqual('j', row['label'])

    def test_markdown_escapes_pipes(self):
        row = ra.summarize_audit('a', audit_json(high=1, msgs=['x | y']))
        md = ra.report_markdown([row], {'host': 'tv', 'scenarios': ['home'], 'started': 't', 'screenshots': ['shots/a.png']})
        self.assertIn('x \\| y', md)
        self.assertIn('| a | 1 | 1 |', md)
        self.assertIn('shots/a.png', md)

    def test_exit_code(self):
        clean = ra.summarize_audit('a', audit_json(medium=3))
        bad = ra.summarize_audit('b', audit_json(high=1))
        none = ra.summarize_audit('c', None, 'timeout')
        self.assertEqual(0, ra.exit_code([clean]))
        self.assertEqual(3, ra.exit_code([clean, none]))  # partial run is never clean
        self.assertEqual(1, ra.exit_code([clean, bad]))
        self.assertEqual(1, ra.exit_code([none, bad]))
        self.assertEqual(3, ra.exit_code([none]))
        self.assertEqual(0, ra.exit_code([]))


class FakeDevice:
    """Simulates adb (bytes in, bytes out): records argv lists, answers logcat with the last broadcast's nonce."""

    def __init__(self, data=None, respond=True, pull_ok=True, run_as_ok=True, reachable=True, line_extra=''):
        self.calls = []
        self.data = data or audit_json(high=1, msgs=['#3 TextView overlaps #4 TextView'])
        self.respond, self.pull_ok, self.run_as_ok, self.reachable = respond, pull_ok, run_as_ok, reachable
        self.line_extra = line_extra
        self.log = 'garbage \xff\xfe bytes: \u00e9\n'.encode('latin-1', errors='replace')

    def __call__(self, argv, capture_output=True, timeout=None):
        assert isinstance(argv, list) and all(isinstance(a, str) for a in argv), argv
        assert timeout is not None
        self.calls.append(argv)
        args = argv[3:] if argv[1] == '-s' else argv[1:]
        out, rc = b'', 0
        if args == ['shell', 'true']:
            rc = 0 if self.reachable else 1
        elif args[:2] == ['shell', 'am'] and args[2] == 'broadcast' and self.respond:
            label = args[args.index('label') + 1]
            nonce = args[args.index('nonce') + 1]
            line = self.line_extra.format(label=label, nonce=nonce) or (
                f'file=/storage/emulated/0/Android/data/dev.tvlauncher/files/audit/{label}-1.json '
                f'internal=/data/user/0/dev.tvlauncher/files/audit/{label}-1.json nonce={nonce}')
            self.log += f'I LayoutAudit: LAYOUT_AUDIT label={label} findings=1 high=1 {line}\n'.encode()
        elif args[:1] == ['logcat']:
            out = self.log
        elif args[:2] == ['exec-out', 'run-as']:
            if self.run_as_ok and args[3] == 'cat' and args[4].startswith('files/audit/'):
                out = json.dumps(self.data).encode()
            else:
                rc = 1
        elif args[:1] == ['pull'] and args[1].endswith('.json') and not self.pull_ok:
            rc = 1
        elif args[:1] == ['pull']:
            remote, local = args[1], args[2]
            with open(local, 'w' if remote.endswith('.json') else 'wb') as f:
                if remote.endswith('.json'):
                    json.dump(self.data, f)
                else:
                    f.write(b'\x89PNG')
        return subprocess.CompletedProcess(argv, rc, out, b'')


class DriverTest(unittest.TestCase):
    def run_main(self, scenario, dev, extra=()):
        with tempfile.TemporaryDirectory() as tmp:
            sp = os.path.join(tmp, 's.json')
            with open(sp, 'w') as f:
                json.dump(scenario, f)
            out = os.path.join(tmp, 'out')
            logs = []
            rc = ra.main(['--host', '192.168.1.38', '--scenario', sp, '--out', out, *extra],
                         runner=dev, log=logs.append, sleep=lambda s: None)
            files = sorted(os.path.relpath(os.path.join(r, n), out) for r, _, ns in os.walk(out) for n in ns) \
                if os.path.isdir(out) else []
            report = None
            if 'report.json' in files:
                with open(os.path.join(out, 'report.json')) as f:
                    report = json.load(f)
            # Second run into the same --out must refuse without --allow-overwrite.
            rc2 = ra.main(['--host', '192.168.1.38', '--scenario', sp, '--out', out], runner=dev, log=logs.append,
                          sleep=lambda s: None) if report else None
            rc3 = ra.main(['--host', '192.168.1.38', '--scenario', sp, '--out', out, '--allow-overwrite'], runner=dev,
                          log=logs.append, sleep=lambda s: None) if report else None
            return rc, files, report, logs, rc2, rc3

    def test_end_to_end_with_fake_adb(self):
        dev = FakeDevice()
        sc = scen([{'key': 'DPAD_RIGHT', 'repeat': 2}, {'audit': 'home-1'}, {'screenshot': 'home-1'}, {'key': 'MENU'}],
                  restart=True)
        rc, files, report, logs, rc2, rc3 = self.run_main(sc, dev)
        self.assertEqual(1, rc)  # one HIGH finding
        self.assertIn('audits/home-1.json', files)
        self.assertIn('shots/home-1.png', files)
        self.assertIn('report.md', files)
        self.assertEqual(1, report['audits'][0]['high'])
        self.assertEqual(2, rc2)
        self.assertEqual(1, rc3)
        shell = [c[4:] for c in dev.calls if c[1:4] == ['-s', '192.168.1.38:5555', 'shell']]
        self.assertIn(['am', 'force-stop', 'dev.tvlauncher'], shell)
        self.assertIn(['input', 'keyevent', 'KEYCODE_DPAD_RIGHT'], shell)
        self.assertIn(['input', 'keyevent', 'KEYCODE_MENU'], shell)
        self.assertIn(['rm', '-f', ra.SHOT_REMOTE], shell)
        bc = [c for c in shell if c[:2] == ['am', 'broadcast']][0]
        self.assertEqual(['-a', 'dev.tvlauncher.debug.AUDIT', '-n', 'dev.tvlauncher/dev.tvlauncher.audit.AuditReceiver',
                          '--es', 'label', 'home-1', '--es', 'nonce'], bc[2:11])
        # Only these shell verbs are ever used: no settings, no pm, no reboot.
        verbs = {tuple(c[:2]) for c in shell}
        self.assertTrue(verbs <= {('true',), ('am', 'force-stop'), ('am', 'start'), ('am', 'broadcast'),
                                  ('input', 'keyevent'), ('screencap', '-p'), ('rm', '-f')}, verbs)
        self.assertFalse(any(c[1:2] == ['exec-out'] for c in dev.calls))  # pull worked: no run-as
        self.assertTrue(any('home-1' in l for l in logs))

    def test_missing_ui_or_receiver_never_fails(self):
        dev = FakeDevice(respond=False)
        clock = iter(range(0, 1000, 5))
        ra_driver = ra.Driver(ra.Adb('tv', runner=dev, log=lambda s: None), tempfile.mkdtemp(), log=lambda s: None,
                              sleep=lambda s: None, clock=lambda: next(clock))
        ra_driver.run(ra.parse_scenario(scen([{'key': 'BACK'}, {'audit': 'a'}, {'audit': 'b'}])))
        self.assertEqual(['timeout', 'timeout'], [r['error'] for r in ra_driver.rows])
        self.assertEqual(3, ra.exit_code(ra_driver.rows))

    def test_clean_screen_exits_zero(self):
        dev = FakeDevice(data=audit_json(medium=2, low=1))
        rc, files, report, *_ = self.run_main(scen([{'audit': 'calm'}]), dev)
        self.assertEqual(0, rc)
        self.assertEqual(0, report['exit'])

    def test_bad_remote_path_not_pulled(self):
        d = ra.Driver(ra.Adb('tv', runner=FakeDevice(), log=lambda s: None), tempfile.mkdtemp(), log=lambda s: None)
        for p in ('-', '/sdcard/../../etc/x.json', 'relative.json', '/a/b.png', '/a/b;rm.json'):
            self.assertEqual((None, 'bad_path'), d.fetch_json('dev.tvlauncher', p, '/tmp/never.json'), p)

    def test_bad_scenario_exit_2(self):
        dev = FakeDevice()
        rc, files, report, logs, *_ = self.run_main(scen([{'key': 'BACK; reboot'}]), dev)
        self.assertEqual(2, rc)
        self.assertEqual([], dev.calls)


class Round1Test(unittest.TestCase):
    def driver(self, dev, **kw):
        return ra.Driver(ra.Adb('tv', runner=dev, log=lambda s: None), tempfile.mkdtemp(), log=lambda s: None,
                         sleep=lambda s: None, **kw)

    def test_pull_fails_runAs_succeeds(self):
        dev = FakeDevice(pull_ok=False)
        d = self.driver(dev)
        row = d.audit('dev.tvlauncher', 'home-1')
        self.assertEqual((1, 1, None), (row['findings'], row['high'], row['error']))
        ra_call = [c for c in dev.calls if c[3:5] == ['exec-out', 'run-as']]
        self.assertEqual([['exec-out', 'run-as', 'dev.tvlauncher', 'cat', 'files/audit/home-1-1.json']],
                         [c[3:] for c in ra_call])
        self.assertTrue(os.path.exists(os.path.join(d.out, 'audits', 'home-1.json')))

    def test_pull_and_runAs_fail(self):
        row = self.driver(FakeDevice(pull_ok=False, run_as_ok=False)).audit('dev.tvlauncher', 'h')
        self.assertEqual('pull_failed', row['error'])

    def test_fileDash_prefersAppError(self):
        dev = FakeDevice(line_extra='file=- nonce={nonce} error=no_resumed_activity')
        row = self.driver(dev).audit('dev.tvlauncher', 'h')
        self.assertEqual('no_resumed_activity', row['error'])
        self.assertIsNone(row['findings'])
        self.assertFalse(any(c[3:4] == ['pull'] for c in dev.calls))

    def test_logcat_bytes_decoded_with_replacement(self):
        adb = ra.Adb('tv', runner=lambda argv, **k: subprocess.CompletedProcess(argv, 0, b'\xff\xfeok \xe9', b''))
        rc, out = adb.cmd('logcat', '-d')
        self.assertEqual(0, rc)
        self.assertIn('ok', out)
        self.assertIn('\ufffd', out)

    def test_unreachable_exits_3_before_any_step(self):
        dev = FakeDevice(reachable=False)
        with tempfile.TemporaryDirectory() as tmp:
            sp = os.path.join(tmp, 's.json')
            with open(sp, 'w') as f:
                json.dump(scen([{'key': 'BACK'}, {'audit': 'a'}]), f)
            rc = ra.main(['--host', 'tv', '--scenario', sp, '--out', os.path.join(tmp, 'o')], runner=dev,
                         log=lambda s: None, sleep=lambda s: None)
        self.assertEqual(3, rc)
        self.assertFalse(any('keyevent' in c for c in dev.calls))

    def test_scenario_size_cap(self):
        with tempfile.TemporaryDirectory() as tmp:
            sp = os.path.join(tmp, 'big.json')
            with open(sp, 'w') as f:
                f.write(json.dumps(scen([{'key': 'BACK', 'note': 'x' * 300_000}])))
            with self.assertRaises(ra.ScenarioError):
                ra.load_scenario(sp)

    def test_time_budget_skips_rest_and_exits_3(self):
        t = [0.0]

        def clock():
            t[0] += 10
            return t[0]
        d = self.driver(FakeDevice(data=audit_json(medium=1)), clock=clock, max_seconds=35)
        d.run(ra.parse_scenario(scen([{'audit': 'a'}, {'key': 'BACK'}, {'key': 'BACK'}, {'audit': 'b'}, {'audit': 'c'}])))
        d.run(ra.parse_scenario(scen([{'audit': 'd'}])))
        self.assertEqual(['a', 'b', 'c', 'd'], [r['label'] for r in d.rows])
        self.assertIsNotNone(d.rows[0]['findings'])
        self.assertEqual(['time_budget'] * 3, [r['error'] for r in d.rows[1:]])
        self.assertEqual(3, ra.exit_code(d.rows))

    def test_max_seconds_validated(self):
        rc = ra.main(['--host', 'tv', '--scenario', 'x', '--out', 'o', '--max-seconds', '0'], runner=FakeDevice(),
                     log=lambda s: None)
        self.assertEqual(2, rc)

    def test_overwrite_protection_covers_audit_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            sp = os.path.join(tmp, 's.json')
            with open(sp, 'w') as f:
                json.dump(scen([{'audit': 'a'}]), f)
            out = os.path.join(tmp, 'o')
            os.makedirs(os.path.join(out, 'audits'))
            with open(os.path.join(out, 'audits', 'old.json'), 'w') as f:
                f.write('{}')
            args = ['--host', 'tv', '--scenario', sp, '--out', out]
            self.assertEqual(2, ra.main(args, runner=FakeDevice(), log=lambda s: None, sleep=lambda s: None))
            self.assertEqual(1, ra.main(args + ['--allow-overwrite'], runner=FakeDevice(), log=lambda s: None,
                                        sleep=lambda s: None))

    def test_allowlists(self):
        for k in sorted(ra.KEYS):
            self.assertEqual(k, ra.normalize_key('KEYCODE_' + k))
        for bad in ('POWER', 'SLEEP', 'WAKEUP', 'TV_POWER', 'SETTINGS'):
            self.assertNotIn(bad, ra.KEYS)
        for p in ra.PACKAGES:
            ra.parse_scenario(scen([], package=p))


if __name__ == '__main__':
    unittest.main()
