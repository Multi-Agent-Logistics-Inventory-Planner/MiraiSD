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
        # Use transform for rolling calculations - avoids groupby().apply() deprecation
        # and preserves all columns including item_id
        grouped = df.groupby("item_id", sort=False)["consumption"]

        if need_ma7:
            df["ma7"] = grouped.transform(
                lambda x: x.astype(float).rolling(window=7, min_periods=1).mean()
            )
        if need_ma14:
            df["ma14"] = grouped.transform(
                lambda x: x.astype(float).rolling(window=14, min_periods=1).mean()
            )
        if need_std14:
            df["std14"] = grouped.transform(
                lambda x: x.astype(float).rolling(window=14, min_periods=1).std(ddof=0)
            )

    return df


def _exp_smooth_last(group: pd.DataFrame, alpha: float) -> float:
    level = None
    for x in group["consumption"].astype(float):
        if level is None:
            level = x
        else:
            level = alpha * x + (1.0 - alpha) * level
    return float(0.0 if level is None else level)


def _dow_weighted_estimate(group: pd.DataFrame) -> tuple[float, float, dict]:
    """Estimate demand with day-of-week weighting.

    1. Compute mean consumption per day-of-week (Mon=0..Sun=6)
    2. Overall mu_hat = mean of all daily consumption (same as ma14)
    3. dow_multiplier[d] = mean_consumption_on_day_d / overall_mean
    4. sigma_d_hat uses residuals after removing DOW effect

    Returns: (mu_hat, sigma_d_hat, dow_multipliers_dict)
    """
    g = group.copy()
    g["dow"] = pd.to_datetime(g["date"]).dt.dayofweek

    overall_mean = max(float(g["consumption"].mean()), config.MU_FLOOR)

    # Per-DOW mean consumption
    dow_means = g.groupby("dow")["consumption"].mean()

    # Multipliers: how much each DOW deviates from overall mean
    dow_multipliers = (dow_means / max(overall_mean, config.MU_FLOOR)).to_dict()

    # Fill missing DOWs with 1.0 (no adjustment)
    for d in range(7):
        if d not in dow_multipliers:
            dow_multipliers[d] = 1.0

    # Round multipliers for cleaner JSON
    dow_multipliers = {d: round(float(v), 4) for d, v in dow_multipliers.items()}

    # Sigma: std of residuals after removing DOW effect
    expected = g["dow"].map(dow_means).fillna(overall_mean)
    residuals = g["consumption"] - expected
    sigma_d_hat = max(float(residuals.std(ddof=0)), config.SIGMA_FLOOR)

    return overall_mean, sigma_d_hat, dow_multipliers


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
    valid_methods = {"ma7", "ma14", "exp_smooth", "dow_weighted"}
    if method not in valid_methods:
        raise ValueError(f"method must be one of {valid_methods}")

    df = _ensure_rollups(features_df)

    results = []
    for item_id, group in df.groupby("item_id", sort=False):
        group = group.sort_values("date")
        dow_multipliers = None

        if method == "dow_weighted":
            mu_hat, sigma_d_hat, dow_multipliers = _dow_weighted_estimate(group)
        elif method == "exp_smooth":
            level = _exp_smooth_last(group, alpha=config.ES_ALPHA)
            mu_hat = max(level, config.MU_FLOOR)
        elif method == "ma7":
            ma = float(group["ma7"].iloc[-1])
            mu_hat = max(ma, config.MU_FLOOR)
        else:  # "ma14"
            ma = float(group["ma14"].iloc[-1])
            mu_hat = max(ma, config.MU_FLOOR)

        if method != "dow_weighted":
            # sigma estimate: prefer std14 if available (ensured by _ensure_rollups)
            sigma = float(group["std14"].iloc[-1])
            sigma_d_hat = max(sigma, config.SIGMA_FLOOR)

        row = {
            "item_id": str(item_id),
            "mu_hat": float(mu_hat),
            "sigma_d_hat": float(sigma_d_hat),
            "method": method,
        }
        if dow_multipliers is not None:
            row["dow_multipliers"] = dow_multipliers

        results.append(row)

    cols = ["item_id", "mu_hat", "sigma_d_hat", "method"]
    if method == "dow_weighted":
        cols.append("dow_multipliers")
    return pd.DataFrame(results, columns=cols).reset_index(drop=True)


def forecast_demand(daily_consumption: pd.DataFrame, horizon_days: int = 21) -> pd.DataFrame:
    """
    Generate demand forecasts using available models.

    Args:
        daily_consumption: DataFrame with columns [item_id, date, consumption, avg_14]
        horizon_days: Number of days to forecast ahead

    Returns:
        DataFrame with forecast predictions
    """
    latest = daily_consumption.groupby("item_id", as_index=False).agg(avg_daily=("avg_14", "last"))
    latest["forecast_demand"] = latest["avg_daily"] * horizon_days
    return latest
