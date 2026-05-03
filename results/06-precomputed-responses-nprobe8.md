# Option 6: Precomputed responses + controller returns int

**Commits**: d06642c, 8240b5f

## Changes
- Controller returns raw fraud count (long) instead of a map — eliminates `{:approved, :fraud_score}` map per request
- Handler precomputes all 6 possible JSON response byte arrays at startup
- Response lookup is O(1) array index instead of Double/toString + getBytes + arraycopy

## Results (3 runs, all with default nginx and config)

| Metric | Option 2 | Option 6 R1 | Option 6 R2 | Option 6 R3 |
|--------|----------|-------------|-------------|-------------|
| p99 latency | 676.40ms | 785.08ms | 885.42ms | 913.93ms |
| final_score | 2846.04 | 2781.33 | 2729.10 | 2715.33 |

## Analysis
- Response precomputation didn't measurably improve over Options 3-4 (~780-810ms)
- Option 2's 676ms was likely a favorable variance run; steady state appears ~780-830ms
- Detecting true per-option improvements requires more runs or a controlled environment

## Configs tried (both worse than baseline):
- worker-threads=100: p99=1067ms (too much context switching)
- nginx HTTP/1.1 proxy_pass: p99=885ms (marginal, reverted)
