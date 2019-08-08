package com.utils.softwatchdog;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WatchDog {
    private static final String TAG = "WatchDog";

    public static boolean debug = false;

    private final static int DEFAULT_DOG_FOOD_COUNT = 10;

    public interface OnDogDiedListener {
        void onDogDied();
    }

    private EatFoodRunnable eatFoodRunnable = null;

    private final static Object LOCK = new Object();

    private static volatile int dogFoodRemain = 10;

    private static volatile boolean isPauseDogEatFood = false;

    private volatile boolean isStartFeedDog = false;

    private ScheduledThreadPoolExecutor exec = null;


    private static long lastSendBroadTime = 0L;

    public WatchDog(OnDogDiedListener onDogDiedListener, Context context) {
        Log.e(TAG, "WatchDog: " + onDogDiedListener);
        eatFoodRunnable = new EatFoodRunnable(onDogDiedListener, context);
        exec = new ScheduledThreadPoolExecutor(1);
    }

    public void isDebug(boolean isDebug) {
        debug = isDebug;
    }

    public void feedDog() {
        synchronized (LOCK) {
            if (dogFoodRemain <= DEFAULT_DOG_FOOD_COUNT) {
                dogFoodRemain++;
                if (debug) Log.i(TAG, "feed Dog,dog's food increase ,food remain " + dogFoodRemain);
            }
        }
    }

    public void pauseEatDogFood() {
        synchronized (LOCK) {
            isPauseDogEatFood = true;
            if (debug) Log.i(TAG, "dog food box is locked,dog can't eat food");
        }
    }

    public void resumeFeedDog() {
        synchronized (LOCK) {
            isPauseDogEatFood = false;
            //when we resume feed dog ,we need fill it's food box
            dogFoodRemain = DEFAULT_DOG_FOOD_COUNT;
            if (debug) Log.i(TAG, "dog food box is unlock,dog can eat food");
        }
    }

    public void startFeedDog(int initFoodCount, int initialDelay, int period, TimeUnit unit) throws Exception {
        if (exec == null) {
            throw new Exception("ScheduledThreadPoolExecutor is not init");
        }
        if (eatFoodRunnable == null) {
            throw new Exception("EatFoodRunnable is not init");
        }
        //when start feed,reset the food count
        dogFoodRemain = initFoodCount;
        if (!isStartFeedDog) {
            exec.scheduleAtFixedRate(eatFoodRunnable, initialDelay, period, unit);
            isStartFeedDog = true;
        }
        isPauseDogEatFood = false;
        if (debug)
            Log.e(TAG, "start feed fog,each " + period + " " + unit.name() + " feed one food");

    }

    public void stopWatchDog() {
        synchronized (LOCK) {
            isPauseDogEatFood = true;
            isStartFeedDog = false;
            if (exec != null) {
                exec.remove(eatFoodRunnable);
                exec.shutdown();
            }
            dogFoodRemain = DEFAULT_DOG_FOOD_COUNT;
            if (debug) Log.i(TAG, "stop watch dog success");
        }
    }

    private static class EatFoodRunnable implements Runnable {


        private WeakReference<OnDogDiedListener> wrf = null;
        private WeakReference<Context> wrfContext = null;


        public EatFoodRunnable(OnDogDiedListener onDogDiedListener, Context context) {
            wrf = new WeakReference<>(onDogDiedListener);
            wrfContext = new WeakReference<>(context);
        }

        @Override
        public void run() {
            synchronized (LOCK) {
                if (isPauseDogEatFood) {
                    if (debug) Log.e(TAG, "dog food box is locked ,can't eat food ");
                    return;
                }
                if (dogFoodRemain <= 0) {
                    if (SystemClock.elapsedRealtimeNanos() - lastSendBroadTime > 5000000000L) {
                        lastSendBroadTime = SystemClock.elapsedRealtimeNanos();
                        if (wrf.get() != null) {
                            wrf.get().onDogDied();
                            if (debug) Log.e(TAG, "dog food remain 0 ,dog will died");
                        } else {
                            if (debug) {
                                Log.e(TAG, "dog died,but no one manage,send broadcast[com.watch.dog.died]");
                            }
                            if (wrfContext.get() != null) {
                                Intent intent = new Intent();
                                intent.setAction("com.watch.dog.died");
                                wrfContext.get().sendBroadcast(intent);
                            }
                        }
                    }
                } else {
                    dogFoodRemain--;
                }
                if (debug) Log.e(TAG, "dog eat a food,dog food remain " + dogFoodRemain);
            }
        }
    }

}
