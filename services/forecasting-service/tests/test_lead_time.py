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


# ---------------------------------------------------------------------------
# Hierarchical lead time tests
# ---------------------------------------------------------------------------

class TestHierarchicalLeadTime:
    def _make_mv_stats(self, rows):
        return pd.DataFrame(rows) if rows else pd.DataFrame(
            columns=["item_id", "supplier_id", "n", "avg_lt", "sigma_L"]
        )

    def _make_products(self, rows):
        return pd.DataFrame(rows)

    def test_level1_preferred_supplier_item(self):
        """Level 1: preferred supplier + item with enough shipments."""
        mv = self._make_mv_stats([
            {"item_id": "A", "supplier_id": "S1", "n": 5, "avg_lt": 8.0, "sigma_L": 1.5},
        ])
        products = self._make_products([
            {"item_id": "A", "preferred_supplier_id": "S1"},
        ])
        result = compute_hierarchical_lead_time(mv, products, min_shipments=3)
        row = result.iloc[0]
        assert row["source"] == LeadTimeSource.PREFERRED_SUPPLIER_ITEM.value
        assert row["avg_lead_time"] == 8.0
        assert row["sigma_L"] == 1.5

    def test_level2_preferred_supplier_avg(self):
        """Level 2: preferred supplier average when item-specific data insufficient."""
        mv = self._make_mv_stats([
            {"item_id": "A", "supplier_id": "S1", "n": 1, "avg_lt": 8.0, "sigma_L": 1.0},
            {"item_id": "B", "supplier_id": "S1", "n": 5, "avg_lt": 10.0, "sigma_L": 2.0},
        ])
        products = self._make_products([
            {"item_id": "A", "preferred_supplier_id": "S1"},
        ])
        result = compute_hierarchical_lead_time(mv, products, min_shipments=3)
        row = result.iloc[0]
        assert row["source"] == LeadTimeSource.PREFERRED_SUPPLIER_AVG.value

    def test_level3_item_history(self):
        """Level 3: item history from any supplier when no preferred supplier."""
        mv = self._make_mv_stats([
            {"item_id": "A", "supplier_id": "S2", "n": 4, "avg_lt": 12.0, "sigma_L": 3.0},
        ])
        products = self._make_products([
            {"item_id": "A", "preferred_supplier_id": None},
        ])
        result = compute_hierarchical_lead_time(mv, products, min_shipments=3)
        row = result.iloc[0]
        assert row["source"] == LeadTimeSource.ITEM_HISTORY.value
        assert row["avg_lead_time"] == 12.0

    def test_level4_global_fallback(self):
        """Level 4: global fallback when no shipment data."""
        mv = self._make_mv_stats([])
        products = self._make_products([
            {"item_id": "A", "preferred_supplier_id": None},
        ])
        result = compute_hierarchical_lead_time(mv, products, min_shipments=3)
        row = result.iloc[0]
        assert row["source"] == LeadTimeSource.GLOBAL_FALLBACK.value
        assert row["avg_lead_time"] == config.LEAD_TIME_GLOBAL_FALLBACK_DAYS
