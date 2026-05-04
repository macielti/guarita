# Result 22 — IVF2: NLIST=1700 + bounding-box repair (nprobe=8)

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=5, nprobe=8 (out of 1700 clusters; replaces staged probing)
- Index: KMeans NLIST=1700, int16 quantization (scale=8192)
- Binary format: IVF2 — per-cluster bbox_min/bbox_max (int16) appended after offsets
- Bbox repair: after top-nprobe scan, check every unvisited cluster whose
  `bbox_lower_bound(query, c) <= worst_neighbor_dist`; scan it if so

## Cluster stats (3M vectors, 1700 clusters)
- min: 123, max: 5694, mean: 1764.7

## Changes vs result 20
- NLIST=1700 retained; IVF2 format adds bbox arrays to ivf.bin
- Staged probing (nprobe-fast=7 / nprobe-full=16) replaced by
  single nprobe=8 + bbox repair (adaptive, scans any cluster whose
  bbox lb could contain a better neighbor)
- KnnScratch gains `^bytes visited` field (2048-byte default, reset per query)

## Results
| Metric | Value |
|--------|-------|
| p99 | TBD |
| FP | TBD |
| FN | TBD |
| weighted_errors_E | TBD |
| final_score | TBD |

## Notes
Baseline (result 20): E=1 (FP=1), p99=912ms, score=2949.44.

NLIST=256 (result 21) failed with timeouts: ~11718 vectors/cluster
made nprobe=8 scan 93K vectors per request. NLIST=1700 restores
~1765 vectors/cluster. The bbox check of the remaining 1692 clusters
adds ~1692 * 28 short reads (cheap), then only scans clusters whose
bbox lower bound <= current worst-neighbor distance.
