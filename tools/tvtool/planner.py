"""Builds disable batches from a profile; never-touch rules enforced here."""
from __future__ import annotations

from .profile import GLOBAL_NEVER, Profile

BATCH_SIZE = 10
TIERS = frozenset({"safe", "optional"})


def plan(profile: Profile, installed: set[str], tiers: set[str]) -> list[list[str]]:
    unknown = set(tiers) - TIERS
    if unknown:
        raise ValueError(f"unknown tier(s): {sorted(unknown)}; valid: {sorted(TIERS)}")
    candidates: list[str] = []
    if "safe" in tiers:
        candidates += profile.safe
    if "optional" in tiers:
        candidates += [o.pkg for o in profile.optional]
    blocked = GLOBAL_NEVER | set(profile.never)
    seen: set[str] = set()
    final: list[str] = []
    for pkg in candidates:
        if pkg in seen or pkg in blocked or pkg not in installed:
            continue
        seen.add(pkg)
        final.append(pkg)
    return [final[i:i + BATCH_SIZE] for i in range(0, len(final), BATCH_SIZE)]
