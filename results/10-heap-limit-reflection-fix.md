# Option 10: Heap limit + reflection type-hint fix

## Changes
1. **Reflection fix**: `^java.util.Collection` type hint was placed on the *expression* (`known-merchants ^java.util.Collection (:known_merchants customer)`) instead of the *binding symbol* (`^java.util.Collection known-merchants ...`). This caused `.contains` to dispatch via reflection on every request.
2. **Heap limit** (server-side docker-compose): API containers were using 146-147MB / 150MB limit, causing constant GC pressure. Added `-Xmx110m -Xms32m` to cap the JVM heap.

## Results (4 runs after fresh deploy)

| Metric | Option 7 stable | R1 | R2 | R3 | R4 |
|--------|----------------|-----|-----|-----|-----|
| p99 latency | ~570-580ms | 584ms | 568ms | 528ms | 575ms |
| final_score | ~2875-2883 | 2871 | **2884** | **2915** | 2878 |
| detection_score | 2638.76 | 2638.76 | 2638.76 | 2638.76 | 2638.76 |
| p99_score | ~236-244 | 233 | 245 | 276 | 239 |
| weighted_errors_E | 15 | 15 | 15 | 15 | 15 |

## Analysis
- Stable range: 2871-2915, mean ~2887 (vs Option 7's ~2879)
- Run 3 at 528ms / 2915 is the new best result
- Reflection on `.contains` was adding per-request overhead (reflection via JNI is ~100-500ns per call × 900 req/s = ~100K overhead)
- Memory reduction from 147MB → 84-86MB at idle; G1GC now has headroom to work properly
- First run (584ms) has more variance as system warms up; runs 2-4 more consistent

## Memory before/after heap limit
- Before: 146-147MB / 150MB (maxed out, constant full GC)
- After: ~84-86MB at idle, grows under load but stays well under 150MB
