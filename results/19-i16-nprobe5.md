# Result 19 — i16 quantization + nprobe=5

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=5, nprobe=5
- Index: full KMeans (n_init=1), vectors quantized to int16 (scale=8192)

## Results
| Metric | Value |
|--------|-------|
| p99 | 1285ms |
| FP | 2 |
| FN | 2 |
| weighted_errors_E | 8 |
| final_score | 2604.82 |

## Notes
Reducing nprobe from 7 to 5 hurt recall too much. E=8 (same as early
float32 experiments). The i16 latency advantage is not enough to compensate
for lost precision. nprobe=7 i16 (E=1) remains the best i16 result.
