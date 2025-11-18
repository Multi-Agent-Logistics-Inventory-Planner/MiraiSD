import pandas as pd

from . import config


def _ensure_rollups(features_df: pd.DataFrame) -> pd.DataFrame:
    df = features_df.copy()
    df["date"] = pd.to_datetime(df["date"]).dt.floor("D")
    df = df.sort_values(["item_id", "date"]).reset_index(drop=True)

    need_ma7 = "ma7" not in df.columns
    need_ma14 = "ma14" not in df.columns
    need_std14 = "std14" not in df.columns

    if need_ma7 or need_ma14 or need_std14:
        def _roll(g: pd.DataFrame) -> pd.DataFrame:
            values = g["consumption"].astype(float)
            if need_ma7:
                g["ma7"] = values.rolling(window=7, min_periods=1).mean()
            if need_ma14:
                g["ma14"] = values.rolling(window=14, min_periods=1).mean()
            if need_std14:
                g["std14"] = values.rolling(window=14, min_periods=1).std(ddof=0)
            return g

        df = df.groupby("item_id", group_keys=False).apply(_roll)

    return df


def _exp_smooth_last(group: pd.DataFrame, alpha: float) -> float:
    level = None
    for x in group["consumption"].astype(float):
        if level is None:
            level = x
        else:
            level = alpha * x + (1.0 - alpha) * level
    return float(0.0 if level is None else level)


def estimate_mu_sigma(features_df: pd.DataFrame, method: str = "ma14") -> pd.DataFrame:
    """Estimate per-SKU average daily demand (mu_hat) and daily std (sigma_d_hat).

    Inputs must contain at least: date, item_id, consumption. If ma7/ma14/std14
    are missing, they are computed with appropriate rolling windows.
    """
    required = {"date", "item_id", "consumption"}
    missing = required - set(features_df.columns)
    if missing:
        raise ValueError(f"features_df missing required columns: {sorted(missing)}")

    method = method.lower()
    if method not in {"ma7", "ma14", "exp_smooth"}:
        raise ValueError("method must be one of {'ma7','ma14','exp_smooth'}")

    df = _ensure_rollups(features_df)

    results = []
    for item_id, group in df.groupby("item_id", sort=False):
        group = group.sort_values("date")

        if method == "exp_smooth":
            level = _exp_smooth_last(group, alpha=config.ES_ALPHA)
            mu_hat = max(level, config.MU_FLOOR)
        elif method == "ma7":
            ma = float(group["ma7"].iloc[-1])
            mu_hat = max(ma, config.MU_FLOOR)
        else:  # "ma14"
            ma = float(group["ma14"].iloc[-1])
            mu_hat = max(ma, config.MU_FLOOR)

        # sigma estimate: prefer std14 if available (ensured by _ensure_rollups)
        sigma = float(group["std14"].iloc[-1])
        sigma_d_hat = max(sigma, config.SIGMA_FLOOR)

        results.append({
            "item_id": str(item_id),
            "mu_hat": float(mu_hat),
            "sigma_d_hat": float(sigma_d_hat),
            "method": method,
        })

    return pd.DataFrame(results, columns=["item_id", "mu_hat", "sigma_d_hat", "method"]).reset_index(drop=True)


