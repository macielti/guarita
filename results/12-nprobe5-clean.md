# Option 12: nprobe=5 (clean — no profiler, reflection fixed, heap limited)

**Commit**: 5f3aa81

## Context
Previous nprobe=5 tests (commit 00e9f61) were contaminated by:
1. `prof/serve-ui` running at port 8080 (consuming container CPU)
2. Reflection bug on `known-merchants` `.contains` call

Both are now fixed. Testing nprobe=5 with clean baseline.

## Changes (vs nprobe=6 baseline)
- nprobe: 6 → 5 (17% fewer cluster scans per request)
- All other changes from Options 9-11 retained

## Results (4 runs after fresh deploy)

| Metric | nprobe=6 stable | nprobe=5 R1 | R2 | R3 | R4 |
|--------|----------------|-----|-----|-----|-----|
| p99 latency | ~528-584ms | 536ms | 565ms | 561ms | 574ms |
| final_score | ~2876-2915 | 2908 | 2886 | 2889 | 2879 |
| detection_score | 2638.76 | 2638.76 | 2638.76 | 2638.76 | 2638.76 |
| weighted_errors_E | 15 | 15 | 15 | 15 | 15 |
| false_positives | 3 | 3 | 3 | 3 | 3 |
| false_negatives | 4 | 4 | 4 | 4 | 4 |

## Key finding: detection accuracy is identical
nprobe=5 and nprobe=6 produce exactly the same detection results (3 FP, 4 FN, 15 weighted_errors).
The 6th cluster scan was adding zero value to accuracy. nprobe=5 is strictly better: same accuracy, less CPU work.

## Score comparison: nprobe=5 vs nprobe=6
- nprobe=5 mean: ~2890 (range 2879-2908)
- nprobe=6 mean: ~2888 (range 2876-2915)
- Difference: within noise; nprobe=5 slightly tighter variance

## Why p99 improvement from nprobe reduction is limited
At 0.45 CPU / container and 450 req/s target, utilization ≈ 100%.
Reducing nprobe by 17% reduces per-request CPU by ~17%, but:
- p99 is dominated by OS CFS scheduling (Docker CPU throttling)
- At near-100% CPU utilization, even small variability causes queue buildup → high p99
- p99 range remains 530-575ms regardless of nprobe=5 vs nprobe=6
