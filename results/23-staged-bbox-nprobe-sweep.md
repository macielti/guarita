# Result 23 — Staged probe + bbox repair on borderline queries: nprobe sweep

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=5, IVF_NPROBE env var controls fast-probe cluster count
- Index: KMeans NLIST=1700, int16 quantization (scale=8192), IVF2 format
- Strategy: scan nprobe-fast nearest clusters; if result is borderline
  (count ∈ {2,3} for k=5, threshold=0.6), run bbox repair over all
  unvisited clusters (scan any cluster whose bbox lower bound ≤ current
  worst neighbor dist). Clear-cut queries pay only the fast-probe cost.

## Rationale for staged+bbox vs always-on bbox
Bbox repair always-on (results 21/22) was too slow: 14D cluster bboxes
overlap heavily, so the repair loop scans most of the 1700 clusters on
every query (~1449ms p99 for nprobe=8, timeouts for nprobe=4). Restricting
repair to borderline queries eliminates that overhead for the ~85% of
queries whose vote is already clear.

## nprobe sweep results
| nprobe | p99 | FP | FN | E | final_score |
|--------|-----|----|----|---|-------------|
| 3 | 1192.87ms | 1 | 0 | 1 | 2833.10 |
| 4 | 1141.53ms | 1 | 0 | 1 | 2852.21 |
| **5** | **892.29ms** | **1** | **0** | **1** | **2959.18** |
| 7 | 1150.98ms | 1 | 0 | 1 | 2848.62 |

Previous best (result 20, staged nprobe_fast=7 / nprobe_full=16):
E=1, p99=912ms, score=2949.44.

## Notes
**nprobe=5 is the new best**: p99=892ms (−20ms vs baseline), score=2959.18
(+9.74 vs baseline). The bbox repair for borderline queries is slightly
more thorough than the fixed nprobe-full=16 probe — it scans any cluster
that geometrically could contain a better neighbor rather than a fixed count.

nprobe < 5 triggers more borderline detections (weaker initial top-k),
causing more bbox repairs and higher latency. nprobe > 5 spends more
time on the initial scan. Five clusters is the crossover point.

The FP=1 persists across all nprobe values, confirming it is not a recall
problem: the nearest 5 neighbors of that legit transaction are
genuinely fraud-majority regardless of probe depth.
