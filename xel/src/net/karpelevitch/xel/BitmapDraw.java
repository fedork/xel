package net.karpelevitch.xel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import net.karpelevitch.l2.World;

import static java.lang.Math.min;


class BitmapDraw implements World.RGBDraw {

    private final Bitmap bitmap;
    private Canvas canvas;

    BitmapDraw(Canvas canvas, Bitmap bitmap) {
        this.canvas = canvas;
        this.bitmap = bitmap;
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

        float scale = (float) min(canvas.getWidth(), canvas.getHeight()) / bitmap.getWidth();
        matrix.setScale(scale, scale);
        this.canvas.drawBitmap(bitmap, matrix, null);
    }
}
