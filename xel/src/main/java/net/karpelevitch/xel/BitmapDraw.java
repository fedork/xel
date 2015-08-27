package net.karpelevitch.xel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;


class BitmapDraw implements World.RGBDraw {

    private Bitmap bitmap;
    private Canvas canvas;
    private final Matrix matrix;

    BitmapDraw() {
        matrix = new Matrix();
    }

    @Override
    public void drawMono(int i, int j, int b) {
        bitmap.setPixel(i, j, MainActivity.WHITE_RGB[b]);
    }

    @Override
    public void drawColor(int i, int j, int color) {
        bitmap.setPixel(i, j, MainActivity.COLORS[color]);
    }

    @Override
    public void done() {
        canvas.drawColor(Color.BLACK);
        this.canvas.drawBitmap(bitmap, matrix, null);
    }

    public void setCanvas(Canvas canvas) {
        this.canvas = canvas;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public void setScale(float scale) {
        matrix.setScale(scale, scale);
    }
}
