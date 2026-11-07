package cn.sqmydor.cgpf;

import android.app.Application;

import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Receives the framework service binder (only delivered when the module is enabled). */
public final class App extends Application implements XposedServiceHelper.OnServiceListener {
    interface Listener {
        void onServiceChanged(XposedService service);
    }

    private static volatile XposedService service;
    private static final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

    static XposedService service() {
        return service;
    }

    static void addListener(Listener l) {
        listeners.add(l);
        l.onServiceChanged(service);
    }

    static void removeListener(Listener l) {
        listeners.remove(l);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService s) {
        service = s;
        for (Listener l : listeners) l.onServiceChanged(s);
    }

    @Override
    public void onServiceDied(XposedService s) {
        service = null;
        for (Listener l : listeners) l.onServiceChanged(null);
    }
}
