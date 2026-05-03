# Result 15 — Majority count + full KMeans (nprobe=7)

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=5, nprobe=7
- Index: full KMeans (n_init=1, random_state=0)

## Results
| Metric | Value |
|--------|-------|
| p99 | 774ms |
| FP | 1 |
| FN | 1 |
| weighted_errors_E | 4 |
| final_score | 2901.51 |

## Notes
Big improvement over nprobe=5 (E=16→4, FN=5→1). FP stayed at 1.
Score 2901 > baseline 2819. Close to target E≤3.
Next: nprobe=8 to try to eliminate the remaining FN.
