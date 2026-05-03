# Option 9: Direct wire (skip adapter chain) + nprobe=6

**Commit**: 6fb0fb3

## Changes
- Skip the entire adapter chain: compute float array directly from raw JSON-parsed map (wire keys)
- Eliminates 5+ intermediate map allocations and 1 HashSet allocation per request
- Uses `.contains` on the wire `Collection` (vector from JSON) instead of creating a new `Set`
- `vectorized-from-wire` added to `logic.fraud-score`; controller now receives raw `json-params`

## Results (1 run)

| Metric | Option 7 (best prev) | Option 9 |
|--------|----------------------|----------|
| p99 latency | ~570-580ms | 580.47ms |
| final_score | ~2875-2883 | 2874.98 |
| detection_score | 2638.76 | 2638.76 |
| p99_score | ~236-244 | 236.22 |
| weighted_errors_E | 15 | 15 |
| false_positives | 3 | 3 |
| false_negatives | 4 | 4 |

## Analysis
- Adapter chain removal is effectively neutral — scores are within run-to-run variance
- At 0.45 CPU / 150MB per instance, KNN scanning (~90% of CPU) still dominates
- The 5 map + 1 HashSet eliminations are negligible vs the KNN compute cost
- JSON parsing by Pedestal/Jackson still allocates full Clojure maps; those allocations dominate the non-KNN overhead
- Positive: code is simpler, one fewer layer of indirection

## Conclusion
Marginal/neutral. Keeps the cleaner code path but does not move the score.
