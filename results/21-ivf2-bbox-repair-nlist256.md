# Result 21 — IVF2: NLIST=256 + bounding-box repair (nprobe=8) — FAILED

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=5, nprobe=8 (out of 256 clusters)
- Index: KMeans NLIST=256, int16 quantization (scale=8192)
- Binary format: IVF2 (bbox_min/bbox_max appended after offsets)
- Bbox repair: after top-nprobe scan, check every unvisited cluster via bbox

## Results
| Metric | Value |
|--------|-------|
| p99 | 2002ms (capped) |
| FP | 0 (but 13356 HTTP errors) |
| FN | 0 |
| weighted_errors_E | 66780 |
| final_score | -6000 |

## Notes
NLIST=256 gives clusters of ~11718 vectors on average. With nprobe=8 that
means scanning ~93750 vectors per request — 7.6× more than the NLIST=1700
baseline (which scanned ~12355). Request timeouts dominated; score collapsed.

Next: revert to NLIST=1700 (mean ~1765 vectors/cluster, tight bboxes)
while keeping the IVF2 bbox repair. With smaller clusters, bbox repair
overhead is minimal and recall should improve over staged probing.
