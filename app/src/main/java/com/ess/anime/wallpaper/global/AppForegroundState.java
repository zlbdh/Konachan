package com.ess.anime.wallpaper.global;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.util.concurrent.atomic.AtomicInteger;

/** Track only app visibility; do not retain screens or user data. */
public final class AppForegroundState implements Application.ActivityLifecycleCallbacks {
    private static final AtomicInteger started = new AtomicInteger();
    private AppForegroundState() { }
    public static void register(Application app) { app.registerActivityLifecycleCallbacks(new AppForegroundState()); }
    public static boolean isVisible() { return started.get() > 0; }
    @Override public void onActivityStarted(Activity activity) { started.incrementAndGet(); }
    @Override public void onActivityStopped(Activity activity) { started.updateAndGet(value -> Math.max(0, value - 1)); }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
