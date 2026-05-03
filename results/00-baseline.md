# Baseline Results

**Commit**: a41f3b8 (use gc1)

## Configuration
- k: 5, nprobe: 16, threshold: 0.6
- 2 API instances (0.45 CPU / 150MB each) + nginx (0.1 CPU)

## Results

| Metric | Value |
|--------|-------|
| p99 latency | 1312.58ms |
| final_score | 2701.26 |
| detection_score | 2819.38 |
| p99_score | -118.12 |
| weighted_errors_E | 3 |
| false_positives | 0 |
| false_negatives | 1 |
| http_errors | 0 |
| failure_rate | 0% |

## Notes
- No HTTP errors, excellent precision (1 FN, 0 FP)
- p99 is high (1312ms) — main opportunity for improvement
- CPU-bound: 0.9 total CPU for ~900 req/s
