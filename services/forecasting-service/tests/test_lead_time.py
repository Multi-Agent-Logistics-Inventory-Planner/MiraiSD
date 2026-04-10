import pandas as pd
import numpy as np

from src.lead_time import compute_lead_time_stats, compute_hierarchical_lead_time, LeadTimeSource
from src import config


def _make_shipment_df(data: list[dict]) -> pd.DataFrame:
    if not data:
        return pd.DataFrame(columns=["item_id", "lead_time_days"])
    return pd.DataFrame(data)


def _make_fallback_df(data: list[dict]) -> pd.DataFrame:
    return pd.DataFrame(data)


def test_multiple_shipments_mean_and_std():
    """Multiple shipments: verify mean and std are computed correctly."""
    shipments = _make_shipment_df([
        {"item_id": "A", "lead_time_days": 10},
        {"item_id": "A", "lead_time_days": 14},
        {"item_id": "A", "lead_time_days": 12},
    ])
    fallback = _make_fallback_df([{"item_id": "A", "lead_time_days": 7}])

    result = compute_lead_time_stats(shipments, fallback, min_shipments=2)

    assert len(result) == 1
    row = result.iloc[0]
    assert row["item_id"] == "A"
    assert abs(row["avg_lead_time"] - 12.0) < 1e-9
    # std(ddof=1) of [10, 14, 12] = 2.0
    assert abs(row["sigma_L"] - 2.0) < 1e-9
    assert row["shipment_count"] == 3
    assert row["source"] == "shipment_history"


def test_single_shipment_uses_default_sigma():
    """Single shipment: sigma_L = default, mean = single value."""
    shipments = _make_shipment_df([
        {"item_id": "A", "lead_time_days": 10},
    ])
    fallback = _make_fallback_df([{"item_id": "A", "lead_time_days": 7}])

    result = compute_lead_time_stats(shipments, fallback, min_shipments=2)

    row = result.iloc[0]
    assert row["avg_lead_time"] == 10.0
    assert row["sigma_L"] == config.LEAD_TIME_STD_DEFAULT_DAYS
    assert row["shipment_count"] == 1
    assert row["source"] == "shipment_history"


def test_no_shipments_falls_back_to_product_default():
    """No shipment data: fallback to products.lead_time_days."""
    shipments = _make_shipment_df([])
    fallback = _make_fallback_df([
        {"item_id": "A", "lead_time_days": 14},
        {"item_id": "B", "lead_time_days": 21},
    ])

    result = compute_lead_time_stats(shipments, fallback)

    assert len(result) == 2
    a_row = result[result["item_id"] == "A"].iloc[0]
    b_row = result[result["item_id"] == "B"].iloc[0]
    assert a_row["avg_lead_time"] == 14.0
    assert b_row["avg_lead_time"] == 21.0
    assert a_row["source"] == "product_default"
    assert b_row["source"] == "product_default"
    assert a_row["shipment_count"] == 0


def test_negative_lead_times_filtered():
    """Negative lead times should be filtered out."""
    shipments = _make_shipment_df([
        {"item_id": "A", "lead_time_days": -5},
        {"item_id": "A", "lead_time_days": 0},
        {"item_id": "A", "lead_time_days": 10},
        {"item_id": "A", "lead_time_days": 12},
    ])
    fallback = _make_fallback_df([{"item_id": "A", "lead_time_days": 7}])

    result = compute_lead_time_stats(shipments, fallback, min_shipments=2)

    row = result.iloc[0]
    # Only 10 and 12 should remain
    assert abs(row["avg_lead_time"] - 11.0) < 1e-9
    assert row["shipment_count"] == 2


def test_mixed_items_some_with_shipments():
    """Some items have shipments, others fall back to product default."""
    shipments = _make_shipment_df([
        {"item_id": "A", "lead_time_days": 10},
        {"item_id": "A", "lead_time_days": 14},
    ])
    fallback = _make_fallback_df([
        {"item_id": "A", "lead_time_days": 7},
        {"item_id": "B", "lead_time_days": 21},
    ])

    result = compute_lead_time_stats(shipments, fallback, min_shipments=2)

    assert len(result) == 2
    a_row = result[result["item_id"] == "A"].iloc[0]
    b_row = result[result["item_id"] == "B"].iloc[0]
    assert a_row["source"] == "shipment_history"
    assert b_row["source"] == "product_default"
    assert b_row["avg_lead_time"] == 21.0


