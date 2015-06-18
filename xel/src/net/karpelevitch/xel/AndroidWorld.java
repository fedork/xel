package net.karpelevitch.xel;

import android.content.Context;
import android.renderscript.*;
import net.karpelevitch.l2.EnergyField;
import net.karpelevitch.l2.World;

import java.util.Arrays;

import static java.lang.Math.max;
import static java.lang.Math.min;

abstract class AndroidWorld extends World {

    public AndroidWorld(int size_x, int size_y) {
        super(size_x, size_y, MainActivity.PAINTS.length);
    }

    @Override
    protected EnergyField createEnergyField(final int size_x, final int size_y) {
        RenderScript rs = RenderScript.create(getCtx());
        Element f32 = Element.F32(rs);

//                    ScriptIntrinsic

        final ScriptIntrinsicConvolve3x3 script = ScriptIntrinsicConvolve3x3.create(rs, f32);
        float c22 = 0.2f;
        float cx = (1.0f - c22) / 8;
        float[] coefficients = new float[9];
        Arrays.fill(coefficients, cx);
        coefficients[4] = c22;
        script.setCoefficients(coefficients);
        Type xy = Type.createXY(rs, f32, size_x + 2, size_y + 2);
        final Allocation ain = Allocation.createTyped(rs, xy);
        final Allocation aout = Allocation.createTyped(rs, xy);
        return new EnergyField() {
            final float[] energy = new float[(size_x + 2) * (size_y + 2)];

            @Override
            public void putEnergy(int coords, int e) {
                int newcoords = coords / size_x * 2 + coords + size_x + 3;
                energy[newcoords] = max(0, min(MAX_ENERGY, energy[newcoords] + e));
            }

            @Override
            public int readEnergy(int coords) {
                return (int) energy[coords / size_x * 2 + coords + size_x + 3];
            }

            @Override
            public void diffuse(World world) {
                ain.copyFrom(energy);
                ain.copy2DRangeFrom(0, 1, 1, size_y, ain, size_x, 1);
                ain.copy2DRangeFrom(size_x + 1, 1, 1, size_y, ain, 1, 1);
                ain.copy2DRangeFrom(0, 0, size_x + 2, 1, ain, 0, size_y);
                ain.copy2DRangeFrom(0, size_y + 1, size_x + 2, 1, ain, 0, 1);
                script.setInput(ain);
                script.forEach(aout);
                aout.copyTo(energy);

            }
        };
    }

    protected abstract Context getCtx();
}
