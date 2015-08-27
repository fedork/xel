package net.karpelevitch.xel;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.AtomicFile;
import android.util.Log;

import java.io.*;

public class XelWorldService extends Service {

    public static final String FILE_NAME = "xel_state";
    static final long SAVE_INTERVAL = 120000L;
    private final LocalBinder localBinder = new LocalBinder();
    private Thread thread;
    private World world;
    private volatile boolean running = false;

    public static void saveState(World world, Context ctx) {
        if (android.os.Build.VERSION.SDK_INT>=17) {
            saveStateAtomic(world, ctx);
        } else {
            saveStateSimple(world, ctx);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private static void saveStateAtomic(World world, Context ctx) {
        AtomicFile file = new AtomicFile(ctx.getFileStreamPath(FILE_NAME));
        FileOutputStream out = null;
        try {
            synchronized (World.class) {
                Log.d("Xel", "attempting to save state");
                long start = System.currentTimeMillis();
                out = file.startWrite();
                world.write(new DataOutputStream(new BufferedOutputStream(out, 65536)));
                file.finishWrite(out);
                Log.d("Xel", "wrote to file in " + (System.currentTimeMillis() - start) + "ms");
            }
        } catch (IOException e) {
            e.printStackTrace();
            file.failWrite(out);
        }
    }

    private static void saveStateSimple(World world, Context ctx) {
        try {
            File fileName = ctx.getFileStreamPath(FILE_NAME);
            File tmpFileName = ctx.getFileStreamPath(FILE_NAME + ".tmp");
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFileName), 65536));
            synchronized (World.class) {
                Log.d("Xel", "attempting to save state");
                long start = System.currentTimeMillis();
                world.write(out);
                out.close();
                if (tmpFileName.renameTo(fileName)) {
                    Log.d("Xel", "wrote to file in " + (System.currentTimeMillis() - start) + "ms");
                } else {
                    Log.d("Xel", "failed to rename file to " + fileName.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("Xel", "intent = " + intent);
        start();
        return localBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopService();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d("Xel", "destroy");
        stopService();
        super.onDestroy();
    }

    void stopService() {
        running = false;
        thread.interrupt();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("Xel", "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Xel", "intent = " + intent);
        start();
        return START_NOT_STICKY;
    }

    synchronized void start() {
        if (running) {
            Log.d("Xel", "already running, so not starting again");
            return;
        }
        running = true;
        if (thread != null) return;
        thread = new Thread() {
            @Override
            public void run() {
                try {
                    synchronized (World.class) {
                        Log.d("Xel", "attempting to restore from file");
                        DataInputStream in = null;
                        try {
                            FileInputStream file;
                            if (android.os.Build.VERSION.SDK_INT>=17) {
                                file = new AtomicFile(getFileStreamPath(XelWorldService.FILE_NAME)).openRead();
                            } else {
                                file = new FileInputStream(getFileStreamPath(XelWorldService.FILE_NAME));
                            }
                            in = new DataInputStream(new BufferedInputStream(file/*, 65536*/));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        ActivityManager activityManager = (ActivityManager) getApplicationContext().getSystemService(ACTIVITY_SERVICE);
                        int memoryClass = activityManager.getMemoryClass();
                        Log.d("Xel", "memory class: "+ memoryClass);
                        int defaultSize = memoryClass<=48?200:300;
                        world = null;
                        Runtime.getRuntime().gc();
                        synchronized (XelWorldService.this) {
                            world = new AndroidWorld(defaultSize, defaultSize, in) {
                                @Override
                                protected Context getCtx() {
                                    return XelWorldService.this;
                                }
                            };
                            XelWorldService.this.notifyAll();
                        }
                    }
                    long saveTime = System.currentTimeMillis() + SAVE_INTERVAL;
                    long printTime = 0;
                    while (running && !interrupted()) {
                        synchronized (World.class) {
                            world.update();
                            if (System.currentTimeMillis() >= saveTime) {
                                saveState(world, XelWorldService.this);
                                saveTime = System.currentTimeMillis() + SAVE_INTERVAL;
                            }
//                            World.class.notifyAll();
//                            World.class.wait(5L);
                        }
                        Thread.sleep(1L);
                        if (System.currentTimeMillis() >= printTime) {
                            Log.d("Xel", "world.maxgen = " + world.maxgen + " \tworld.list.size() = " + world.list.size());
                            printTime = System.currentTimeMillis() + 5000L;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    saveState(world, XelWorldService.this);
                    running = false;
                    thread = null;
                    world = null;
                    Log.d("Xel", "service thread exited");

                    stopSelf();
                }
            }
        };
        thread.start();
    }

    public synchronized World getWorld() {
        return world;
    }

    public class LocalBinder extends Binder {
        XelWorldService getService() {
            return XelWorldService.this;
        }
    }
}
