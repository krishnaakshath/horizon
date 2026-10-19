import json
from pathlib import Path

import pytest

from tvtool.profile import GLOBAL_NEVER, load_profile

ROOT = Path(__file__).resolve().parents[2]
TCL = ROOT / "profiles" / "tcl-32S5403AF.json"
SCAN = ROOT.parent / "tv" / "scans" / "packages_system.txt"


def _write(tmp_path, data):
    p = tmp_path / "p.json"
    p.write_text(json.dumps(data))
    return p


def _valid():
    return {
        "model": "X",
        "match": {"manufacturer": "M", "fingerprint_prefix": "M/"},
        "safe": ["a.b"],
        "optional": [{"pkg": "c.d", "breaks": "thing"}],
        "never": ["e.f"],
    }


def test_load_profile_rejects_missing_model(tmp_path):
    d = _valid()
    del d["model"]
    with pytest.raises(ValueError, match="model"):
        load_profile(_write(tmp_path, d))


def test_load_profile_valid(tmp_path):
    p = load_profile(_write(tmp_path, _valid()))
    assert p.model == "X" and p.safe == ["a.b"] and p.never == ["e.f"]
    assert p.optional[0].pkg == "c.d" and p.optional[0].breaks == "thing"


def test_load_profile_rejects_bad_optional(tmp_path):
    d = _valid()
    d["optional"] = [{"pkg": "c.d"}]
    with pytest.raises(ValueError, match="breaks"):
        load_profile(_write(tmp_path, d))


def test_tcl_profile_loads_and_has_no_global_never_in_safe_or_optional():
    p = load_profile(TCL)
    assert p.model == "32S5403AF"
    bad = (set(p.safe) | {o.pkg for o in p.optional}) & GLOBAL_NEVER
    assert not bad
    assert "com.google.android.katniss" not in p.safe


def test_tcl_profile_safe_packages_all_exist_in_scan():
    if not SCAN.exists():
        pytest.skip(f"scan file absent: {SCAN}")
    files = [SCAN, SCAN.with_name("packages_user.txt")]
    installed = {
        l.strip().removeprefix("package:")
        for f in files if f.exists()
        for l in f.read_text().splitlines()
    }
    p = load_profile(TCL)
    missing = [x for x in p.safe + [o.pkg for o in p.optional] if x not in installed]
    assert not missing
