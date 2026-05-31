import numpy as np
import pandas as pd
from scipy.optimize import minimize_scalar
from scipy.stats import poisson

from . import config
from .estimators.tsb import tsb_estimate


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


def _censored_poisson_rate(
    observed: np.ndarray,
    censored: np.ndarray,
    initial: float | None = None,
) -> float:
    """Maximum-likelihood Poisson rate with right-censored stockout days.

    Stockout days carry partial information: we know demand was at least the
    observed consumption (the store sold its remaining inventory), but the
    true rate could be higher. Treats those rows as right-censored and
    maximizes the joint log-likelihood:

        sum_{not censored} log P(X = k_i | lambda)
        + sum_{censored}   log P(X >= k_i | lambda)

    using scipy's bounded scalar optimizer. Used in place of a naive mean
    for items that have ≥1 stockout day in the training window.
    """
    if len(observed) == 0:
        return config.MU_FLOOR
    obs = np.asarray(observed, dtype=float)
    cens = np.asarray(censored, dtype=bool)
    if not cens.any():
        return max(float(obs.mean()), config.MU_FLOOR)

    uncensored = obs[~cens]
    censored_obs = obs[cens]
    init = initial if initial is not None else max(float(obs.mean()), config.MU_FLOOR)

    def neg_log_lik(lam: float) -> float:
        if lam <= 0:
            return 1e12
        ll = 0.0
        if uncensored.size:
            ll += float(poisson.logpmf(uncensored, lam).sum())
        if censored_obs.size:
            # P(X >= k) = P(X > k-1) = poisson.sf(k-1, lam)
            sf_vals = poisson.sf(np.maximum(censored_obs - 1, 0), lam)
            sf_vals = np.clip(sf_vals, 1e-300, 1.0)
            ll += float(np.log(sf_vals).sum())
        return -ll

    upper = max(init * 10.0, 1.0)
    result = minimize_scalar(neg_log_lik, bounds=(1e-6, upper), method="bounded")
    if not result.success or not np.isfinite(result.x):
        return max(init, config.MU_FLOOR)
    return max(float(result.x), config.MU_FLOOR)


def _dow_weighted_estimate(
    group: pd.DataFrame,
    min_in_stock_days: int = 0,
) -> tuple[float, float, dict]:
    """Estimate demand with day-of-week weighting.

    1. Compute mean consumption per day-of-week (Mon=0..Sun=6)
    2. Overall mu_hat = mean of all daily consumption (same as ma14),
       or censored-Poisson MLE if is_stockout flags are present.
    3. dow_multiplier[d] = mean_consumption_on_day_d / overall_mean
    4. sigma_d_hat uses residuals after removing DOW effect

    Returns: (mu_hat, sigma_d_hat, dow_multipliers_dict)
    """
    g = group
    if g.empty:
        return config.MU_FLOOR, config.SIGMA_FLOOR, {d: 1.0 for d in range(7)}

    g["dow"] = pd.to_datetime(g["date"]).dt.dayofweek

    # Overall mu: censored-Poisson MLE when censored observations are present
    # and the SKU has enough stockout days to be worth the extra computation;
    # otherwise a plain mean. The MLE accounts for "demand was at least X"
    # observations that a plain mean understates.
    naive_mean = max(float(g["consumption"].mean()), config.MU_FLOOR)
    if (
        config.CENSORED_DEMAND_ENABLED
        and "is_stockout" in g.columns
        and int(g["is_stockout"].sum()) >= config.CENSORED_DEMAND_MIN_STOCKOUT_DAYS
    ):
        overall_mean = _censored_poisson_rate(
            observed=g["consumption"].to_numpy(),
            censored=g["is_stockout"].astype(bool).to_numpy(),
            initial=naive_mean,
        )
    else:
        overall_mean = naive_mean

    # Per-DOW mean consumption
    dow_means = g.groupby("dow")["consumption"].mean()

    # Multipliers: how much each DOW deviates from overall mean.
    # Floored at config.DOW_MULTIPLIER_FLOOR so cold weekdays do not collapse
    # to literally zero -- one weekday sale on a zeroed-out DOW reads as
    # infinite error and the policy layer ends up over-concentrating buffer
    # on a single day.
    dow_multipliers = (dow_means / max(overall_mean, config.MU_FLOOR)).clip(
        lower=config.DOW_MULTIPLIER_FLOOR
    ).to_dict()

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


