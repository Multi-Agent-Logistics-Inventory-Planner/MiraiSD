# Forecasting System Evaluation Report

**Date:** 2026-04-12
**Branch:** `experiment/forecasting-backtest` (commit `de2cfb1`)
**Compared against:** `main` (commit `ad79ad76`)
**Author:** Automated analysis via walk-forward backtesting against 37 days of production data (627 products, 3,836 stock movements)

---

## 1. Situation

The forecasting service predicts daily demand for ~188 active products at Mirai Arcade. Two independent development efforts diverged from a common ancestor and each introduced different improvements.

**Main branch** added hierarchical supplier-based lead time computation (4-level fallback: preferred supplier + item, preferred supplier average, item history from any supplier, global default of 11 days). It also added reorder point propagation back to the products table so the UI and alert system reflect dynamically computed thresholds.

**Experiment branch** added stockout-aware demand estimation (detecting zero-inventory days and excluding them from training data), category-based fallback for cold-start items with no sales history, a walk-forward backtester with a REST API endpoint, and a corrected confidence formula (`1/(1+CV)` replacing the broken `1 - sigma/mu` which could produce negative values).

Neither branch contained both sets of improvements. The question was: which approach produces better forecasts, and can the two be combined?

---

## 2. Methodology

A head-to-head comparison script (`experiments/compare_fair.py`) was built to evaluate three forecasting strategies against the same prediction set, eliminating any bias from different evaluation criteria.

**Approaches tested:**

- **A (Main):** DOW-weighted estimation using all data, no stockout awareness, no category fallback
- **B (Branch):** DOW-weighted estimation with stockout day exclusion (`MIN_IN_STOCK_DAYS=7`), category fallback for cold-start items
- **C (Combined):** Branch approach plus stockout demand imputation (imputing DOW-average demand for each excluded stockout day)

**Evaluation protocol:** Walk-forward backtesting with forecast origins spaced by the horizon length. At each origin, all data before that date is used for training, and the next N days are used for evaluation. Tested at 3-day, 5-day, and 7-day horizons to verify robustness. All approaches were evaluated against both raw actuals and stockout-corrected actuals (excluding stockout days from the test window).

---

## 3. Results

### 3.1 Accuracy Comparison (same prediction set, N=455 for all)

| Approach | MAE | RMSE | Bias | Within 1 unit | Within 2 units |
|---|---|---|---|---|---|
| A. Main (no stockout filter) | **1.2158** | **2.6098** | -0.619 | **65.5%** | **83.5%** |
| B. Branch (stockout exclusion) | 1.2985 | 2.7171 | -0.560 | 64.2% | 82.0% |
| C. Combined (exclusion + imputation) | 1.2961 | 2.6972 | -0.574 | 63.7% | 82.0% |

Main wins across every metric. The branch's stockout exclusion increases MAE by +0.08 units/day. Imputation provides negligible improvement over pure exclusion (+0.002).

### 3.2 Robustness Across Horizons

| Horizon | Main MAE | Branch MAE | Delta |
|---|---|---|---|
| 3-day (N=714) | 1.9150 | 1.9537 | +0.04 |
| 5-day (N=516) | 1.4318 | 1.4820 | +0.05 |
| 7-day (N=455) | 1.2158 | 1.2985 | +0.08 |

Main outperforms consistently at every horizon. The gap widens with longer horizons.

### 3.3 Segment Analysis (7-day horizon)

**Clean test windows (no stockout in test period, N=278):**

| Approach | MAE | Bias |
|---|---|---|
| Main | **1.0095** | -0.391 |
| Branch | 1.0687 | -0.334 |

**Stockout-affected test windows (N=122):**

| Approach | MAE | Bias |
|---|---|---|
| Main | **2.3933** | -1.823 |
| Branch | 2.4722 | -1.777 |

Main wins in both segments. The branch's slight bias improvement (+0.05) does not compensate for the MAE degradation.

### 3.4 Stockout-Corrected Evaluation (N=400)

When evaluating against in-stock-day actuals only (removing stockout days from the test window):

| Approach | MAE | Bias |
|---|---|---|
| Main | **1.4315** | -0.828 |
| Branch | 1.4968 | -0.774 |

Same pattern. Main wins on accuracy; branch has marginally less bias.

---

## 4. Analysis

### 4.1 Why Stockout Exclusion Hurts

The system has only 37 days of historical data. Excluding stockout days (20.8% of item-days) removes approximately 1 in 5 training observations. With 14-day rolling windows, this leaves some items with as few as 8-10 data points for the DOW-weighted estimator, which needs at least 7 observations per day-of-week for stable multipliers.

The theoretical benefit of stockout exclusion (removing censored demand) is real, but it requires enough remaining in-stock observations to compensate. At the current data volume, the sample-size penalty outweighs the bias correction. This tradeoff will shift as data accumulates: with 60-90+ days of history, the exclusion approach may start providing net benefit.

### 4.2 Imputation Provides No Lift

The DOW-based imputation strategy (replacing stockout days with DOW-average demand from in-stock days) barely moves the needle because the imputed values are close to the DOW means that were already used in the estimation. The approach is mathematically sound but provides no practical improvement at this data volume.

### 4.3 What the Current Production Forecasts Look Like

