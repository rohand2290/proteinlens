import pytest

from proteinlens_ml.eval.complexes import complex_metrics, overlap, restrict_reference, split_reference


def fs(*x):
    return frozenset(x)


def test_overlap_identical_and_disjoint():
    assert overlap(fs("a", "b", "c"), fs("a", "b", "c")) == 1.0
    assert overlap(fs("a", "b", "c"), fs("x", "y", "z")) == 0.0
    assert overlap(fs("a", "b", "c", "d"), fs("a", "b")) == pytest.approx(4 / 8)


def test_perfect_prediction():
    ref = [fs("a", "b", "c"), fs("d", "e", "f")]
    m = complex_metrics(list(ref), ref)
    assert (m["precision"], m["recall"], m["f1"], m["mmr"]) == (1.0, 1.0, 1.0, 1.0)


def test_one_big_cluster_matches_nothing():
    ref = [fs(f"a{i}", f"b{i}", f"c{i}") for i in range(10)]
    big = frozenset(x for c in ref for x in c)
    m = complex_metrics([big], ref)
    # overlap = 9 / (30 * 3) = 0.1 < 0.25
    assert m["recall"] == 0.0 and m["precision"] == 0.0 and m["f1"] == 0.0
    assert m["mmr"] == pytest.approx(0.1 / 10)


def test_small_predictions_are_dropped_and_empty_is_safe():
    m = complex_metrics([fs("a", "b")], [fs("a", "b", "c")])
    assert m["n_pred"] == 0 and m["f1"] == 0.0


def test_mmr_is_one_to_one():
    ref = [fs("a", "b", "c"), fs("a", "b", "d")]
    m = complex_metrics([fs("a", "b", "c")], ref)
    assert m["mmr"] == pytest.approx(1.0 / 2)   # one prediction is assigned to only one complex
    assert m["recall"] == 1.0                   # both overlap >= 0.25 (second is 4/9)


def test_restrict_reference_drops_small_complexes():
    ref = {"x": fs("a", "b", "c"), "y": fs("a", "b", "z")}
    assert list(restrict_reference(ref, {"a", "b", "c"})) == ["x"]


def test_split_is_deterministic_and_disjoint():
    ref = {f"c{i}": fs("a", "b", "c") for i in range(11)}
    t1, e1 = split_reference(ref, 42)
    assert (t1, e1) == split_reference(ref, 42)
    assert not set(t1) & set(e1) and len(t1) + len(e1) == 11
