import pandas as pd

from src.forecast import estimate_mu_sigma


def _make_constant_features(item: str, value: float, days: int = 20) -> pd.DataFrame:
    dates = pd.date_range("2025-11-01", periods=days, freq="D")
    return pd.DataFrame({
        "date": dates,
        "item_id": [item] * days,
        "consumption": [float(value)] * days,
    })


def test_constant_series_ma14_with_prints():
    feats = _make_constant_features("A", 5.0, days=20)
    print("\n" + "="*80)
    print("TEST: Constant series (A, 5.0/day) with ma14 method")
    print("="*80)
    print("\nINPUT features_df (head):")
    print(feats.head().to_string(index=False))
    print("\nINPUT features_df (tail):")
    print(feats.tail().to_string(index=False))

    out = estimate_mu_sigma(feats, method="ma14")
    print("\nOUTPUT estimate_mu_sigma (ma14):")
    print(out.to_string(index=False))
    print("="*80 + "\n")

    # mu ~ 5, sigma ~ 0 (floored to >= 0.01)
    row = out.iloc[0]
    assert row["item_id"] == "A"
    assert abs(row["mu_hat"] - 5.0) < 1e-9
    assert row["sigma_d_hat"] >= 0.01


def test_constant_series_es_with_prints():
    feats = _make_constant_features("B", 3.0, days=15)
    print("\n" + "="*80)
    print("TEST: Constant series (B, 3.0/day) with exp_smooth method")
    print("="*80)
    print("\nINPUT features_df (head):")
    print(feats.head().to_string(index=False))
    print("\nINPUT features_df (tail):")
    print(feats.tail().to_string(index=False))

    out = estimate_mu_sigma(feats, method="exp_smooth")
    print("\nOUTPUT estimate_mu_sigma (exp_smooth):")
    print(out.to_string(index=False))
    print("="*80 + "\n")

    row = out.iloc[0]
    assert row["item_id"] == "B"
    assert abs(row["mu_hat"] - 3.0) < 1e-6
    assert row["sigma_d_hat"] >= 0.01


def test_multi_sku_with_prints():
    a = _make_constant_features("A", 2.0, days=10)
    b = _make_constant_features("B", 4.0, days=10)
    feats = pd.concat([a, b], ignore_index=True)
    print("\n" + "="*80)
    print("TEST: Multi-SKU (A=2.0/day, B=4.0/day) with ma7 method")
    print("="*80)
    print("\nINPUT multi-SKU features_df (sample per item):")
    print(feats.groupby("item_id").head(2).to_string(index=False))

    out = estimate_mu_sigma(feats, method="ma7")
    print("\nOUTPUT estimate_mu_sigma (ma7, multi):")
    print(out.sort_values("item_id").to_string(index=False))
    print("="*80 + "\n")

    assert set(out["item_id"]) == {"A", "B"}
    mu_map = {r["item_id"]: r["mu_hat"] for _, r in out.iterrows()}
    assert abs(mu_map["A"] - 2.0) < 1e-9
    assert abs(mu_map["B"] - 4.0) < 1e-9


