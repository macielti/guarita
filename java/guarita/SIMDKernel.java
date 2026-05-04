package guarita;

import jdk.incubator.vector.*;

public final class SIMDKernel {

    // Hardcode 128-bit (SSE2) — compatible with all x86-64 CPUs including those
    // without AVX. SPECIES_PREFERRED is intentionally NOT used because it is
    // resolved at build time from the build machine's CPU (e.g. AVX-512 on a CI
    // runner) and gets baked into the native image, causing SIGILL on CPUs that
    // lack AVX (e.g. Intel Celeron J4105).
    private static final VectorSpecies<Float>   FS4 = FloatVector.SPECIES_128;  // 4 floats / 128-bit
    private static final VectorSpecies<Integer> IS4 = IntVector.SPECIES_128;    // 4 ints  / 128-bit
    private static final VectorSpecies<Short>   SS8 = ShortVector.SPECIES_128;  // 8 shorts / 128-bit
    private static final float SCALE_INV = 1.0f / 8192.0f;

    private SIMDKernel() {}

    /**
     * Squared Euclidean distance between c[off..off+15] and q[0..15].
     * Both arrays must be stride-16 padded (extra dims = 0.0).
     * 4 iterations × 4 floats = 16 floats total.
     */
    public static float sqDistCentroids(float[] c, int off, float[] q) {
        FloatVector acc = FloatVector.zero(FS4);
        for (int i = 0; i < 16; i += 4) {
            FloatVector d = FloatVector.fromArray(FS4, c, off + i)
                                       .sub(FloatVector.fromArray(FS4, q, i));
            acc = d.fma(d, acc);
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    /**
     * Squared Euclidean distance between a stride-16 short[] (i16, scaled by 8192)
     * and a stride-16 float[] query.
     * Loads 8 shorts at a time with SS8; splits into two groups of 4 via
     * convertShape part=0 (lower) and part=1 (upper).
     */
    public static float sqDistShorts(short[] s, float[] q) {
        FloatVector acc = FloatVector.zero(FS4);
        for (int i = 0; i < 16; i += 8) {
            ShortVector sv  = ShortVector.fromArray(SS8, s, i);
            FloatVector fv0 = ((FloatVector) ((IntVector) sv.convertShape(VectorOperators.S2I, IS4, 0))
                                  .convertShape(VectorOperators.I2F, FS4, 0)).mul(SCALE_INV);
            FloatVector fv1 = ((FloatVector) ((IntVector) sv.convertShape(VectorOperators.S2I, IS4, 1))
                                  .convertShape(VectorOperators.I2F, FS4, 0)).mul(SCALE_INV);
            FloatVector d0  = fv0.sub(FloatVector.fromArray(FS4, q, i));
            FloatVector d1  = fv1.sub(FloatVector.fromArray(FS4, q, i + 4));
            acc = d0.fma(d0, d1.fma(d1, acc));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    /**
     * Minimum possible squared distance from q to the bounding box [mn..mx].
     * mn and mx are stride-16 short[] arrays indexed by cluster offset off.
     */
    public static float bboxLowerSq(short[] mn, short[] mx, int off, float[] q) {
        FloatVector acc  = FloatVector.zero(FS4);
        FloatVector zero = FloatVector.zero(FS4);
        for (int i = 0; i < 16; i += 8) {
            ShortVector mn_sv = ShortVector.fromArray(SS8, mn, off + i);
            ShortVector mx_sv = ShortVector.fromArray(SS8, mx, off + i);
            for (int part = 0; part < 2; part++) {
                int qi = i + part * 4;
                FloatVector mn_fv = ((FloatVector) ((IntVector) mn_sv.convertShape(VectorOperators.S2I, IS4, part))
                                        .convertShape(VectorOperators.I2F, FS4, 0)).mul(SCALE_INV);
                FloatVector mx_fv = ((FloatVector) ((IntVector) mx_sv.convertShape(VectorOperators.S2I, IS4, part))
                                        .convertShape(VectorOperators.I2F, FS4, 0)).mul(SCALE_INV);
                FloatVector qv    = FloatVector.fromArray(FS4, q, qi);
                // Per dim: max(0, mn-q) if q<mn; max(0, q-mx) if q>mx; 0 otherwise.
                FloatVector d     = mn_fv.sub(qv).max(zero).add(qv.sub(mx_fv).max(zero));
                acc = d.fma(d, acc);
            }
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }
}
