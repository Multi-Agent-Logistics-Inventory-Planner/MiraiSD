import numpy as np
import pandas as pd

from src.backtest import apply_residual_bias_correction


def _series(values):
    return pd.Series(values, dtype=float)


def test_empty_input_returns_empty():
    result = apply_residual_bias_correction(
        mu_hat=_series([]),
        residual_bias=_series([]),
        backtest_days=_series([]),
        cap_fraction=0.5,
        min_backtest_days=7,
        mu_floor=0.1,
    )
    assert result.empty
    assert list(result.columns) == [
        "mu_hat_corrected",
        "correction_applied",
        "mu_hat_pre_correction",
    ]


def test_skipped_when_backtest_days_below_min():
    """Items with too few backtest days pass through uncorrected."""
    result = apply_residual_bias_correction(
        mu_hat=_series([2.0, 3.0]),
        residual_bias=_series([0.5, -0.5]),
        backtest_days=_series([3.0, 6.0]),
        cap_fraction=0.5,
        min_backtest_days=7,
        mu_floor=0.1,
    )
    assert result["correction_applied"].tolist() == [0.0, 0.0]
    assert result["mu_hat_corrected"].tolist() == [2.0, 3.0]
    assert result["mu_hat_pre_correction"].tolist() == [2.0, 3.0]


def test_positive_bias_clipped_to_upper_cap():
    """Bias way above +cap*mu_hat clips to +cap*mu_hat -> mu_hat shrinks by cap."""
    # mu_hat=4, bias=+10 (huge over-prediction). cap=0.5 -> max correction = +2.
    # Corrected = 4 - 2 = 2.
    result = apply_residual_bias_correction(
        mu_hat=_series([4.0]),
        residual_bias=_series([10.0]),
        backtest_days=_series([14.0]),
        cap_fraction=0.5,
        min_backtest_days=7,
        mu_floor=0.1,
    )
    assert result["correction_applied"].iloc[0] == 2.0
    assert result["mu_hat_corrected"].iloc[0] == 2.0


def test_negative_bias_clipped_to_lower_cap_and_floored():
    """Bias way below -cap*mu_hat clips to -cap*mu_hat. Floor enforced."""
    # mu_hat=0.05 (below floor 0.1), bias=-100. cap=0.5 -> min correction = -0.025.
    # Corrected raw = 0.05 - (-0.025) = 0.075, then floored to 0.1.
    result = apply_residual_bias_correction(
        mu_hat=_series([0.05]),
        residual_bias=_series([-100.0]),
        backtest_days=_series([14.0]),
        cap_fraction=0.5,
        min_backtest_days=7,
        mu_floor=0.1,
    )
    assert result["correction_applied"].iloc[0] == -0.025
    assert result["mu_hat_corrected"].iloc[0] == 0.1


def test_small_bias_applied_uncapped():
    """Bias within +/- cap*mu_hat passes through unclipped."""
    # mu_hat=5, bias=+1 (under cap of 2.5). Corrected = 5 - 1 = 4.
    result = apply_residual_bias_correction(
        mu_hat=_series([5.0]),
        residual_bias=_series([1.0]),
        backtest_days=_series([10.0]),
        cap_fraction=0.5,
        min_backtest_days=7,
        mu_floor=0.1,
    )
    assert result["correction_applied"].iloc[0] == 1.0
    assert result["mu_hat_corrected"].iloc[0] == 4.0
    assert result["mu_hat_pre_correction"].iloc[0] == 5.0


def test_nan_bias_passes_through_uncorrected():
    """NaN bias entries -> no correction. Eligible siblings still correct."""
    result = apply_residual_bias_correction(
        mu_hat=_series([2.0, 3.0, 4.0]),
        residual_bias=pd.Series([np.nan, 0.6, np.nan]),
        backtest_days=_series([14.0, 14.0, 14.0]),
        cap_fraction=0.5,
        min_backtest_days=7,
        mu_floor=0.1,
    )
    assert result["correction_applied"].iloc[0] == 0.0
    assert result["correction_applied"].iloc[1] == 0.6
    assert result["correction_applied"].iloc[2] == 0.0
    assert result["mu_hat_corrected"].iloc[0] == 2.0
    assert result["mu_hat_corrected"].iloc[1] == pd.Series([3.0 - 0.6]).iloc[0]
    assert result["mu_hat_corrected"].iloc[2] == 4.0
