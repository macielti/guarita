package guarita;

import jdk.incubator.vector.*;
import java.nio.ShortBuffer;

public final class SIMDKernel {

    // Hardcode 256-bit (AVX2) — the competition target is a Mac Mini Late 2014
    // (Haswell), which supports AVX2. SPECIES_PREFERRED is intentionally NOT used
    // because it is resolved at build time from the build machine's CPU (e.g.
    // AVX-512 on a CI runner) and gets baked into the native image, causing SIGILL
    // on CPUs that lack AVX-512 (e.g. Haswell).
    private static final VectorSpecies<Float>   FS8  = FloatVector.SPECIES_256;   // 8 floats / 256-bit
    private static final VectorSpecies<Integer> IS8  = IntVector.SPECIES_256;     // 8 ints   / 256-bit
    private static final VectorSpecies<Short>   SS16 = ShortVector.SPECIES_256;   // 16 shorts / 256-bit
    private static final float SCALE_INV = 1.0f / 8192.0f;

    private SIMDKernel() {}

    /**
     * Squared Euclidean distance between c[off..off+15] and q[0..15].
     * Both arrays must be stride-16 padded (extra dims = 0.0).
     * 2 iterations × 8 floats = 16 floats total.
     */
    public static float sqDistCentroids(float[] c, int off, float[] q) {
        FloatVector acc = FloatVector.zero(FS8);
        for (int i = 0; i < 16; i += 8) {
            FloatVector d = FloatVector.fromArray(FS8, c, off + i)
                                       .sub(FloatVector.fromArray(FS8, q, i));
            acc = d.fma(d, acc);
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    /**
     * Squared Euclidean distance between a stride-16 short[] (i16, scaled by 8192)
     * and a stride-16 float[] query.
     * Loads all 16 shorts at once with SS16; splits into two groups of 8 via
     * convertShape part=0 (lower) and part=1 (upper).
     */
    public static float sqDistShorts(short[] s, float[] q) {
        ShortVector sv  = ShortVector.fromArray(SS16, s, 0);
        FloatVector fv0 = ((FloatVector) ((IntVector) sv.convertShape(VectorOperators.S2I, IS8, 0))
                              .convertShape(VectorOperators.I2F, FS8, 0)).mul(SCALE_INV);
        FloatVector fv1 = ((FloatVector) ((IntVector) sv.convertShape(VectorOperators.S2I, IS8, 1))
                              .convertShape(VectorOperators.I2F, FS8, 0)).mul(SCALE_INV);
        FloatVector d0  = fv0.sub(FloatVector.fromArray(FS8, q, 0));
        FloatVector d1  = fv1.sub(FloatVector.fromArray(FS8, q, 8));
        FloatVector acc = d0.fma(d0, d1.fma(d1, FloatVector.zero(FS8)));
        return acc.reduceLanes(VectorOperators.ADD);
    }

    /**
     * Minimum possible squared distance from q to the bounding box [mn..mx].
     * mn and mx are stride-16 short[] arrays indexed by cluster offset off.
     */
    public static float bboxLowerSq(short[] mn, short[] mx, int off, float[] q) {
        FloatVector acc  = FloatVector.zero(FS8);
        FloatVector zero = FloatVector.zero(FS8);
        ShortVector mn_sv = ShortVector.fromArray(SS16, mn, off);
        ShortVector mx_sv = ShortVector.fromArray(SS16, mx, off);
        for (int part = 0; part < 2; part++) {
            int qi = part * 8;
            FloatVector mn_fv = ((FloatVector) ((IntVector) mn_sv.convertShape(VectorOperators.S2I, IS8, part))
                                    .convertShape(VectorOperators.I2F, FS8, 0)).mul(SCALE_INV);
            FloatVector mx_fv = ((FloatVector) ((IntVector) mx_sv.convertShape(VectorOperators.S2I, IS8, part))
                                    .convertShape(VectorOperators.I2F, FS8, 0)).mul(SCALE_INV);
            FloatVector qv    = FloatVector.fromArray(FS8, q, qi);
            // Per dim: max(0, mn-q) if q<mn; max(0, q-mx) if q>mx; 0 otherwise.
            FloatVector d     = mn_fv.sub(qv).max(zero).add(qv.sub(mx_fv).max(zero));
            acc = d.fma(d, acc);
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    // Argmax over top-k doubles — updates worst[0] with index of the maximum.
    private static void updateWorst(double[] topDist, int[] worst, int k) {
        double maxD = Double.NEGATIVE_INFINITY;
        int    maxJ = 0;
        for (int j = 0; j < k; j++) {
            double d = topDist[j];
            if (d > maxD) { maxD = d; maxJ = j; }
        }
        worst[0] = maxJ;
    }

    // Public entry point: allocates one short[16] per call, delegates to knnRangeImpl.
    public static void knnRange(ShortBuffer vectors, float[] query,
                                int start, int end, int k,
                                long[] topIdx, double[] topDist, int[] worst) {
        short[] buf = new short[16];
        knnRangeImpl(vectors, query, start, end, k, topIdx, topDist, worst, buf);
    }

    // Inner loop: reuses caller-supplied buf — avoids one allocation per cluster in bboxRepairScan.
    private static void knnRangeImpl(ShortBuffer vectors, float[] query,
                                     int start, int end, int k,
                                     long[] topIdx, double[] topDist, int[] worst,
                                     short[] buf) {
        for (int i = start; i < end; i++) {
            double w = topDist[worst[0]];
            vectors.get(i * 14, buf, 0, 14);
            double d = sqDistShorts(buf, query);
            if (d < w) {
                int wi = worst[0];
                topIdx[wi]  = i;
                topDist[wi] = d;
                updateWorst(topDist, worst, k);
            }
        }
    }

    // Centroid selection: absorbs topn-clusters! + per-centroid updateWorst call.
    // c * 16 matches the simd-stride constant in dataset.clj.
    public static void topkClusters(float[] centroids, float[] query, int nlist, int nprobe,
                                    int[] clIds, double[] clDist, int[] clWorst) {
        for (int c = 0; c < nlist; c++) {
            double d = sqDistCentroids(centroids, c * 16, query);
            if (d < clDist[clWorst[0]]) {
                int wi = clWorst[0];
                clIds[wi]  = c;
                clDist[wi] = d;
                updateWorst(clDist, clWorst, nprobe);
            }
        }
    }

    // Bbox repair scan: absorbs the repair loop from knn-ivf-fraud-count.
    // Allocates one buf for the entire sweep; calls knnRangeImpl per matching cluster.
    public static void bboxRepairScan(ShortBuffer vectors, float[] query,
                                      short[] bboxMin, short[] bboxMax,
                                      int[] offsets, int nlist, int k,
                                      byte[] visited, long[] topIdx, double[] topDist,
                                      int[] worst) {
        short[] buf = new short[16];
        for (int c = 0; c < nlist; c++) {
            if (visited[c] != 0) continue;
            double w  = topDist[worst[0]];
            float  lb = bboxLowerSq(bboxMin, bboxMax, c * 16, query);
            if (lb <= w) {
                knnRangeImpl(vectors, query, offsets[c], offsets[c + 1],
                             k, topIdx, topDist, worst, buf);
            }
        }
    }
}
