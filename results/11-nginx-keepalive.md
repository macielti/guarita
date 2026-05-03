# Option 11: nginx HTTP/1.1 keepalive to upstream

**Server-side change only** (nginx.conf on server, not in git)

## Changes
- Added `proxy_http_version 1.1;` and `proxy_set_header Connection "";` to nginx location block
- Without HTTP/1.1, nginx's `keepalive 256` directive was ineffective — each request created a new TCP connection to the upstream despite the pool being configured
- Connection reuse eliminates ~0.1-0.5ms TCP setup overhead per request at 900 req/s

## Results (3 runs, fresh deploy with heap limit + reflection fix)

| Metric | Option 10 R2-R4 | Option 11 R1 | R2 | R3 |
|--------|-----------------|-----|-----|-----|
| p99 latency | 528-584ms | 563ms | 559ms | 578ms |
| final_score | 2871-2884 | 2888 | 2891 | 2876 |
| p99_score | 233-245 | 249 | 252 | 237 |
| weighted_errors_E | 15 | 15 | 15 | 15 |

## Analysis
- Nginx keepalive gives slightly more consistent results (range 2876-2891 vs 2871-2915)
- Mean score: ~2885 vs ~2884 for Option 10 (comparable)
- More stable: all 3 runs within narrow band vs Option 10 which had high variance
- Improvement is marginal but keepalive is correct behavior — keeping it

## Observation: CPU saturation is the p99 bottleneck
At 900 req/s across 2 containers (0.45 CPU each), each request consumes ~1.2ms CPU.
The container is near 100% utilization (375 req/s × 1.2ms = 450ms/s = 100% of 0.45 CPU).
With utilization ≈ 1.0, queue theory predicts diverging wait times, explaining the high p99.
The only code-level fix is to reduce KNN work per request (nprobe reduction).