def estimate_mu_sigma(
    features_df: pd.DataFrame,
    method: str = "ma14",
    min_in_stock_days: int | None = None,
) -> pd.DataFrame:
    """Estimate per-SKU average daily demand (mu_hat) and daily std (sigma_d_hat).

    Inputs must contain at least: date, item_id, consumption. If ma7/ma14/std14
    are missing, they are computed with appropriate rolling windows.

    Args:
        min_in_stock_days: Retained for back-compat; the censored-demand path
            handles stockout-aware estimation internally. Used as a sanity
            floor for n_observed_days reporting.
    """
    required = {"date", "item_id", "consumption"}
    missing = required - set(features_df.columns)
    if missing:
        raise ValueError(f"features_df missing required columns: {sorted(missing)}")

    method = method.lower()
    valid_methods = {"ma7", "ma14", "exp_smooth", "dow_weighted", "tsb", "dow_weighted_events"}
    if method not in valid_methods:
        raise ValueError(f"method must be one of {valid_methods}")
    # The "events" suffix routes the per-SKU estimate through dow_weighted -- the
    # event-multiplier learning + application happens in the pipeline layer where
    # we have access to the full event stream. Method label is preserved on the
    # output row so downstream code knows the events path is active.
    base_method = "dow_weighted" if method == "dow_weighted_events" else method
    label_method = method

    if min_in_stock_days is None:
        min_in_stock_days = config.MIN_IN_STOCK_DAYS

    df = _ensure_rollups(features_df)

    results = []
    for item_id, group in df.groupby("item_id", sort=False):
        group = group.sort_values("date")
        dow_multipliers = None
        p_value = None
        z_value = None

        # n_observed_days = count of in-stock training days. Surfaced so the
        # shrinkage layer can weight item-level vs category-level estimates
        # by sample size rather than treating every item identically. When
        # is_stockout is present, only in-stock days count toward sample size.
        if "is_stockout" in group.columns:
            n_observed_days = int((~group["is_stockout"].astype(bool)).sum())
        else:
            n_observed_days = int(len(group))

        if base_method == "tsb":
            # TSB consumes the zero-filled daily series directly: zero-sale
            # days are the signal it decays the sale probability against.
            mu_hat, sigma_d_hat, p_value, z_value, dow_multipliers = tsb_estimate(group)
        elif base_method == "dow_weighted":
            mu_hat, sigma_d_hat, dow_multipliers = _dow_weighted_estimate(
                group, min_in_stock_days
            )
        elif base_method == "exp_smooth":
            level = _exp_smooth_last(group, alpha=config.ES_ALPHA) if not group.empty else 0.0
            mu_hat = max(level, config.MU_FLOOR)
        elif base_method == "ma7":
            ma = float(group["ma7"].iloc[-1]) if not group.empty else 0.0
            mu_hat = max(ma, config.MU_FLOOR)
        else:  # "ma14"
            ma = float(group["ma14"].iloc[-1]) if not group.empty else 0.0
            mu_hat = max(ma, config.MU_FLOOR)

        if base_method not in ("dow_weighted", "tsb"):
            sigma = float(group["std14"].iloc[-1]) if not group.empty else 0.0
            sigma_d_hat = max(sigma, config.SIGMA_FLOOR)

        row = {
            "item_id": str(item_id),
            "mu_hat": float(mu_hat),
            "sigma_d_hat": float(sigma_d_hat),
            "method": label_method,
            "n_observed_days": n_observed_days,
        }
        if dow_multipliers is not None:
            row["dow_multipliers"] = dow_multipliers
        if p_value is not None:
            row["p"] = float(p_value)
        if z_value is not None:
            row["z"] = float(z_value)

        results.append(row)

    cols = ["item_id", "mu_hat", "sigma_d_hat", "method", "n_observed_days"]
    if base_method in ("dow_weighted", "tsb"):
        cols.append("dow_multipliers")
    if base_method == "tsb":
        cols.extend(["p", "z"])
    return pd.DataFrame(results, columns=cols).reset_index(drop=True)


