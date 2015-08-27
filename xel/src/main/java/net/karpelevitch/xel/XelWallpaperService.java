package net.karpelevitch.xel;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.IBinder;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;

public class XelWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new XelWallpeperEngine();
    }

    private class XelWallpeperEngine extends Engine implements ServiceConnection {

        private RenderingThread mThread;
        private SurfaceHolder surfaceHolder;
        private XelWorldService xelService;
        private GestureDetector gestureDetector;
        private ScaleGestureDetector scaleGestureDetector;
        private int lastPixelOffset = 0;

        @Override
        public void onCreate(final SurfaceHolder surfaceHolder) {
            this.surfaceHolder = surfaceHolder;
            Log.d("Xel", "Wallpaper.onCreate: " + this.surfaceHolder.getSurfaceFrame().width() + ", " + this.surfaceHolder.getSurfaceFrame().height());
            super.onCreate(this.surfaceHolder);
        }

        private void createRenderingThread(final int width, final int height, World world) {
            Log.d("Xel", "createRenderingThread: " + width + ", " + height);
            if (mThread != null) {
                mThread.stopRendering();
                mThread = null;
            }
            if (width > 0 && height > 0) {
                mThread = new RenderingThread(XelWallpaperService.this, world, width, height) {

                    private final BitmapDraw rgbDraw = new BitmapDraw();

                    @Override
                    protected void draw(World world) {
                        final Canvas canvas = surfaceHolder.lockCanvas(null);
                        try {
                            Bitmap b = getBitmap(canvas);
                            rgbDraw.setCanvas(canvas);
                            rgbDraw.setBitmap(b);
                            rgbDraw.setScale(scale);
                            world.draw(true, rgbDraw, b.getWidth(), b.getHeight(), offsetX, offsetY);
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
            if (mThread != null) {
                mThread.move(lastPixelOffset - xPixelOffset, 0);
                lastPixelOffset = xPixelOffset;
            }
        }

/*
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
//                    this.mThread.world.
            }
        }
*/

        @Override
        public void onVisibilityChanged(boolean visible) {
            Log.d("Xel", "Wallpaper.onVisibilityChanged: " + visible);
            super.onVisibilityChanged(visible);
            if (visible) {
                if (mThread == null || mThread.getState() != Thread.State.NEW) {
                    Log.d("Xel", "mThread.getState()");
                    bindService(new Intent(XelWallpaperService.this, XelWorldService.class), this, BIND_AUTO_CREATE);
                }
            } else {
                del();
            }
        }

        private void del() {
            if (mThread != null) mThread.stopRendering();
            mThread = null;
            gestureDetector = null;
            scaleGestureDetector = null;
            try {
                unbindService(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
            xelService = null;
        }



        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d("Xel", "onSurfaceChanged: " + width + ", " + height + ", " + format);
            super.onSurfaceChanged(holder, format, width, height);
            this.surfaceHolder = holder;

        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            boolean result = scaleGestureDetector != null && scaleGestureDetector.onTouchEvent(event) | (gestureDetector != null && gestureDetector.onTouchEvent(event));
//            System.out.println("result = " + result);
        }


        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d("Xel", "on service connected name = " + name + " \tservice = " + service);
            xelService = ((XelWorldService.LocalBinder) service).getService();
            World world;
            do {
                synchronized (xelService) {
                    world = xelService.getWorld();
                    if (world != null) break;
                    try {
                        xelService.wait(1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } while (true);
            createRenderingThread(surfaceHolder.getSurfaceFrame().width(), surfaceHolder.getSurfaceFrame().height(), world);
            mThread.start();
            GestureDetector.SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    return mThread.scroll(distanceX, distanceY);
                }
            };
            gestureDetector = new GestureDetector(XelWallpaperService.this, gestureListener);
            scaleGestureDetector = new ScaleGestureDetector(XelWallpaperService.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    return mThread.zoom(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d("Xel", "wallpaper.onServiceDisconnected name = " + name);
            del();
        }
    }
}
