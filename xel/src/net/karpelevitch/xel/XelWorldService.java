package net.karpelevitch.xel;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.util.AtomicFile;
import android.util.Log;
import net.karpelevitch.l2.World;

import java.io.*;

public class XelWorldService extends Service {

    public static final String FILE_NAME = "xel_state";
    static final long SAVE_INTERVAL = 120000L;
    private final LocalBinder localBinder = new LocalBinder();
    private Thread thread;
    private World world;
    private boolean running = true;

    public static void saveState(World world, Context ctx) {
        AtomicFile file = new AtomicFile(ctx.getFileStreamPath(FILE_NAME));
        FileOutputStream out = null;
        try {
            synchronized (World.class) {
                Log.d("Xel", "attempting to save state");
//            stream.reset();
                long start = System.currentTimeMillis();
                out = file.startWrite();
                world.write(new DataOutputStream(new BufferedOutputStream(out, 65536)));
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
                            in = new DataInputStream(new BufferedInputStream(new AtomicFile(getFileStreamPath(XelWorldService.FILE_NAME)).openRead(), 65536));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        world = new AndroidWorld(300, 300, in) {
                            @Override
                            protected Context getCtx() {
                                return XelWorldService.this;
                            }
                        };
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
                        if (System.currentTimeMillis() >= printTime) {
                            Log.d("Xel", "world.maxgen = " + world.maxgen + " \tworld.list.size() = " + world.list.size());
                            printTime = System.currentTimeMillis() + 5000L;
                        }
                    }
                    saveState(world, XelWorldService.this);
                    stopSelf();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                thread = null;
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
