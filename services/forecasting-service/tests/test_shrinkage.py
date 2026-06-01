"""Tests for hierarchical shrinkage of mu_hat toward the category prior."""
import pandas as pd
import pytest

from src import config
from src.forecast import apply_shrinkage


def _est_row(item_id: str, mu: float, n: int) -> dict:
    return {"item_id": item_id, "mu_hat": mu, "n_observed_days": n}


def test_high_n_item_essentially_unchanged():
    """n=80 with k=10 -> weight on item = 80/90 = 0.889."""
    estimates = pd.DataFrame([
        _est_row("A", mu=5.0, n=80),
        _est_row("B", mu=1.0, n=80),
        _est_row("C", mu=3.0, n=80),
        _est_row("D", mu=2.0, n=80),
        _est_row("E", mu=4.0, n=80),
    ])
    category_map = {iid: "toys" for iid in ["A", "B", "C", "D", "E"]}

    result = apply_shrinkage(estimates, category_map, strength=10.0, min_category_items=5)

    cat_mean = (5.0 + 1.0 + 3.0 + 2.0 + 4.0) / 5  # 3.0
    expected_A = (80 * 5.0 + 10 * cat_mean) / 90  # ~4.778
    a = result.loc[result["item_id"] == "A", "mu_hat"].iloc[0]
    assert a == pytest.approx(expected_A, abs=1e-6)


def test_low_n_item_pulled_hard_toward_category():
    """n=2 with k=10 -> weight on category = 10/12 = 0.833."""
    estimates = pd.DataFrame([
        _est_row("A", mu=10.0, n=2),  # noisy outlier
        _est_row("B", mu=1.0, n=80),
        _est_row("C", mu=1.2, n=80),
        _est_row("D", mu=0.9, n=80),
        _est_row("E", mu=1.1, n=80),
    ])
    category_map = {iid: "toys" for iid in ["A", "B", "C", "D", "E"]}

    result = apply_shrinkage(estimates, category_map, strength=10.0, min_category_items=5)

    cat_mean = (10.0 + 1.0 + 1.2 + 0.9 + 1.1) / 5  # 2.84
    expected_A = (2 * 10.0 + 10 * cat_mean) / 12
    a = result.loc[result["item_id"] == "A", "mu_hat"].iloc[0]
    assert a == pytest.approx(expected_A, abs=1e-6)
    # A's mu pulled noticeably toward the prior.
    assert a < 5.0


def test_tiny_category_skipped():
    """Category with <min_category_items SKUs -> no shrinkage."""
    estimates = pd.DataFrame([
        _est_row("A", mu=10.0, n=2),
        _est_row("B", mu=1.0, n=80),
    ])
    category_map = {"A": "rare", "B": "rare"}

    result = apply_shrinkage(estimates, category_map, strength=10.0, min_category_items=5)
    a = result.loc[result["item_id"] == "A", "mu_hat"].iloc[0]
    assert a == pytest.approx(10.0)


def test_uncategorized_bucket_skipped():
    """Items in the (uncategorized)/Unknown bucket -> no shrinkage."""
    estimates = pd.DataFrame([
        _est_row(f"X{i}", mu=10.0 + i, n=2) for i in range(10)
    ])
    category_map = {f"X{i}": "Unknown" for i in range(10)}

    result = apply_shrinkage(estimates, category_map, strength=10.0, min_category_items=5)
    # Every item keeps its original mu.
    for i in range(10):
        v = result.loc[result["item_id"] == f"X{i}", "mu_hat"].iloc[0]
        assert v == pytest.approx(10.0 + i)


def test_n_zero_item_not_shrunk():
    """Cold-start items (n=0) already replaced by category fallback; shrinkage
    should not double-correct."""
    estimates = pd.DataFrame([
        _est_row("A", mu=3.0, n=0),   # already cat-filled value
        _est_row("B", mu=2.0, n=50),
        _est_row("C", mu=4.0, n=50),
        _est_row("D", mu=3.0, n=50),
        _est_row("E", mu=3.5, n=50),
    ])
    category_map = {iid: "toys" for iid in ["A", "B", "C", "D", "E"]}

    result = apply_shrinkage(estimates, category_map, strength=10.0, min_category_items=5)
    a = result.loc[result["item_id"] == "A", "mu_hat"].iloc[0]
    assert a == pytest.approx(3.0)


def test_pre_shrinkage_column_preserved():
    """Caller (drawer) reads mu_hat_pre_shrinkage to show the move."""
    estimates = pd.DataFrame([
        _est_row("A", mu=10.0, n=2),
        _est_row("B", mu=1.0, n=80),
        _est_row("C", mu=1.0, n=80),
        _est_row("D", mu=1.0, n=80),
        _est_row("E", mu=1.0, n=80),
    ])
    category_map = {iid: "toys" for iid in ["A", "B", "C", "D", "E"]}

    result = apply_shrinkage(estimates, category_map, strength=10.0, min_category_items=5)
    a_pre = result.loc[result["item_id"] == "A", "mu_hat_pre_shrinkage"].iloc[0]
    a_post = result.loc[result["item_id"] == "A", "mu_hat"].iloc[0]
    assert a_pre == pytest.approx(10.0)
    assert a_post < a_pre
