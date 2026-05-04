# Result 20 — Staged probing + bulk ShortBuffer read + partial distance early exit

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=5, nprobe_fast=7, nprobe_full=16
- Index: full KMeans (n_init=1), vectors quantized to int16 (scale=8192)
- Staged probing: fast probe (nprobe=7); if count ∈ {2,3} (borderline), extends to nprobe=16
- cl-ids sorted by distance after topn-clusters! so fast probe always scans nearest clusters
- Bulk ShortBuffer.get(int,short[],int,int): 1 call per vector vs 14 individual gets
- Partial distance early exit: compute first 8 of 14 dims; skip rest when partial >= worst

## Results
| Metric | Value |
|--------|-------|
| p99 | 912ms |
| FP | 1 |
| FN | 0 |
| weighted_errors_E | 1 |
| final_score | 2949.44 |

## Notes
New best. Staged probing brings most queries down to nprobe=7 cost while
only borderline cases (count=2 or 3, the decision boundary for k=5,
threshold=0.6) pay the full nprobe=16 cost. Combined with bulk ShortBuffer
read (14× fewer JNI calls per vector) and partial distance early exit
(skip 6 of 14 dims when partial already exceeds worst), p99 dropped from
1260ms → 912ms while keeping E=1 (FN=0). Beats float32 nprobe=7 baseline
(E=4, score=2901) on both precision and final score.

Ideas from jairoblatt/rinha-2026-rust: staged probing strategy and
partial distance early termination.
