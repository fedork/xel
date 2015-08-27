package net.karpelevitch.xel;

import android.content.Context;
import android.support.v8.renderscript.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static java.lang.Math.max;
import static java.lang.Math.min;

abstract class AndroidWorld extends World {

    public AndroidWorld(int size_x, int size_y, DataInputStream in) {
        super(size_x, size_y, MainActivity.PAINTS.length, in);
    }


    @Override
    protected EnergyField createEnergyField(final int size_x, final int size_y) {
        RenderScript rs = RenderScript.create(getCtx());
        Element f32 = Element.F32(rs);

//                    ScriptIntrinsic

        final ScriptIntrinsicConvolve3x3 script = ScriptIntrinsicConvolve3x3.create(rs, f32);
        float c22 = 0.2f;
        float c12 = (1.0f - c22) / 6.8f;
        float c11 = (1.0f - c22 - 4f * c12) / 4f;
        float[] coefficients = new float[]{
                c11, c12, c11,
                c12, c22, c12,
                c11, c12, c11
        };
        coefficients[4] = c22;
        script.setCoefficients(coefficients);
        Type xy = new Type.Builder(rs, f32).setX(size_x + 2).setY(size_y + 2).create();
//        Type xy = Type.createXY(rs, f32, size_x + 2, size_y + 2);
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

            @Override
            public void read(DataInputStream in, int version) throws IOException {
                int size = in.readInt();
                for (int i = 0; i < size; i++) {
                    float e = in.readFloat();
                    if (i < energy.length) {
                        energy[i] = e;
                    }
                }
            }

            @Override
            public void write(DataOutputStream out) throws IOException {
                out.writeInt(energy.length);
                for (float e : energy) {
                    out.writeFloat(e);
                }
            }
        };
    }

    protected abstract Context getCtx();
}
