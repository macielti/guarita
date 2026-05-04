# Baseline — sha-0539821

**Image:** `ghcr.io/macielti/guarita:main` (commit `0539821`)
**Config:** `IVF_NPROBE=3`, `k=5`, staged probe + bbox repair

## Scores

| Metric | Value |
|---|---|
| final_score | **2965.9** |
| p99 | 878.60 ms |
| p99_score | +56.21 |
| detection_score | 2909.69 |
| weighted_errors_E | 1 |
| failure_rate | 0% |
| http_errors | 0 |

## Breakdown

| | Count |
|---|---|
| true_positives | 14233 |
| true_negatives | 17840 |
| false_positives | 1 |
| false_negatives | 0 |

## Notes

- Scalar distance computations in Clojure (14-dim unrolled loops)
- No mmap prewarming at startup
- This run was done after a clean container restart (cold mmap, warmed during run)
