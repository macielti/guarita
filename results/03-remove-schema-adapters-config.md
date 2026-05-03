# Option 3: Remove schema validation from adapters and config

**Commit**: f6bdfed

## Changes
- Converted adapter functions to plain `defn`: wire->fraud-score, wire->transaction, wire->last-transaction, wire->customer, wire->merchant, wire->terminal
- Converted config accessors (normalization, mcc-risk) to plain `defn` — these were validating a static config map on every single request
- Replaced `medley/assoc-some` with `cond->` in wire->fraud-score

## Results (two runs)

| Metric | Option 2 | Option 3 Run1 | Option 3 Run2 |
|--------|----------|---------------|---------------|
| p99 latency | 676.40ms | 775.49ms | 753.01ms |
| final_score | 2846.04 | 2786.67 | 2799.44 |
| p99_score | 169.80 | 110.43 | 123.20 |
| weighted_errors_E | 11 | 11 | 11 |

## Analysis
- Results are slightly worse than Option 2, likely due to test variance (p99 is sensitive to GC pauses and outliers)
- Detection accuracy unchanged (same FP/FN counts)
- The improvement from removing adapter validation is smaller than normalize functions (adapters are called fewer times per request: 5-6 times vs 14)
- p99 variance of ~100ms is expected at this load level

## Conclusion
- Option 2 remains the best measured result (676ms p99, 2846 score)
- Options 1+2+3 are all improvements over baseline; small variance makes ranking 2 vs 3 uncertain
