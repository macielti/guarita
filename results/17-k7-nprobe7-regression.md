# Result 17 — k=7 nprobe=7 (regression)

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=7, nprobe=7
- Index: full KMeans (n_init=1)

## Results
| Metric | Value |
|--------|-------|
| p99 | 933ms |
| FP | 1 |
| FN | 496 |
| weighted_errors_E | 1489 |
| final_score | 508.75 |

## Notes
Catastrophic regression. Root cause: with k=7 and threshold=0.6,
approval boundary is fraud_count ≤ 4 (4/7=0.57 < 0.6), not ≤ 3
as assumed. Most fraud edge cases have 3–4 fraud neighbors and pass
through as approved. Reverted to k=5 nprobe=7 (best: E=4, score=2901).
