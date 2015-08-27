package net.karpelevitch.xel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;
import android.view.TextureView;

public class TextureRenderingThread extends RenderingThread {

    protected final TextureView mSurface;
    private BitmapDraw rgbDraw;

    public TextureRenderingThread(TextureView surface, Context ctx, World world) {
        super(ctx, world, surface.getWidth(), surface.getHeight());
        mSurface = surface;
        rgbDraw = new BitmapDraw();
    }

    protected void draw(World world) {
        if (world == null) {
            Log.d("Xel", "World is NULL in TRT.draw !");
            return;
        }
        final Canvas canvas = mSurface.lockCanvas(null);
        if (canvas == null) return;
        try {
            Bitmap b = getBitmap(canvas);
            rgbDraw.setCanvas(canvas);
            rgbDraw.setBitmap(b);
            rgbDraw.setScale(scale);
            world.draw(true, rgbDraw, b.getWidth(), b.getHeight(), offsetX, offsetY);
            rgbDraw.done();
        } finally {
            mSurface.unlockCanvasAndPost(canvas);
        }
    }

}