def test_empty_inputs():
    """Both inputs empty should return empty DataFrame with correct columns."""
    shipments = _make_shipment_df([])
    fallback = _make_fallback_df([])
    fallback = pd.DataFrame(columns=["item_id", "lead_time_days"])

    result = compute_lead_time_stats(shipments, fallback)

    assert result.empty
    assert set(result.columns) == {"item_id", "avg_lead_time", "sigma_L", "shipment_count", "source"}


# --- Tests for compute_hierarchical_lead_time ---

def _make_mv_df(data: list[dict]) -> pd.DataFrame:
    """Create MV stats DataFrame."""
    if not data:
        return pd.DataFrame(columns=["item_id", "supplier_id", "n", "avg_lt", "sigma_L"])
    return pd.DataFrame(data)


def _make_products_df(data: list[dict]) -> pd.DataFrame:
    """Create products DataFrame with preferred_supplier_id."""
    return pd.DataFrame(data)


def test_hierarchical_level1_preferred_supplier_item():
    """Level 1: Preferred Supplier + Item with 3+ shipments."""
    mv = _make_mv_df([
        {"item_id": "A", "supplier_id": "S1", "n": 5, "avg_lt": 10.0, "sigma_L": 1.5},
        {"item_id": "A", "supplier_id": "S2", "n": 3, "avg_lt": 15.0, "sigma_L": 2.0},
    ])
    products = _make_products_df([
        {"item_id": "A", "preferred_supplier_id": "S1"},
    ])

    result = compute_hierarchical_lead_time(mv, products, min_shipments=3)

    assert len(result) == 1
    row = result.iloc[0]
    assert row["item_id"] == "A"
    assert row["avg_lead_time"] == 10.0
    assert row["sigma_L"] == 1.5
    assert row["shipment_count"] == 5
    assert row["source"] == LeadTimeSource.PREFERRED_SUPPLIER_ITEM.value


def test_hierarchical_level2_preferred_supplier_avg():
    """Level 2: Preferred Supplier average when item has <3 from preferred, but supplier has 3+ total."""
    mv = _make_mv_df([
        {"item_id": "A", "supplier_id": "S1", "n": 2, "avg_lt": 10.0, "sigma_L": 1.0},  # <3 for item A
        {"item_id": "B", "supplier_id": "S1", "n": 5, "avg_lt": 12.0, "sigma_L": 2.0},  # S1 has more from B
    ])
    products = _make_products_df([
        {"item_id": "A", "preferred_supplier_id": "S1"},
    ])

    result = compute_hierarchical_lead_time(mv, products, min_shipments=3)

    assert len(result) == 1
    row = result.iloc[0]
    assert row["item_id"] == "A"
    # Weighted avg: only B qualifies (5 shipments), so avg = 12.0
    assert row["avg_lead_time"] == 12.0
    assert row["source"] == LeadTimeSource.PREFERRED_SUPPLIER_AVG.value


def test_hierarchical_level3_item_history():
    """Level 3: Item history across any supplier when no preferred supplier."""
    mv = _make_mv_df([
        {"item_id": "A", "supplier_id": "S1", "n": 2, "avg_lt": 10.0, "sigma_L": 1.0},
        {"item_id": "A", "supplier_id": "S2", "n": 4, "avg_lt": 14.0, "sigma_L": 2.0},
    ])
    products = _make_products_df([
        {"item_id": "A", "preferred_supplier_id": None},
    ])

    result = compute_hierarchical_lead_time(mv, products, min_shipments=3)

    assert len(result) == 1
    row = result.iloc[0]
    assert row["item_id"] == "A"
    # Only S2 qualifies (4 >= 3), so avg = 14.0
    assert row["avg_lead_time"] == 14.0
    assert row["source"] == LeadTimeSource.ITEM_HISTORY.value


def test_hierarchical_level4_global_fallback():
    """Level 4: Global fallback when no sufficient history exists."""
    mv = _make_mv_df([
        {"item_id": "A", "supplier_id": "S1", "n": 1, "avg_lt": 10.0, "sigma_L": 1.0},  # <3
    ])
    products = _make_products_df([
        {"item_id": "A", "preferred_supplier_id": None},
    ])

    result = compute_hierarchical_lead_time(mv, products, min_shipments=3)

    assert len(result) == 1
    row = result.iloc[0]
    assert row["item_id"] == "A"
    assert row["avg_lead_time"] == config.LEAD_TIME_GLOBAL_FALLBACK_DAYS
    assert row["sigma_L"] == config.LEAD_TIME_STD_DEFAULT_DAYS
    assert row["shipment_count"] == 0
    assert row["source"] == LeadTimeSource.GLOBAL_FALLBACK.value


