"""Debloat profiles and the global never-touch safety list."""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

GLOBAL_NEVER: frozenset[str] = frozenset({
    "android",
    "com.android.shell",
    "com.android.systemui",
    "com.android.tv.settings",
    "com.android.vending",
    "com.android.bluetooth",
    "com.android.externalstorage",
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.google.android.permissioncontroller",
    "com.android.providers.tv",
    "com.android.providers.settings",
    "com.android.providers.media",
    "com.android.providers.media.module",
    "com.android.providers.downloads",
    "com.google.android.tvlauncher",
    "com.google.android.gms",
    "com.google.android.gsf",
    "com.google.android.inputmethod.latin",
    "com.google.android.tv.remote.service",
    "com.google.android.webview",
    "com.tcl.android.webview",
    "com.dolby.android.audio.service",
    "com.tcl.tv",
    "com.tcl.ttvs",
    "com.tcl.tvinput",
    "com.tcl.system.server",
    "com.tcl.settings",
    "com.tcl.inputmethod.international",
    "com.tcl.tcl_bt_rcu_service",
})


@dataclass(frozen=True)
class OptionalPkg:
    pkg: str
    breaks: str


@dataclass(frozen=True)
class Profile:
    model: str
    manufacturer: str
    fingerprint_prefix: str
    safe: list[str]
    optional: list[OptionalPkg]
    never: list[str]


def _str(d: dict, key: str, where: str = "profile") -> str:
    v = d.get(key)
    if not isinstance(v, str) or not v.strip():
        raise ValueError(f"{where}: missing or invalid string field '{key}'")
    return v


def _str_list(d: dict, key: str) -> list[str]:
    v = d.get(key)
    if not isinstance(v, list) or not all(isinstance(x, str) and x for x in v):
        raise ValueError(f"profile: '{key}' must be a list of package names")
    return list(v)


def load_profile(path) -> Profile:
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        raise ValueError(f"profile {path}: invalid JSON: {e}") from e
    if not isinstance(data, dict):
        raise ValueError("profile: top level must be an object")
    model = _str(data, "model")
    match = data.get("match")
    if not isinstance(match, dict):
        raise ValueError("profile: missing or invalid object field 'match'")
    manufacturer = _str(match, "manufacturer", "match")
    prefix = _str(match, "fingerprint_prefix", "match")
    safe = _str_list(data, "safe")
    never = _str_list(data, "never")
    raw = data.get("optional")
    if not isinstance(raw, list):
        raise ValueError("profile: 'optional' must be a list")
    optional = []
    for item in raw:
        if not isinstance(item, dict):
            raise ValueError("profile: each 'optional' entry must be an object")
        optional.append(OptionalPkg(_str(item, "pkg", "optional"), _str(item, "breaks", "optional")))
    return Profile(model, manufacturer, prefix, safe, optional, never)
