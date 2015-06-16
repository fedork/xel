package net.karpelevitch.xel;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.renderscript.*;
import android.util.Log;
import android.view.TextureView;
import net.karpelevitch.l2.EnergyField;
import net.karpelevitch.l2.World;

import java.util.Arrays;

import static java.lang.Math.max;
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
    private static final long FRAME_INTERVAL = 1000L / 30;
    private static final long MAX_FRAME_INTERVAL = 1000L;

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
                max((int) (Color.red(color) * FACTOR), 0),
                max((int) (Color.green(color) * FACTOR), 0),
                max((int) (Color.blue(color) * FACTOR), 0));
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
    protected void onPause() {
        Log.d("Xel", "onPause!");
        if (mThread != null) mThread.stopRendering();
        super.onPause();
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
            world = new World(SIZE, PAINTS.length) {
                @Override
                protected EnergyField createEnergyField(final int size) {
                    RenderScript rs = RenderScript.create(MainActivity.this);
                    Element f32 = Element.F32(rs);

//                    ScriptIntrinsic

                    final ScriptIntrinsicConvolve3x3 script = ScriptIntrinsicConvolve3x3.create(rs, f32);
                    float c22 = 1.0f - 1.0f / World.DIFFUSE_FACTOR;
                    float cx = (1.0f - c22) / 8;
                    float[] coefficients = new float[9];
                    Arrays.fill(coefficients, cx);
                    coefficients[4] = c22;
                    script.setCoefficients(coefficients);
                    Type xy = Type.createXY(rs, f32, size + 2, size + 2);
                    final Allocation ain = Allocation.createTyped(rs, xy);
                    final Allocation aout = Allocation.createTyped(rs, xy);
                    return new EnergyField() {
                        final float[] energy = new float[(size + 2) * (size + 2)];

                        @Override
                        public void putEnergy(int coords, int e) {
                            int newcoords = coords / size * 2 + coords + size + 3;
                            energy[newcoords] = max(0, min(MAX_ENERGY, energy[newcoords] + e));
                        }

                        @Override
                        public int readEnergy(int coords) {
                            return (int) energy[coords / size * 2 + coords + size + 3];
                        }

                        @Override
                        public void diffuse(World world) {
                            ain.copyFrom(energy);
                            ain.copy2DRangeFrom(0, 1, 1, size, ain, size, 1);
                            ain.copy2DRangeFrom(size + 1, 1, 1, size, ain, 1, 1);
                            ain.copy2DRangeFrom(0, 0, size + 2, 1, ain, 0, size);
                            ain.copy2DRangeFrom(0, size + 1, size + 2, 1, ain, 0, 1);
                            script.setInput(ain);
                            script.forEach(aout);
                            aout.copyTo(energy);

                        }
                    };
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
                long maxNextFrame = nextFrame + MAX_FRAME_INTERVAL;
                draw(totalEnergy);
                long sleepTime;
                do {
                    maxage[0] = world.update();
                    nextFrame += FRAME_INTERVAL;
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

                Matrix matrix = new Matrix();

                float scale = (float) min(canvas.getWidth(), canvas.getHeight()) / bitmap.getWidth();
                matrix.setScale(scale, scale);
                this.canvas.drawBitmap(bitmap, matrix, null);
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
