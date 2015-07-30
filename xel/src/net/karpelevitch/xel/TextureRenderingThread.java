package net.karpelevitch.xel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.TextureView;
import net.karpelevitch.l2.World;

public class TextureRenderingThread extends RenderingThread {

    protected final TextureView mSurface;

    public TextureRenderingThread(TextureView surface, Context ctx, World world) {
        super(ctx, world, surface.getWidth(), surface.getHeight());
        mSurface = surface;
    }

    protected void draw(World world) {
        final Canvas canvas = mSurface.lockCanvas(null);
        try {
            Bitmap b = getBitmap(canvas);
            World.RGBDraw rgbDraw = new BitmapDraw(canvas, b, scale);
            world.draw(true, rgbDraw, b.getWidth(), b.getHeight(), offsetX, offsetY);
            rgbDraw.done();
        } finally {
            mSurface.unlockCanvasAndPost(canvas);
        }
    }

}
