# Option 5: Inline vectorized + nprobe=16

**Commit**: 840716d

## Changes
- Inlined the vectorized function: extract all values once, compute 14 features without 14 separate function calls and their map destructuring
- Restored nprobe=16 to recover baseline accuracy (hypothesis: schema removal compensates for latency)

## Results

| Metric | Baseline | Option 2 (nprobe=8) | Option 5 (nprobe=16) |
|--------|----------|---------------------|----------------------|
| p99 latency | 1312.58ms | 676.40ms | 1189.20ms |
| final_score | 2701.26 | 2846.04 | 2744.13 |
| detection_score | 2819.38 | 2676.25 | 2819.38 |
| p99_score | -118.12 | 169.80 | -75.25 |
| weighted_errors_E | 3 | 11 | 3 |
| false_positives | 0 | 2 | 0 |
| false_negatives | 1 | 3 | 1 |

## Analysis
- nprobe=16 restores baseline accuracy (0 FP, 1 FN, weighted_errors=3) ✓
- BUT p99 is 1189ms even with all schema validation removed — KNN scanning dominates
- Schema removal only saved ~123ms off 1312ms baseline with nprobe=16 (~9%)
- KNN scanning is ~90% of request CPU time at nprobe=16

## Conclusion
- **nprobe=8 wins**: -143 detection penalty is worth +245 p99_score improvement
- nprobe=16 option: final_score 2744 vs nprobe=8's 2846
- Inline vectorization is marginal since KNN dominates
- Keep nprobe=8 going forward; focus on other optimizations