def apply_category_fallback(
    estimates_df: pd.DataFrame,
    category_map: dict[str, str],
    items_with_history: set[str] | None = None,
) -> pd.DataFrame:
    """Replace MU_FLOOR estimates with category average for cold-start items.

    Only applies to items that have NO sales history at all (truly new products).
    Items that have history but low/zero sales keep their computed mu_hat --
    the estimator already accounted for stockouts.

    Args:
        estimates_df: DataFrame with at least item_id and mu_hat columns.
        category_map: Dict mapping item_id -> category_name.
        items_with_history: Set of item_ids that appeared in training data.
            If provided, only items NOT in this set get the fallback.
            If None, falls back to the mu_hat <= MU_FLOOR heuristic.

    Returns:
        New DataFrame with cold-start items updated to category averages.
    """
    df = estimates_df.copy()
    df["_category"] = df["item_id"].map(category_map).fillna("Unknown")

    # Compute category averages from items that have real demand
    has_demand = df[df["mu_hat"] > config.MU_FLOOR]
    cat_mu = has_demand.groupby("_category")["mu_hat"].mean()

    # Identify cold-start items: at MU_FLOOR AND no history
    at_floor = df["mu_hat"] <= config.MU_FLOOR
    if items_with_history is not None:
        is_cold_start = at_floor & ~df["item_id"].isin(items_with_history)
    else:
        is_cold_start = at_floor

    df.loc[is_cold_start, "mu_hat"] = (
        df.loc[is_cold_start, "_category"].map(cat_mu).fillna(config.MU_FLOOR)
    )

    return df.drop(columns=["_category"])


def apply_shrinkage(
    estimates_df: pd.DataFrame,
    category_map: dict[str, str],
    strength: float | None = None,
    min_category_items: int | None = None,
) -> pd.DataFrame:
    """Pull each item's mu_hat toward its category prior, weighted by sample size.

    Formula: ``mu_shrunk = (n * mu_item + k * mu_cat) / (n + k)`` where
    ``n = n_observed_days`` and ``k = strength``. Items with lots of history
    keep their own estimate; items with thin history get pulled toward the
    category mean. Categories with fewer than ``min_category_items`` SKUs
    provide too-noisy a prior, so we leave their items untouched. The
    "(uncategorized)"/"Unknown" bucket is also skipped because pooling across
    unrelated SKUs is worse than no pooling.

    Runs AFTER apply_category_fallback, so cold-start items (which have
    already been replaced with the category mean) collapse to their prior
    when shrunk; thin-history items get a soft pull; rich-history items
    are essentially unchanged.

    Args:
        estimates_df: Must include item_id, mu_hat, and n_observed_days.
        category_map: item_id -> category_name.
        strength: k parameter; defaults to config.SHRINKAGE_STRENGTH.
        min_category_items: skip categories smaller than this.

    Returns:
        New DataFrame with shrunken mu_hat. Adds a "mu_hat_pre_shrinkage"
        column so callers (the why-this-number drawer) can show the move.
    """
    k = strength if strength is not None else config.SHRINKAGE_STRENGTH
    min_items = (
        min_category_items if min_category_items is not None
        else config.SHRINKAGE_MIN_CATEGORY_ITEMS
    )
    df = estimates_df.copy()
    if "n_observed_days" not in df.columns:
        df["n_observed_days"] = 0

    df["_category"] = df["item_id"].map(category_map).fillna("Unknown")
    df["mu_hat_pre_shrinkage"] = df["mu_hat"].astype(float)

    has_demand = df[df["mu_hat"] > config.MU_FLOOR]
    cat_mu = has_demand.groupby("_category")["mu_hat"].mean()
    cat_size = df.groupby("_category")["item_id"].nunique()

    def _shrink(row):
        cat = row["_category"]
        if cat in {"Unknown", "(uncategorized)"}:
            return row["mu_hat"]
        size = int(cat_size.get(cat, 0))
        if size < min_items:
            return row["mu_hat"]
        prior = cat_mu.get(cat)
        if prior is None or pd.isna(prior):
            return row["mu_hat"]
        n = float(row["n_observed_days"])
        if n <= 0:
            return row["mu_hat"]
        return (n * float(row["mu_hat"]) + k * float(prior)) / (n + k)

    df["mu_hat"] = df.apply(_shrink, axis=1).astype(float).clip(lower=config.MU_FLOOR)
    return df.drop(columns=["_category"])


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
