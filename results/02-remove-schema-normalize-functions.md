# Option 2: Remove schema validation from normalize functions

**Commit**: 96d6d8a

## Changes
- Converted 14 `s/defn` normalize functions in `logic.fraud-score` to plain `defn`
- Removed `schema.core` import and model schema requires from `logic/fraud_score.clj`
- These are pure internal computations called 14× per request — no runtime type-checking benefit

## Results vs Baseline and Option 1

| Metric | Baseline | Option 1 | Option 2 | Delta vs Opt1 |
|--------|----------|----------|----------|---------------|
| p99 latency | 1312.58ms | 798.09ms | 676.40ms | **-15%** |
| final_score | 2701.26 | 2774.20 | 2846.04 | **+72** |
| detection_score | 2819.38 | 2676.25 | 2676.25 | 0 |
| p99_score | -118.12 | 97.95 | 169.80 | **+72** |
| weighted_errors_E | 3 | 11 | 11 | 0 |
| false_positives | 0 | 2 | 2 | 0 |
| false_negatives | 1 | 3 | 3 | 0 |
| http_errors | 0 | 0 | 0 | 0 |
| requests processed | ~24,731 | ~38,397 | ~38,615 | +218 |

## Analysis
- Another significant latency reduction (-15%) from eliminating schema validation overhead
- Accuracy unchanged from Option 1 (same detection errors)
- The 14 s/defn→defn conversions eliminate ~14 input/output schema checks per request
- p99_score went from 97.95 → 169.80, contributing +72 to final score

## Cumulative improvement from baseline
- p99: 1312ms → 676ms (**-48%**)
- final_score: 2701.26 → 2846.04 (**+5.4%**)
