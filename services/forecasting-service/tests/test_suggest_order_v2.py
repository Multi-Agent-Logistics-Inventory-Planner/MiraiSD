"""Tests for suggest_order_v2: lead-time-aware order quantity with on-order netting.

The v1 formula (Q = target_days*mu - on_hand) ignores lead time and safety
stock entirely, so even a perfect mu under-orders by a full lead time of
demand and double-orders anything already inbound on a PENDING shipment.
"""

import pandas as pd
import pytest

from src import policy


class TestSuggestOrderV2:
    def test_includes_lead_time_demand_and_safety_stock(self):
        # mu=2, L=10, target=21, SS=5, on_hand=0 -> ceil(2*31 + 5) = 67
        qty = policy.suggest_order_v2_vectorized(
            current_qty=pd.Series([0]),
            mu_hat=pd.Series([2.0]),
            L=pd.Series([10.0]),
            safety_stock=pd.Series([5.0]),
            target_days=21,
        )
        assert qty.iloc[0] == 67

    def test_nets_out_on_hand(self):
        qty = policy.suggest_order_v2_vectorized(
            current_qty=pd.Series([30]),
            mu_hat=pd.Series([2.0]),
            L=pd.Series([10.0]),
            safety_stock=pd.Series([5.0]),
            target_days=21,
        )
        assert qty.iloc[0] == 37  # 67 - 30

    def test_nets_out_on_order(self):
        qty = policy.suggest_order_v2_vectorized(
            current_qty=pd.Series([30]),
            mu_hat=pd.Series([2.0]),
            L=pd.Series([10.0]),
            safety_stock=pd.Series([5.0]),
            target_days=21,
            on_order=pd.Series([20.0]),
        )
        assert qty.iloc[0] == 17  # 67 - 30 - 20

    def test_floors_at_zero(self):
        qty = policy.suggest_order_v2_vectorized(
            current_qty=pd.Series([500]),
            mu_hat=pd.Series([2.0]),
            L=pd.Series([10.0]),
            safety_stock=pd.Series([5.0]),
            target_days=21,
        )
        assert qty.iloc[0] == 0

    def test_scalar_lead_time(self):
        qty = policy.suggest_order_v2_vectorized(
            current_qty=pd.Series([0, 0]),
            mu_hat=pd.Series([1.0, 3.0]),
            L=7,
            safety_stock=pd.Series([0.0, 0.0]),
            target_days=14,
        )
        assert qty.iloc[0] == 21  # 1*(7+14)
        assert qty.iloc[1] == 63

    def test_zero_mu_orders_nothing(self):
        qty = policy.suggest_order_v2_vectorized(
            current_qty=pd.Series([0]),
            mu_hat=pd.Series([0.0]),
            L=pd.Series([10.0]),
            safety_stock=pd.Series([0.0]),
            target_days=21,
        )
        assert qty.iloc[0] == 0

    def test_negative_inputs_clipped(self):
        qty = policy.suggest_order_v2_vectorized(
            current_qty=pd.Series([-5]),
            mu_hat=pd.Series([-1.0]),
            L=pd.Series([-3.0]),
            safety_stock=pd.Series([-2.0]),
            target_days=21,
        )
        assert qty.iloc[0] == 0