A direct comparison of new pipeline output against current production forecasts (272 matched items) revealed:

- 158 of 165 items with sales history changed by less than 0.5 units/day in demand estimate
- 5 high-stockout items saw large decreases (2-11 units/day) because the new pipeline correctly uses all observed data rather than inflated stockout-filtered means
- Confidence scores improved dramatically: average confidence went from 0.094 to 0.284. The old formula (`1 - sigma/mu`) was producing 0.000 for many items where sigma exceeded mu, which is meaningless. The new formula (`1/(1+CV)`, floored at 0.01) always produces a positive, interpretable score.
- Average reorder points decreased from 30.4 to 17.7 units, and average safety stock from 17.4 to 10.8 units. This is a direct consequence of using true observed demand rates rather than inflated estimates.

### 4.4 Confidence Formula Comparison

The old production formula `confidence = 1 - (sigma / mu)` has two failure modes: it goes negative when sigma > mu (common for low-demand items), and it conflates variability with predictability. The new formula `1/(1+CV)` is bounded (0.01, 1.0], monotonically decreasing with coefficient of variation, and never produces nonsensical values.

---

## 5. What Was Implemented

The following changes were made on the experiment branch, combining the best of both branches:

1. **Hierarchical lead time computation** (from main): 4-level supplier-based hierarchy replacing the flat shipment-based approach. Uses a materialized view `mv_lead_time_stats` when available, falls back gracefully.

2. **Reorder point propagation** (from main): Pipeline Step 8 writes computed reorder points back to the products table so UI and alerts use dynamic thresholds.

3. **Stockout filter gated behind `STOCKOUT_FILTER_ENABLED`** (default: false): The stockout exclusion code is preserved but disabled by default. When the system accumulates 60-90+ days of data, it can be enabled via environment variable.

4. **Confidence formula fixed**: `1/(1+CV)` with a 0.01 floor, replacing the broken `1 - sigma/mu` in both vectorized and legacy code paths.

5. **Backtester API preserved**: `GET /forecasts/backtest` endpoint available for ongoing accuracy monitoring.

6. **Category fallback preserved**: Cold-start items with no history can inherit category-average demand. No current lift but provides future value as new products are added.

7. **Combined items query**: `get_items()` now returns both `preferred_supplier_id` (for hierarchical lead times) and `category_name` (for category fallback).

All 220 tests pass. No database schema changes, no API contract changes, no Kafka consumer changes.

---

## 6. Recommended Course of Action

### Immediate (merge to main)

1. **Rebase this branch onto current main** to resolve the file-level divergences in `supabase_repo.py`, `pipeline.py`, `lead_time.py`, and `config.py`. The changes are isolated to the forecasting service and do not conflict with main's frontend or Java backend changes (PRs #225-#230).

2. **Merge to main** with the stockout filter disabled (`STOCKOUT_FILTER_ENABLED=false`). This gives the system main's superior MAE=1.22 accuracy while preserving all the infrastructure improvements.

3. **Run `POST /forecasts/trigger`** after deployment to regenerate all forecasts with the new pipeline. This will update demand estimates, confidence scores, and reorder points across all 272 active items.

4. **Verify the frontend** handles the new confidence range (0.01-1.0 vs the old 0.0-1.0) and the lower reorder points. The 5 high-stockout items that see large mu decreases (Dog with pink bib, Panda wrapped in cabbage, etc.) should be spot-checked to confirm reorder alerts behave correctly with the adjusted thresholds.

### Short-term (next 2-4 weeks)

5. **Monitor accuracy** via the backtester endpoint (`GET /forecasts/backtest?methods=dow_weighted&horizon=7`). Run weekly and track MAE, bias, and within-1/within-2 metrics. As data volume grows past 45 days, the metrics should improve naturally.

6. **Clean up deprecated code**: Remove `_resolve_lead_time` (non-indexed variant) and `_compute_forecasts_legacy` from the codebase. They exist only for historical comparison testing and add maintenance burden.

7. **Remove experiment PNG files** from git history. The 14 report images (~1.4MB) in `experiments/report/` and `experiments/system_report/` should be gitignored or moved to an artifact store.

### Medium-term (60-90 days of data)

8. **Re-evaluate stockout filtering**: Once the system has 60-90+ days of history, re-run the fair comparison (`experiments/compare_fair.py`) to test whether stockout exclusion now provides net benefit. If MAE improves, enable `STOCKOUT_FILTER_ENABLED=true` in the environment.

9. **Enable MAPE-blended confidence**: Once historical forecast predictions span 60+ days, enable `CONFIDENCE_MAPE_ENABLED=true` to blend the variability-based confidence with actual backtest accuracy. This produces confidence scores that reflect real prediction quality rather than just demand variability.

---

## Appendix: Test Artifacts

| File | Description |
|---|---|
| `experiments/compare_fair.py` | Head-to-head backtest comparing all approaches on identical prediction sets |
| `experiments/compare_approaches.py` | Initial comparison (unfair N due to different evaluation guards) |
| `experiments/compare_forecasts.py` | Production forecast comparison (current vs new pipeline output) |
| `experiments/forecast_comparison.csv` | Full item-by-item comparison export (272 items) |
| `experiments/FORECASTING_REPORT.md` | This report |
