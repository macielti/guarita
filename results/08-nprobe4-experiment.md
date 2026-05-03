# Option 8: nprobe=4 experiment

**Commit**: (reverted before push)

## Changes
- Reduced nprobe from 6 to 4 (33% fewer clusters to scan vs nprobe=6)

## Results

| Metric | nprobe=6 (Opt7) | nprobe=4 |
|--------|-----------------|----------|
| p99 latency | ~570ms | lower |
| final_score | ~2878 | 2796 |
| weighted_errors_E | 15 | 45 |
| false_positives | 3 | ~15 |
| false_negatives | 4 | ~15 |

## Analysis
- nprobe=4 causes severe detection degradation: weighted_errors jump from 15 → 45
- Detection score drops significantly, more than offsetting any p99 gain
- final_score regression: 2878 → 2796 (-82 points)
- **Conclusion**: nprobe=6 remains the sweet spot; nprobe=4 too aggressive

## Reverted to nprobe=6
