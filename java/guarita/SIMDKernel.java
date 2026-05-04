package guarita;

import jdk.incubator.vector.*;

public final class SIMDKernel {

    // Use SPECIES_PREFERRED so GraalVM AOT picks the widest available ISA
    // (SPECIES_512 on AVX-512, SPECIES_256 on AVX2). With stride-16 arrays this
    // loop always executes either once (512-bit) or twice (256-bit).
    private static final VectorSpecies<Float>   FSP  = FloatVector.SPECIES_PREFERRED;
    // Short-to-float conversion always goes through 256-bit intermediates:
    //   ShortVector(16 shorts) -> 2 × IntVector(8 ints) -> 2 × FloatVector(8 floats)
    private static final VectorSpecies<Float>   FS8  = FloatVector.SPECIES_256;
    private static final VectorSpecies<Integer> IS8  = IntVector.SPECIES_256;
    private static final VectorSpecies<Short>   SS16 = ShortVector.SPECIES_256;
    private static final float SCALE_INV = 1.0f / 8192.0f;

    private SIMDKernel() {}

    /**
     * Squared Euclidean distance between c[off..off+15] and q[0..15].
     * Both arrays must be stride-16 padded (extra dims = 0).
     */
    public static float sqDistCentroids(float[] c, int off, float[] q) {
        FloatVector acc = FloatVector.zero(FSP);
        for (int i = 0; i < 16; i += FSP.length()) {
            FloatVector d = FloatVector.fromArray(FSP, c, off + i)
                                       .sub(FloatVector.fromArray(FSP, q, i));
            acc = d.fma(d, acc);
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    /**
     * Squared Euclidean distance between a stride-16 short[] (i16, scaled by 8192)
     * and a stride-16 float[] query.
     */
    public static float sqDistShorts(short[] s, float[] q) {
        ShortVector sv  = ShortVector.fromArray(SS16, s, 0);
        FloatVector fv0 = ((FloatVector) ((IntVector) sv.convertShape(VectorOperators.S2I, IS8, 0))
                              .convertShape(VectorOperators.I2F, FS8, 0)).mul(SCALE_INV);
        FloatVector fv1 = ((FloatVector) ((IntVector) sv.convertShape(VectorOperators.S2I, IS8, 1))
                              .convertShape(VectorOperators.I2F, FS8, 0)).mul(SCALE_INV);
        FloatVector d0  = fv0.sub(FloatVector.fromArray(FS8, q, 0));
        FloatVector d1  = fv1.sub(FloatVector.fromArray(FS8, q, 8));
        return d0.fma(d0, d1.fma(d1, FloatVector.zero(FS8))).reduceLanes(VectorOperators.ADD);
    }

    /**
     * Minimum possible squared distance from q to the bounding box [mn..mx].
     * mn and mx are stride-16 short[] arrays indexed by cluster offset off.
     */
    public static float bboxLowerSq(short[] mn, short[] mx, int off, float[] q) {
        ShortVector mn_sv = ShortVector.fromArray(SS16, mn, off);
        ShortVector mx_sv = ShortVector.fromArray(SS16, mx, off);
        FloatVector zero  = FloatVector.zero(FS8);

        FloatVector mn0 = ((FloatVector) ((IntVector) mn_sv.convertShape(VectorOperators.S2I, IS8, 0))
                             .convertShape(VectorOperators.I2F, FS8, 0)).mul(SCALE_INV);
        FloatVector mx0 = ((FloatVector) ((IntVector) mx_sv.convertShape(VectorOperators.S2I, IS8, 0))
                             .convertShape(VectorOperators.I2F, FS8, 0)).mul(SCALE_INV);
        FloatVector mn1 = ((FloatVector) ((IntVector) mn_sv.convertShape(VectorOperators.S2I, IS8, 1))
                             .convertShape(VectorOperators.I2F, FS8, 0)).mul(SCALE_INV);
        FloatVector mx1 = ((FloatVector) ((IntVector) mx_sv.convertShape(VectorOperators.S2I, IS8, 1))
                             .convertShape(VectorOperators.I2F, FS8, 0)).mul(SCALE_INV);

        FloatVector qv0 = FloatVector.fromArray(FS8, q, 0);
        FloatVector qv1 = FloatVector.fromArray(FS8, q, 8);

        // Per dim: max(0, mn-q) if q<mn; max(0, q-mx) if q>mx; 0 otherwise.
        // Only one branch can be positive per dim since mn<=mx, so adding is safe.
        FloatVector d0 = mn0.sub(qv0).max(zero).add(qv0.sub(mx0).max(zero));
        FloatVector d1 = mn1.sub(qv1).max(zero).add(qv1.sub(mx1).max(zero));

        return d0.fma(d0, d1.fma(d1, zero)).reduceLanes(VectorOperators.ADD);
    }
}
