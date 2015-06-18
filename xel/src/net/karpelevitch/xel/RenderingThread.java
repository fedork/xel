package net.karpelevitch.xel;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import net.karpelevitch.l2.World;

abstract class RenderingThread extends Thread {
    protected final int size_x;
    protected final int size_y;
    final World world;
    private volatile boolean mRunning = true;

    public RenderingThread(final Context ctx, int size_x, int size_y) {
        this.size_x = size_x;
        this.size_y = size_y;
        world = new AndroidWorld(this.size_x, this.size_y) {
            @Override
            protected Context getCtx() {
                return ctx;
            }
        };
    }

    @Override
    public void run() {

//            Paint paint = new Paint();
//            paint.setColor(0xff00ff00);
        long startTime = System.currentTimeMillis();
        int gen = 0;
        int frames = 0;
        int[] totalEnergy = new int[1];
        int[] maxage = new int[1];

//            diffuse();


        while (mRunning && !Thread.interrupted()) {
            long nextFrame = System.currentTimeMillis();
            long maxNextFrame = nextFrame + MainActivity.MAX_FRAME_INTERVAL;
            draw(totalEnergy);
            long sleepTime;
            do {
                maxage[0] = world.update();
                nextFrame += MainActivity.FRAME_INTERVAL;
                gen++;
                frames++;
            }
            while (0 > (sleepTime = nextFrame - System.currentTimeMillis()) && System.currentTimeMillis() < maxNextFrame);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
            long now;
            if ((now = System.currentTimeMillis()) - startTime > 100) {
                long totalMemory = Runtime.getRuntime().totalMemory();
                long maxMemory = Runtime.getRuntime().maxMemory();
                long freeMem = Runtime.getRuntime().freeMemory();
                double freePercent = 100.0 * freeMem / totalMemory;
//                    Runtime.getRuntime().gc();
                Log.d("Xel", String.format("%d \t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%f\n", gen, frames * 1000 / (now - startTime), world.list.size(), totalEnergy[0], maxage[0], world.maxgen, freeMem, totalMemory, maxMemory, freePercent));
                startTime = System.currentTimeMillis();
//                    startTime = now;
                frames = 0;
            }
        }
    }

    protected abstract void draw(int[] totalEnergy);

    void stopRendering() {
        interrupt();
        mRunning = false;
    }

    private class CanvasDraw implements World.RGBDraw {
        private final int pixels;
        private Canvas canvas;

        private CanvasDraw(Canvas canvas) {
            this.canvas = canvas;
            pixels = MainActivity.XEL_SIZE;
        }

        @Override
        public void drawMono(int i, int j, int b) {
            canvas.drawRect(i * pixels, j * pixels, (i + 1) * pixels/* - 1*/, (j + 1) * pixels /*- 1*/, MainActivity.WHITES[b]);
        }

        @Override
        public void drawColor(int i, int j, int color) {
            canvas.drawRect(i * pixels, j * pixels, (i + 1) * pixels/* - 1*/, (j + 1) * pixels /*- 1*/, MainActivity.PAINTS[color]);
        }

        @Override
        public void done() {
        }
    }

}
