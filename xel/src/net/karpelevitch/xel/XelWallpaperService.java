package net.karpelevitch.xel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
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
                    protected void draw(int[] totalEnergy) {
                        final Canvas canvas = surfaceHolder.lockCanvas(null);
                        try {

                            World.RGBDraw rgbDraw = new BitmapDraw(canvas, bitmap);
                            totalEnergy[0] = world.draw(true, rgbDraw);
                            rgbDraw.done();
                        } finally {
                            surfaceHolder.unlockCanvasAndPost(canvas);
                        }
                    }
                };
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
