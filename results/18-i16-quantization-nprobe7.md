# Result 18 — i16 quantization + nprobe=7

## Config
- Scoring: simple majority count (`fraud_count / k`)
- k=5, nprobe=7
- Index: full KMeans (n_init=1), vectors quantized to int16 (scale=8192)

## Results
| Metric | Value |
|--------|-------|
| p99 | 1260ms |
| FP | 1 |
| FN | 0 |
| weighted_errors_E | 1 |
| final_score | 2809.20 |

## Notes
i16 quantization improved precision dramatically (E=4→E=1, FN eliminated) but
latency jumped from 774ms→1260ms due to 14 individual ShortBuffer.get(int) calls
replacing the prior bulk FloatBuffer.get(). Detection score 2909.69 vs 2901 would
be a net win if latency recovers. Next: try nprobe=5 to reduce latency while
hoping E stays at 1.
