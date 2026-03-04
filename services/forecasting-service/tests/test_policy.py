import math

import numpy as np
import pandas as pd

from src.policy import (
    z_for_service_level,
    sigma_lead_time,
    compute_safety_stock,
    compute_safety_stock_vectorized,
    reorder_point,
    days_to_stockout,
    suggest_order,
)


def test_policy_core_scenario_with_prints():
    # Scenario: mu=5/day, sigma_d=2, L=7, alpha=0.95
    mu = 5.0
    sigma_d = 2.0
    L = 7.0
    alpha = 0.95

    z = z_for_service_level(alpha)
    sigma_lt = sigma_lead_time(mu, sigma_d, L)
    ss = compute_safety_stock(mu, sigma_d, L, alpha)
    rop = reorder_point(mu, ss, L)
    dso = days_to_stockout(current_qty=50.0, mu_hat=mu)
    q = suggest_order(current_qty=60.0, mu_hat=mu, L=L, safety_stock=ss, target_days_of_cover=21.0)

    print("\n--- Policy Core Scenario ---")
    print(f"inputs: mu={mu}, sigma_d={sigma_d}, L={L}, alpha={alpha}")
    print(f"z={z:.3f}, sigma_lt={sigma_lt:.3f}, safety_stock={ss:.3f}, rop={rop:.3f}")
    print(f"days_to_stockout={dso:.3f}, suggested_qty={q}")

    # math checks
    assert abs(z - 1.64485) < 5e-3
    assert abs(sigma_lt - (L ** 0.5) * sigma_d) < 1e-9
    assert abs(ss - z * sigma_lt) < 1e-9
    assert abs(rop - (mu * L + ss)) < 1e-9
    assert abs(dso - (50.0 / mu)) < 1e-9
    assert q == 45  # ceil(21*5 - 60) = ceil(105 - 60) = 45


def test_policy_edge_cases_with_prints():
    print("\n--- Policy Edge Cases ---")
    # Near-zero demand -> infinite days to stockout
    dso_inf = days_to_stockout(current_qty=100.0, mu_hat=0.0)
    print(f"mu_hat=0 -> days_to_stockout={dso_inf}")
    assert math.isinf(dso_inf)

    # L = 0 -> sigma_lt = 0, ROP = safety_stock
    mu = 3.0
    sigma_d = 1.0
    L = 0.0
    alpha = 0.9
    z = z_for_service_level(alpha)
    sigma_lt = sigma_lead_time(mu, sigma_d, L)
    ss = compute_safety_stock(mu, sigma_d, L, alpha)
    rop = reorder_point(mu, ss, L)
    print(f"L=0 -> z={z:.3f}, sigma_lt={sigma_lt:.3f}, safety_stock={ss:.3f}, rop={rop:.3f}")
    assert abs(sigma_lt - 0.0) < 1e-12
    assert abs(rop - ss) < 1e-12

    # Invalid alpha should raise
    try:
        _ = z_for_service_level(1.5)
        assert False, "Expected ValueError for invalid alpha"
    except ValueError:
        pass


def test_vectorized_sigma_L_none_matches_current():
    """sigma_L=None should match the existing demand-only behavior."""
    mu = pd.Series([5.0, 3.0])
    sigma_d = pd.Series([2.0, 1.0])
    L = pd.Series([7.0, 14.0])

    ss_without = compute_safety_stock_vectorized(mu, sigma_d, L, 0.95, sigma_L=None)
    ss_zero = compute_safety_stock_vectorized(mu, sigma_d, L, 0.95, sigma_L=pd.Series([0.0, 0.0]))

    # With sigma_L=0, full formula reduces to demand-only: z * sqrt(L * sigma_d^2 + 0) = z * sigma_d * sqrt(L)
    np.testing.assert_array_almost_equal(ss_without.values, ss_zero.values, decimal=9)


def test_vectorized_sigma_L_increases_safety_stock():
    """sigma_L > 0 should produce higher safety stock than sigma_L=None."""
    mu = pd.Series([5.0, 10.0])
    sigma_d = pd.Series([2.0, 3.0])
    L = pd.Series([7.0, 7.0])

    ss_without = compute_safety_stock_vectorized(mu, sigma_d, L, 0.95, sigma_L=None)
    ss_with = compute_safety_stock_vectorized(mu, sigma_d, L, 0.95, sigma_L=pd.Series([2.0, 3.0]))

    for i in range(len(mu)):
        assert ss_with.iloc[i] > ss_without.iloc[i], (
            f"Item {i}: sigma_L > 0 should increase safety stock"
        )


def test_vectorized_sigma_L_scalar():
    """sigma_L as scalar float should work."""
    mu = pd.Series([5.0])
    sigma_d = pd.Series([2.0])
    L = 7.0

    ss = compute_safety_stock_vectorized(mu, sigma_d, L, 0.95, sigma_L=1.5)
    assert ss.iloc[0] > 0


