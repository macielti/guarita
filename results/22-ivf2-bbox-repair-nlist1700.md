# Result 22 — IVF2: NLIST=1700 + bounding-box repair (nprobe=8)

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=5, nprobe=8 (out of 1700 clusters)
- Index: KMeans NLIST=1700, int16 quantization (scale=8192)
- Binary format: IVF2 — per-cluster bbox_min/bbox_max (int16) after offsets
- Bbox repair: after top-nprobe scan, check every unvisited cluster via bbox
- No staged probing

## Cluster stats (3M vectors, 1700 clusters)
- min: 123, max: 5694, mean: 1764.7

## Results
| Metric | Value |
|--------|-------|
| p99 | 1448.94ms |
| FP | 1 |
| FN | 0 |
| weighted_errors_E | 1 |
| final_score | 2748.64 |

## Notes
Baseline (result 20): E=1 (FP=1), p99=912ms, score=2949.44.

Bbox repair is 59% slower (912ms → 1449ms) with identical E=1. In 14
dimensions, cluster bounding boxes are loose and heavily overlapping.
Most queries have bbox_lower_bound=0 for many clusters (query sits
"inside" their bbox), so the repair loop scans the majority of the 1700
clusters instead of pruning them. This approaches brute-force behavior
and explains the latency increase.

Ball-based pruning (triangle inequality: dist_to_centroid - max_radius)
would also be ineffective here: if bbox doesn't prune, balls won't either
since the bbox is always contained within the bounding ball.

Conclusion: IVF6 bbox repair is a good idea for low-dimensional or
well-separated clusters, but 14D with NLIST=1700 is too dense for it.
Staged probing (result 20) remains the best approach.
