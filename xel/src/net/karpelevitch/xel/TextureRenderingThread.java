package net.karpelevitch.xel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.TextureView;
import net.karpelevitch.l2.World;

public class TextureRenderingThread extends RenderingThread {
    protected final Bitmap bitmap;
    private final TextureView mSurface;

    public TextureRenderingThread(TextureView surface, Context ctx) {
        super(ctx, surface.getWidth() / MainActivity.XEL_SIZE, surface.getHeight() / MainActivity.XEL_SIZE);
        bitmap = Bitmap.createBitmap(this.size_x, this.size_y, Bitmap.Config.ARGB_8888);
        mSurface = surface;

    }

    protected void draw() {
        final Canvas canvas = mSurface.lockCanvas(null);
        try {

            World.RGBDraw rgbDraw = new BitmapDraw(canvas, bitmap);
            world.draw(true, rgbDraw);
            rgbDraw.done();
        } finally {
            mSurface.unlockCanvasAndPost(canvas);
        }
    }
}
