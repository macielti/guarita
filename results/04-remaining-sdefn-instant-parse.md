# Option 4: Remove remaining s/defn + Instant/parse

**Commit**: 262527f

## Changes
- Converted controller `fraud-score!` and handler `fraud-score!` from `s/defn` to plain `defn`
- Replaced `jt/instant` with `Instant/parse` directly in transaction adapter (skips java-time dispatch)

## Results (two runs)

| Metric | Option 2 | Option 3 Run1 | Option 4 Run1 | Option 4 Run2 |
|--------|----------|---------------|---------------|---------------|
| p99 latency | 676.40ms | 775.49ms | 778.85ms | 807.06ms |
| final_score | 2846.04 | 2786.67 | 2784.79 | 2769.34 |
| weighted_errors_E | 11 | 11 | 11 | 11 |

## Analysis
- Options 3+4 consistently show ~750-810ms p99, vs Option 2's single 676ms measurement
- The 676ms in Option 2 may have been a lucky low-variance run
- Removing adapter/config/controller schema validation shows diminishing returns
- Detection accuracy unchanged across all options (same FP/FN counts)

## Observations
- Option 2 remains the best confirmed result, but the true steady-state may be ~750ms
- Test has inherent p99 variance due to GC pauses and OS scheduling at 0.45 CPU limits
