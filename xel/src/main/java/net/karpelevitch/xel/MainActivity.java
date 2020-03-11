package net.karpelevitch.xel;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;

import net.karpelevitch.xel.databinding.MainBinding;

import static java.lang.Math.max;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, ServiceConnection {
    public static final int XEL_SIZE = 5;
    static final Paint[] PAINTS;
    static final Paint[] WHITES;
    static final int[] COLORS;
    static final int[] WHITE_RGB;
    static final long FRAME_INTERVAL = 1000L / 60;
    static final long MAX_FRAME_INTERVAL = 300L;
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
    private MainBinding binding;
    private XelWorldService xelService;
    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;

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
        binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.textureView.setSurfaceTextureListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean bound = bindService(new Intent(this, XelWorldService.class), this, BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        Log.d("Xel", "onPause!");
        del();
        super.onPause();
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
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d("Xel", "onSurfaceTextureAvailable width = " + width + " height = " + height);

        boolean bound = bindService(new Intent(this, XelWorldService.class), this, BIND_AUTO_CREATE);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Ignored
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        del();
//        unbindService(this);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Ignored
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return scaleGestureDetector != null && scaleGestureDetector.onTouchEvent(event) | (gestureDetector != null && gestureDetector.onTouchEvent(event));
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
                    xelService.wait(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } while (true);
        mThread = new TextureRenderingThread(binding.textureView, this, world);
        mThread.start();
        GestureDetector.SimpleOnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return mThread.scroll(distanceX, distanceY);
            }
        };
        gestureDetector = new GestureDetector(this, gestureListener);
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                return mThread.zoom(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d("Xel", "onServiceDisconnected name = " + name);
        del();
        // maybe stop rendering
    }
}
