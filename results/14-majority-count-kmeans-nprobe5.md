# Result 14 — Majority count + full KMeans (nprobe=5)

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=5, nprobe=5
- Index: full KMeans (n_init=1, random_state=0, 300 iterations)
- Bins: pre-built locally, shipped as .gz in git

## Results
| Metric | Value |
|--------|-------|
| p99 | 647ms |
| FP | 1 |
| FN | 5 |
| weighted_errors_E | 16 |
| final_score | 2819.71 |

## Breakdown
- true_positive: 20852
- true_negative: 26121
- false_positive: 1
- false_negative: 5
- http_errors: 0

## Notes
E=16 slightly worse than previous inverse-distance run (E=15). FP count improved (3→1) but FN worsened (4→5). KMeans with n_init=1 produced unbalanced clusters (min=123, max=5694, mean=1764.7) — large clusters hurt IVF recall. Latency also increased (565→647ms) likely due to large clusters requiring longer scans.

Next: increase nprobe to improve recall and reduce FN.
