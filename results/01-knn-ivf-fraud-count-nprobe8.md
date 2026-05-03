# Option 1: knn-ivf-fraud-count + nprobe 16→8

**Commit**: 60b29f6

## Changes
- Added `knn-ivf-fraud-count` that returns fraud count directly (avoids ArrayList/sort/map allocation in finalize-results)
- Reduced nprobe from 16 to 8 (halves cluster scanning per request)
- Controller uses knn-ivf-fraud-count, removing the reduce over neighbor maps

## Results vs Baseline

| Metric | Baseline | Option 1 | Delta |
|--------|----------|----------|-------|
| p99 latency | 1312.58ms | 798.09ms | **-39%** |
| final_score | 2701.26 | 2774.20 | **+2.7%** |
| detection_score | 2819.38 | 2676.25 | -5.1% |
| p99_score | -118.12 | 97.95 | **+216** |
| weighted_errors_E | 3 | 11 | +8 |
| false_positives | 0 | 2 | +2 |
| false_negatives | 1 | 3 | +2 |
| http_errors | 0 | 0 | 0 |
| requests processed | ~24,731 | ~38,397 | **+55%** |

## Analysis
- Significant latency improvement (39%) — fewer queued requests, higher throughput
- More requests processed because server keeps up better under load
- Minor precision loss from nprobe=8 (some edge cases misclassified by smaller search space)
- Net score improvement: p99 gain outweighs detection penalty

## Notes
- The +55% requests processed is a side-effect of lower latency: fewer requests time out
- nprobe=8 is still within pre-allocated scratch bounds (k≤8, nprobe≤16)
