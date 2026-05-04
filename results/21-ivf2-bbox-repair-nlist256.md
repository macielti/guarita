# Result 21 — IVF2: NLIST=256 + bounding-box repair (nprobe=8)

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=5, nprobe=8 (out of 256 clusters; replaces staged probing)
- Index: KMeans NLIST=256, int16 quantization (scale=8192)
- Binary format: IVF2 (adds bbox_min/bbox_max sections after offsets)
- Bbox repair: after scanning top-nprobe clusters, check every unvisited cluster
  via its axis-aligned bounding box; scan it if `bbox_lower_bound(query, c) ≤ worst_dist`
- No staged probing — bbox repair makes it adaptive by construction

## Cluster stats (3M vectors, 256 clusters)
- min size: 561, max: 38579, mean: 11718.8

## Changes
- `scripts/generate_dataset.py`: NLIST 1700→256; new IVF2 magic (0x32465649);
  per-cluster `bbox_min`/`bbox_max` in int16 appended after offsets section
- `src/guarita/dataset.clj`: loads bbox arrays; adds `bbox-lower-sq` (unrolled 14D);
  `knn-ivf-fraud-count` now scans nprobe nearest clusters then runs bbox repair loop;
  `KnnScratch` gains `^bytes visited` field (nlist-sized, reset per query)
- `src/guarita/controllers/fraud_score.clj`: single `nprobe=8`; calls
  `knn-ivf-fraud-count` (bbox variant)

## Results
| Metric | Value |
|--------|-------|
| p99 | TBD |
| FP | TBD |
| FN | TBD |
| weighted_errors_E | TBD |
| final_score | TBD |

## Notes
Baseline (result 20): E=1 (FP=1, FN=0), p99=912ms, score=2949.44.

With NLIST=256, each cluster holds ~11719 vectors on average (vs ~1765 with NLIST=1700).
The bbox repair guarantees no neighbor closer than the current worst-dist is skipped,
giving near-exact recall at adaptive cost. Trade-off: initial scan is heavier per cluster
(~11719 vectors per probe vs ~1765), but bbox prunes unvisited clusters quickly.
