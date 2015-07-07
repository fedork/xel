package net.karpelevitch.xel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import net.karpelevitch.l2.World;

public class XelWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new XelWallpeperEngine();
    }

    private class XelWallpeperEngine extends Engine {

        private RenderingThread renderingThread;
        private SurfaceHolder surfaceHolder;
        private int offsetX;

        @Override
        public void onCreate(final SurfaceHolder surfaceHolder) {
            this.surfaceHolder = surfaceHolder;
            Log.d("Xel", "Wallpaper.onCreate: " + this.surfaceHolder.getSurfaceFrame().width() + ", " + this.surfaceHolder.getSurfaceFrame().height());
            super.onCreate(this.surfaceHolder);
            Rect surfaceFrame = this.surfaceHolder.getSurfaceFrame();
            createRenderingThread(surfaceFrame.width(), surfaceFrame.height());
        }

        private void createRenderingThread(final int width, final int height) {
            Log.d("Xel", "createRenderingThread: " + width + ", " + height);
            if (renderingThread != null) {
                renderingThread.stopRendering();
            }
            if (width > 0 && height > 0) {
                renderingThread = new RenderingThread(XelWallpaperService.this, width / MainActivity.XEL_SIZE, height / MainActivity.XEL_SIZE) {
                    public Bitmap bitmap = Bitmap.createBitmap(size_x, size_y, Bitmap.Config.ARGB_8888);

                    @Override
                    protected void draw() {
                        final Canvas canvas = surfaceHolder.lockCanvas(null);
                        try {

                            World.RGBDraw rgbDraw = new BitmapDraw(canvas, bitmap);
                            int width = canvas.getWidth() / MainActivity.XEL_SIZE;
                            int height = canvas.getHeight() / MainActivity.XEL_SIZE;
                            world.draw(true, rgbDraw, width, height, -offsetX / MainActivity.XEL_SIZE, 0);
                            rgbDraw.done();
                        } finally {
                            surfaceHolder.unlockCanvasAndPost(canvas);
                        }
                    }
                };
            }
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
            System.out.println("xOffset = " + xOffset);
            System.out.println("yOffset = " + yOffset);
            System.out.println("xOffsetStep = " + xOffsetStep);
            System.out.println("yOffsetStep = " + yOffsetStep);
            System.out.println("xPixelOffset = " + xPixelOffset);
            System.out.println("yPixelOffset = " + yPixelOffset);
            this.offsetX = xPixelOffset;
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            int actionMasked = event.getActionMasked();
            int actionIndex = event.getActionIndex();
            float pressure = event.getPressure(actionIndex);
            System.out.println("actionMasked = " + actionMasked);
            System.out.println("actionIndex = " + actionIndex);
            System.out.println("pressure = " + pressure);
            switch (actionMasked) {
//                MotionEvent.ACTION_DOWN:
//                    this.renderingThread.world.
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.d("Xel", "Wallpaper.onVisibilityChanged: " + visible);
            super.onVisibilityChanged(visible);
            if (visible) {
                Log.d("Xel", "renderingThread.getState() = " + renderingThread.getState());
                if (renderingThread.getState() != Thread.State.NEW) {
                    createRenderingThread(surfaceHolder.getSurfaceFrame().width(), surfaceHolder.getSurfaceFrame().height());
                }
                renderingThread.start();
            } else {
                renderingThread.stopRendering();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d("Xel", "onSurfaceChanged: " + width + ", " + height + ", " + format);
            super.onSurfaceChanged(holder, format, width, height);
            this.surfaceHolder = holder;
            createRenderingThread(width, height);
        }
    }
}
