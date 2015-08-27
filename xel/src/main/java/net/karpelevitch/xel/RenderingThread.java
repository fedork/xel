package net.karpelevitch.xel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Log;

abstract class RenderingThread extends Thread {
    protected final World world;
    private final Context ctx;
    protected Bitmap bitmap = null;
    protected int offsetX = 0;
    protected int offsetY = 0;
    protected float scale;
    private float minScale = 3.0f;
    private volatile boolean mRunning = true;

    public RenderingThread(final Context ctx, World world, int width, int height) {
        this.ctx = ctx;
        this.world = world;
        scale = Math.max((float) width / world.size_x, (float) height / world.size_y);
    }

    @Override
    public void run() {
        Log.d("Xel", "RenderingThread.run()");

        try {
            while (mRunning && !Thread.interrupted()) {
                long nextFrame = System.currentTimeMillis() + MainActivity.FRAME_INTERVAL;
                long maxNextFrame = nextFrame + MainActivity.MAX_FRAME_INTERVAL;
                synchronized (World.class) {
                    if (mRunning && !Thread.interrupted()) {
                        draw(world);
                    }
                }
                long sleepTime;
                sleepTime = Math.max(5L, nextFrame - System.currentTimeMillis());
                Thread.sleep(sleepTime);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected abstract void draw(World world);

    void stopRendering() {
        mRunning = false;
        interrupt();
        try {
            join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean scroll(float distanceX, float distanceY) {
        int dx = (int) (distanceX / scale);
        int dy = (int) (distanceY / scale);
        if (dx != 0 || dy != 0) {
            move(dx, dy);
            return true;
        }
        return false;
    }

    void move(int dx, int dy) {
        offsetX += dx;
        while (offsetX < 0) offsetX += world.size_x;
        offsetX %= world.size_x;

        offsetY += dy;
        while (offsetY < 0) offsetY += world.size_y;
        offsetY %= world.size_y;
    }

    public boolean zoom(float scaleFactor, float focusX, float focusY) {
        float newScale = Math.min(20.0f, Math.max(minScale, Math.round(10 * scale * scaleFactor) / 10.0f));
        if (Math.abs(newScale - scale) > 0.05f) {
            move((int) (focusX / scale - focusX / newScale), (int) (focusY / scale - focusY / newScale));
            scale = newScale;
            return true;
        }
        return false;
    }

    public Bitmap getBitmap(Canvas canvas) {
        minScale = Math.max((float) canvas.getWidth() / world.size_x, (float) canvas.getHeight() / world.size_y);
        if (scale < minScale) {
            scale = minScale;
        }
        int w = Math.min((int) (canvas.getWidth() / scale), world.size_x);
        int h = Math.min((int) (canvas.getHeight() / scale), world.size_y);
        if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
            Log.d("Xel", "Creating new bitmap:" + w + ", " +h);
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
        return bitmap;
    }

}
