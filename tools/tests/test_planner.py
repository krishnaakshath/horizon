import pytest

from tvtool.planner import plan
from tvtool.profile import GLOBAL_NEVER, OptionalPkg, Profile


def prof(safe=(), optional=(), never=()):
    return Profile("m", "M", "M/", list(safe), [OptionalPkg(p, "x") for p in optional], list(never))


def test_plan_skips_packages_not_installed():
    p = prof(safe=["a", "b"])
    assert plan(p, {"a"}, {"safe"}) == [["a"]]


def test_plan_never_removes_global_never_even_if_profile_lists_it():
    p = prof(safe=["com.google.android.gms", "a"], optional=["com.android.vending"])
    out = plan(p, {"com.google.android.gms", "a", "com.android.vending"}, {"safe", "optional"})
    assert out == [["a"]]
    assert "com.google.android.gms" in GLOBAL_NEVER


def test_plan_profile_never_excluded():
    p = prof(safe=["a", "b"], never=["b"])
    assert plan(p, {"a", "b"}, {"safe"}) == [["a"]]


def test_plan_batches_at_most_ten():
    pk = [f"p{i}" for i in range(23)]
    out = plan(prof(safe=pk), set(pk), {"safe"})
    assert [len(b) for b in out] == [10, 10, 3]
    assert [x for b in out for x in b] == pk


def test_plan_optional_excluded_unless_requested():
    p = prof(safe=["a"], optional=["o"])
    assert plan(p, {"a", "o"}, {"safe"}) == [["a"]]
    assert plan(p, {"a", "o"}, {"optional"}) == [["o"]]
    assert plan(p, {"a", "o"}, {"safe", "optional"}) == [["a", "o"]]


def test_plan_dedupes_preserving_order():
    p = prof(safe=["a", "b", "a"], optional=["b", "c"])
    assert plan(p, {"a", "b", "c"}, {"safe", "optional"}) == [["a", "b", "c"]]


def test_plan_unknown_tier_raises():
    with pytest.raises(ValueError):
        plan(prof(safe=["a"]), {"a"}, {"risky"})


def test_plan_empty_returns_no_batches():
    assert plan(prof(safe=["a"]), set(), {"safe"}) == []
