package net.karpelevitch.xel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import net.karpelevitch.l2.World;


class BitmapDraw implements World.RGBDraw {

    private final Bitmap bitmap;
    private float scale;
    private Canvas canvas;

    BitmapDraw(Canvas canvas, Bitmap bitmap, float scale) {
        this.canvas = canvas;
        this.bitmap = bitmap;
        this.scale = scale;
    }

    @Override
    public void drawMono(int i, int j, int b) {
        bitmap.setPixel(i, j, MainActivity.WHITE_RGB[b]);
//                            canvas.drawRect(i * pixels, j * pixels, (i + 1) * pixels - 1, (j + 1) * pixels - 1, WHITES[b]);
    }

    @Override
    public void drawColor(int i, int j, int color) {
        bitmap.setPixel(i, j, MainActivity.COLORS[color]);
//                            canvas.drawRect(i * pixels, j * pixels, (i + 1) * pixels - 1, (j + 1) * pixels - 1, PAINTS[color]);
    }

    @Override
    public void done() {

        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        canvas.drawColor(Color.DKGRAY);
        this.canvas.drawBitmap(bitmap, matrix, null);
    }
}
