"""Tests for the walk-forward backtester module."""

import sys
from unittest.mock import MagicMock

import numpy as np
import pandas as pd
import pytest

# Mock kafka (not installed in test env)
sys.modules["kafka"] = MagicMock()
sys.modules["kafka.errors"] = MagicMock()

# Patch pydantic v1 -> v2 compatibility (field_validator not in pydantic v1)
import pydantic
if not hasattr(pydantic, "field_validator"):
    pydantic.field_validator = lambda *a, **kw: lambda f: f

from src.application.backtester import _compute_method_summary


class TestComputeMethodSummary:
    def _make_predictions(self, records: list[dict]) -> pd.DataFrame:
        return pd.DataFrame(records)

    def test_perfect_predictions(self):
        """Perfect predictions should yield MAE=0, bias=0, within_1=1.0."""
        preds = self._make_predictions([
            {"method": "dow_weighted", "predicted_mu": 5.0, "actual_mu_corrected": 5.0},
            {"method": "dow_weighted", "predicted_mu": 3.0, "actual_mu_corrected": 3.0},
        ])
        result = _compute_method_summary(preds)

        assert len(result) == 1
        s = result[0]
        assert s["method"] == "dow_weighted"
        assert s["mae"] == 0.0
        assert s["bias"] == 0.0
        assert s["within_1_unit"] == 1.0
        assert s["within_2_units"] == 1.0

    def test_known_errors(self):
        """Verify MAE and bias with known error values."""
        preds = self._make_predictions([
            {"method": "dow_weighted", "predicted_mu": 6.0, "actual_mu_corrected": 4.0},
            {"method": "dow_weighted", "predicted_mu": 3.0, "actual_mu_corrected": 5.0},
        ])
        result = _compute_method_summary(preds)

        s = result[0]
        # errors: +2.0, -2.0
        assert s["mae"] == 2.0
        assert s["bias"] == 0.0  # symmetric errors cancel
        assert s["within_1_unit"] == 0.0
        assert s["within_2_units"] == 1.0

    def test_zero_actual_excluded(self):
        """Predictions against zero actual demand should be excluded."""
        preds = self._make_predictions([
            {"method": "dow_weighted", "predicted_mu": 5.0, "actual_mu_corrected": 0.0},
            {"method": "dow_weighted", "predicted_mu": 5.0, "actual_mu_corrected": 5.0},
        ])
        result = _compute_method_summary(preds)

        s = result[0]
        assert s["n_predictions"] == 1  # only the non-zero row

    def test_multiple_methods_sorted_by_mae(self):
        """Multiple methods should each get independent summaries, sorted by MAE."""
        preds = self._make_predictions([
            {"method": "dow_weighted", "predicted_mu": 6.0, "actual_mu_corrected": 5.0},
            {"method": "ma14", "predicted_mu": 5.0, "actual_mu_corrected": 5.0},
        ])
        result = _compute_method_summary(preds)

        assert len(result) == 2
        # ma14 has MAE=0, should be first
        assert result[0]["method"] == "ma14"
        assert result[1]["method"] == "dow_weighted"

    def test_rmse_greater_than_mae_with_outliers(self):
        """RMSE should be >= MAE, with larger gap when errors vary."""
        preds = self._make_predictions([
            {"method": "dow_weighted", "predicted_mu": 5.0, "actual_mu_corrected": 5.0},
            {"method": "dow_weighted", "predicted_mu": 5.0, "actual_mu_corrected": 5.0},
            {"method": "dow_weighted", "predicted_mu": 15.0, "actual_mu_corrected": 5.0},
        ])
        result = _compute_method_summary(preds)

        s = result[0]
        assert s["rmse"] >= s["mae"]

    def test_empty_predictions(self):
        """Empty predictions should return empty list."""
        preds = pd.DataFrame(columns=[
            "method", "predicted_mu", "actual_mu_corrected"
        ])
        result = _compute_method_summary(preds)
        assert result == []