def test_hierarchical_empty_mv():
    """Empty MV stats: all items get global fallback."""
    mv = _make_mv_df([])
    products = _make_products_df([
        {"item_id": "A", "preferred_supplier_id": "S1"},
        {"item_id": "B", "preferred_supplier_id": None},
    ])

    result = compute_hierarchical_lead_time(mv, products)

    assert len(result) == 2
    for _, row in result.iterrows():
        assert row["avg_lead_time"] == config.LEAD_TIME_GLOBAL_FALLBACK_DAYS
        assert row["sigma_L"] == config.LEAD_TIME_STD_DEFAULT_DAYS
        assert row["source"] == LeadTimeSource.GLOBAL_FALLBACK.value


def test_hierarchical_weighted_average_level3():
    """Level 3: Weighted average across multiple suppliers for same item."""
    mv = _make_mv_df([
        {"item_id": "A", "supplier_id": "S1", "n": 3, "avg_lt": 10.0, "sigma_L": 1.0},
        {"item_id": "A", "supplier_id": "S2", "n": 6, "avg_lt": 16.0, "sigma_L": 2.0},
    ])
    products = _make_products_df([
        {"item_id": "A", "preferred_supplier_id": None},
    ])

    result = compute_hierarchical_lead_time(mv, products, min_shipments=3)

    assert len(result) == 1
    row = result.iloc[0]
    # Weighted avg: (3*10 + 6*16) / (3+6) = (30 + 96) / 9 = 14.0
    expected_avg = (3 * 10.0 + 6 * 16.0) / 9
    assert abs(row["avg_lead_time"] - expected_avg) < 1e-9
    assert row["shipment_count"] == 9
    assert row["source"] == LeadTimeSource.ITEM_HISTORY.value


def test_hierarchical_preserves_preferred_supplier_id():
    """Result includes preferred_supplier_id from products."""
    mv = _make_mv_df([
        {"item_id": "A", "supplier_id": "S1", "n": 5, "avg_lt": 10.0, "sigma_L": 1.5},
    ])
    products = _make_products_df([
        {"item_id": "A", "preferred_supplier_id": "S1"},
    ])

    result = compute_hierarchical_lead_time(mv, products, min_shipments=3)

    assert result.iloc[0]["preferred_supplier_id"] == "S1"


def test_hierarchical_fallback_through_levels():
    """Test item that falls through L1 -> L2 -> L3 -> L4."""
    # Item A: no preferred supplier, no history -> L4
    # Item B: preferred S1 with 3+ from S1+B -> L1
    # Item C: preferred S1, but <3 from S1+C, S1 has 3+ total -> L2
    # Item D: no preferred, 3+ from any supplier -> L3
    mv = _make_mv_df([
        {"item_id": "B", "supplier_id": "S1", "n": 4, "avg_lt": 8.0, "sigma_L": 1.0},
        {"item_id": "C", "supplier_id": "S1", "n": 2, "avg_lt": 10.0, "sigma_L": 1.0},
        {"item_id": "D", "supplier_id": "S2", "n": 5, "avg_lt": 12.0, "sigma_L": 1.5},
    ])
    products = _make_products_df([
        {"item_id": "A", "preferred_supplier_id": None},
        {"item_id": "B", "preferred_supplier_id": "S1"},
        {"item_id": "C", "preferred_supplier_id": "S1"},
        {"item_id": "D", "preferred_supplier_id": None},
    ])

    result = compute_hierarchical_lead_time(mv, products, min_shipments=3)
    result = result.set_index("item_id")

    assert result.loc["A", "source"] == LeadTimeSource.GLOBAL_FALLBACK.value
    assert result.loc["B", "source"] == LeadTimeSource.PREFERRED_SUPPLIER_ITEM.value
    # C: S1 has 4 shipments from B (>=3), so L2 applies
    assert result.loc["C", "source"] == LeadTimeSource.PREFERRED_SUPPLIER_AVG.value
    assert result.loc["D", "source"] == LeadTimeSource.ITEM_HISTORY.value
