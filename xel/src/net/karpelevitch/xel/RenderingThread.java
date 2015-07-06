package net.karpelevitch.xel;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AtomicFile;
import android.util.Log;
import net.karpelevitch.l2.World;

import java.io.*;

abstract class RenderingThread extends Thread {
    private static final String FILE_NAME = "xel_state";
    private static final long SAVE_INTERVAL = 120000L;
    protected final int size_x;
    protected final int size_y;
    final World world;
    private final Context ctx;
    private volatile boolean mRunning = true;

    public RenderingThread(final Context ctx, int size_x, int size_y) {
        this.ctx = ctx;
        this.size_x = size_x;
        this.size_y = size_y;
        synchronized (World.class) {
            DataInputStream in = null;
            try {
                Log.d("Xel", "attempting to restore from file");
                in = new DataInputStream(new BufferedInputStream(new AtomicFile(ctx.getFileStreamPath(FILE_NAME)).openRead()));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            world = new AndroidWorld(this.size_x, this.size_y, in) {
                @Override
                protected Context getCtx() {
                    return ctx;
                }
            };
        }
    }

    @Override
    public void run() {

//            Paint paint = new Paint();
//            paint.setColor(0xff00ff00);
        long startTime = System.currentTimeMillis();
        int gen = 0;
        int frames = 0;
        int[] maxage = new int[1];

//            diffuse();

        long nextSave = 0L;
        while (mRunning && !Thread.interrupted()) {
            long nextFrame = System.currentTimeMillis();
            long maxNextFrame = nextFrame + MainActivity.MAX_FRAME_INTERVAL;
            draw();
            if (System.currentTimeMillis() > nextSave) {
                if (nextSave > 0) {
                    saveState();
                }
                nextSave = System.currentTimeMillis() + SAVE_INTERVAL;
            }
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
            if ((now = System.currentTimeMillis()) - startTime > 10000) {
                long totalMemory = Runtime.getRuntime().totalMemory();
                long maxMemory = Runtime.getRuntime().maxMemory();
                long freeMem = Runtime.getRuntime().freeMemory();
                double freePercent = 100.0 * freeMem / totalMemory;
//                    Runtime.getRuntime().gc();
                Log.d("Xel", String.format("%d \t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%f\n", gen, frames * 1000 / (now - startTime), world.list.size(), maxage[0], world.maxgen, freeMem, totalMemory, maxMemory, freePercent));
                startTime = System.currentTimeMillis();
//                    startTime = now;
                frames = 0;
            }
        }
        saveState();
    }

    void saveState() {
        AtomicFile file = new AtomicFile(ctx.getFileStreamPath(FILE_NAME));
        FileOutputStream out = null;
        try {
            synchronized (World.class) {
                Log.d("Xel", "attempting to save state");
//            stream.reset();
                long start = System.currentTimeMillis();
                out = file.startWrite();
                world.write(new DataOutputStream(new BufferedOutputStream(out)));
                file.finishWrite(out);
                Log.d("Xel", "wrote to file in " + (System.currentTimeMillis() - start) + "ms");
            }
/*
            new Thread() {
                @Override
                public void run() {
                    Log.d("Xel", "writing to file in another thread");
                    long start = System.currentTimeMillis();
                    try {
                        stream.writeTo(ctx.openFileOutput(FILE_NAME, Context.MODE_PRIVATE));
                        Log.d("Xel", "wrote to file in " + (System.currentTimeMillis() - start) + "ms");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }.start();
*/


        } catch (IOException e) {
            e.printStackTrace();
            file.failWrite(out);
        }
    }

    protected abstract void draw();

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
