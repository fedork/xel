package net.karpelevitch.xel;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;

import static java.lang.Math.max;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    public static final int XEL_SIZE = 15;
    static final Paint[] PAINTS;
    static final Paint[] WHITES;
    static final int[] COLORS;
    static final int[] WHITE_RGB;
    static final long FRAME_INTERVAL = 1000L / 60;
    static final long MAX_FRAME_INTERVAL = 100L;
    private static final double FACTOR = 0.9;
    private static final int ORANGE = Color.rgb(255, 200, 0);
    private static final int PINK = Color.rgb(255, 175, 175);
    public static final int[] BASE_COLORS = new int[]{Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, ORANGE, PINK, Color.RED, Color.YELLOW};

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
        mThread = new TextureRenderingThread(textureView, this);
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

}
