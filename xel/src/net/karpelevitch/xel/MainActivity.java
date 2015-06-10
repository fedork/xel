package net.karpelevitch.xel;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.renderscript.*;
import android.util.Log;
import android.view.TextureView;
import net.karpelevitch.l2.World;

import static java.lang.Math.min;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    public static final int SIZE = 300;
    private static final double FACTOR = 0.8;
    private static final int ORANGE = Color.rgb(255, 200, 0);
    private static final int PINK = Color.rgb(255, 175, 175);
    public static final int[] BASE_COLORS = new int[]{Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, ORANGE, PINK, Color.RED, Color.YELLOW};
    private static final Paint[] PAINTS;
    private static final Paint[] WHITES;
    private static final int[] COLORS;
    private static final int[] WHITE_RGB;

    static {
        int COLOR_COUNT = BASE_COLORS.length * 3;
        PAINTS = new Paint[COLOR_COUNT];
        COLORS = new int[COLOR_COUNT];
        for (int i = 0; i < BASE_COLORS.length; i++) {
            int color = BASE_COLORS[i];
            for (int j = 0; j < 3; j++) {
                PAINTS[i * 3 + j] = newPaint(color);
                COLORS[i * 3 + j] = color;
                color = darker(color);
            }
        }
        WHITES = new Paint[256];
        WHITE_RGB = new int[256];
        for (int i = 0; i < WHITES.length; i++) {
            int rgb = 0xff000000 | (i << 16) | (i << 8) | i;
            WHITES[i] = newPaint(rgb);
            WHITE_RGB[i] = rgb;
        }
    }

    private RenderingThread mThread;
    private TextureView textureView;

    private static Paint newPaint(int color) {
        Paint paint = new Paint();
        paint.setColor(color);
        return paint;
    }

    private static int darker(int color) {
        return Color.argb(Color.alpha(color),
                Math.max((int) (Color.red(color) * FACTOR), 0),
                Math.max((int) (Color.green(color) * FACTOR), 0),
                Math.max((int) (Color.blue(color) * FACTOR), 0));
    }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        textureView = (TextureView) findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(this);


    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mThread = new RenderingThread(textureView);
        mThread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Ignored
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mThread != null) mThread.stopRendering();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Ignored
    }

    private class RenderingThread extends Thread {
        private final TextureView mSurface;
        private final World world;
        private final Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        private volatile boolean mRunning = true;

        public RenderingThread(TextureView surface) {
            mSurface = surface;
            world = new World(SIZE, PAINTS.length);
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
                maxage[0] = world.update();
                gen++;
                frames++;
                long now;
                if ((now = System.currentTimeMillis()) - startTime > 1000) {
                    draw(totalEnergy);
                    long totalMemory = Runtime.getRuntime().totalMemory();
                    long maxMemory = Runtime.getRuntime().maxMemory();
                    long freeMem = Runtime.getRuntime().freeMemory();
                    double freePercent = 100.0 * freeMem / totalMemory;
//                    Runtime.getRuntime().gc();
                    Log.d("Xel", String.format("%d \t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%f\n", gen, frames * 1000 / (now - startTime), world.list.size(), totalEnergy[0], maxage[0], world.maxgen, freeMem, totalMemory, maxMemory, freePercent));
                    startTime = now;
                    frames = 0;
                }
            }
        }

        private void diffuse() {
            RenderScript rs = RenderScript.create(MainActivity.this);
            Element f32 = Element.F32(rs);
            ScriptIntrinsicConvolve3x3 script = ScriptIntrinsicConvolve3x3.create(rs, f32);
            script.setCoefficients(new float[]{
                    .01f, .015f, .01f,
                    .015f, .9f, .015f,
                    .01f, .015f, .01f});
            Type xy = Type.createXY(rs, f32, 12, 12);
            Allocation ain = Allocation.createTyped(rs, xy);
            Allocation aout = Allocation.createTyped(rs, xy);
            float[] arr = new float[144];
            arr[12 * 6 + 1] = 1.0f;
            while (System.currentTimeMillis() > 0) {
                ain.copyFrom(arr);
                ain.copy2DRangeFrom(0, 1, 1, 10, ain, 10, 1);
                ain.copy2DRangeFrom(11, 1, 1, 10, ain, 1, 1);
                ain.copy2DRangeFrom(0, 0, 12, 1, ain, 0, 10);
                ain.copy2DRangeFrom(0, 11, 12, 1, ain, 0, 1);
                script.setInput(ain);
                script.forEach(aout);
                aout.copyTo(arr);
                for (int i = 1; i < 11; i++) {
                    for (int j = 1; j < 11; j++) {
                        System.out.print(arr[i * 12 + j] + "\t");
                    }
                    System.out.println();
                }
                System.out.println();

/*
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
*/
            }
//            Allocation.createSized(rs, )
        }

        private void draw(int[] totalEnergy) {
            final Canvas canvas = mSurface.lockCanvas(null);
            try {
                World.RGBDraw rgbDraw = new BitmapDraw(canvas);
                totalEnergy[0] = world.draw(true, rgbDraw);
                rgbDraw.done();
//                    canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
//                    canvas.drawRect(x, y, x + 20.0f, y + 20.0f, paint);
            } finally {
                mSurface.unlockCanvasAndPost(canvas);
            }
        }

        void stopRendering() {
            interrupt();
            mRunning = false;
        }

        private class BitmapDraw implements World.RGBDraw {

            private Canvas canvas;

            private BitmapDraw(Canvas canvas) {
                this.canvas = canvas;
            }

            @Override
            public void drawMono(int i, int j, int b) {
                bitmap.setPixel(i, j, WHITE_RGB[b]);
//                            canvas.drawRect(i * pixels, j * pixels, (i + 1) * pixels - 1, (j + 1) * pixels - 1, WHITES[b]);
            }

            @Override
            public void drawColor(int i, int j, int color) {
                bitmap.setPixel(i, j, COLORS[color]);
//                            canvas.drawRect(i * pixels, j * pixels, (i + 1) * pixels - 1, (j + 1) * pixels - 1, PAINTS[color]);
            }

            @Override
            public void done() {
                this.canvas.drawBitmap(bitmap, 0, 0, null);
            }
        }

        private class CanvasDraw implements World.RGBDraw {
            private final int pixels;
            private android.graphics.Canvas canvas;

            private CanvasDraw(Canvas canvas) {
                this.canvas = canvas;
                pixels = min(canvas.getHeight(), canvas.getWidth()) / SIZE;
            }

            @Override
            public void drawMono(int i, int j, int b) {
                canvas.drawRect(i * pixels, j * pixels, (i + 1) * pixels/* - 1*/, (j + 1) * pixels /*- 1*/, WHITES[b]);
            }

            @Override
            public void drawColor(int i, int j, int color) {
                canvas.drawRect(i * pixels, j * pixels, (i + 1) * pixels/* - 1*/, (j + 1) * pixels /*- 1*/, PAINTS[color]);
            }

            @Override
            public void done() {
            }
        }
    }

}
