# Result 16 — Majority count + full KMeans (nprobe=8)

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=5, nprobe=8
- Index: full KMeans (n_init=1, random_state=0)

## Results
| Metric | Value |
|--------|-------|
| p99 | 1382ms |
| FP | 1 |
| FN | 1 |
| weighted_errors_E | 4 |
| final_score | 2649.78 |

## Notes
E unchanged vs nprobe=7 (still FP=1 FN=1 E=4) but p99 jumped from
774ms → 1382ms, tanking the final score (2901→2649). nprobe=7 is the
latency/precision knee. Next: try k=7 with nprobe=7 to break the
remaining FP=1 FN=1 tie.
