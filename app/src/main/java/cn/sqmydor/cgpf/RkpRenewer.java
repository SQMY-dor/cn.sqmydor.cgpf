package cn.sqmydor.cgpf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.os.UserHandle;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;

/**
 * Automatic RKP (Remote Key Provisioning) renewal.
 *
 * system_server part: once boot has completed, watches the local proxy. When it becomes reachable
 * (and then once a day while it stays reachable) it sends an explicit BOOT_COMPLETED to rkpd's
 * BootReceiver. rkpd reacts by re-enqueueing its PeriodicProvisioner with CANCEL_AND_REENQUEUE,
 * which runs right away once its own constraints (network, battery not low) are met: expired
 * attestation keys are purged and new ones are fetched from Google.
 *
 * rkpd part: optionally routes rkpd's requests to *.googleapis.com through 127.0.0.1:port
 * (only while the proxy is reachable, otherwise the direct connection is kept).
 */
final class RkpRenewer {
    static final String PKG_RKPD = "com.android.rkpdapp";             // Android 14+ (APEX com.android.rkpd)
    static final String PKG_LEGACY = "com.android.remoteprovisioner"; // Android 12/13

    private static final long FIRST_DELAY_MS = 60_000L;
    private static final long POLL_MS = 30_000L;
    private static final long DAILY_MS = 24L * 60 * 60 * 1000;
    private static final long MIN_GAP_MS = 10L * 60 * 1000; // avoid re-triggering on proxy flaps

    private final ModuleMain module;
    private boolean started;
    private boolean routeInstalled;

    RkpRenewer(ModuleMain module) {
        this.module = module;
    }

    // ------------------------------------------------------------------ system_server

    synchronized void startInSystemServer() {
        if (started) return;
        started = true;
        Thread t = new Thread(this::loop, "cgpf-rkp-renew");
        t.setDaemon(true);
        t.start();
        module.info("RKP renewal watcher started in system_server");
    }

    private void loop() {
        boolean wasListening = false;
        long lastTrigger = -1;
        int lastPort = -1;
        sleep(FIRST_DELAY_MS);
        while (true) {
            try {
                if (!"1".equals(sysprop("sys.boot_completed"))) {
                    sleep(POLL_MS);
                    continue;
                }
                if (!module.rkpRenewEnabled()) {
                    wasListening = false;
                    sleep(POLL_MS);
                    continue;
                }
                int port = module.proxyPort();
                if (port != lastPort) {
                    wasListening = false;
                    lastPort = port;
                }
                Context ctx = systemContext();
                boolean listening = ctx != null && networkConnected(ctx) && proxyListening(port);
                long now = SystemClock.elapsedRealtime();
                boolean becameReady = listening && !wasListening;
                boolean dailyDue = listening && lastTrigger >= 0 && now - lastTrigger >= DAILY_MS;
                boolean gapOk = lastTrigger < 0 || now - lastTrigger >= MIN_GAP_MS;
                if ((becameReady && gapOk) || dailyDue) {
                    if (trigger(ctx)) lastTrigger = now;
                }
                wasListening = listening;
            } catch (Throwable t) {
                module.warn("RKP watcher error: " + t);
            }
            sleep(POLL_MS);
        }
    }

    private boolean trigger(Context ctx) {
        String pkg = installed(ctx, PKG_RKPD) ? PKG_RKPD
                : installed(ctx, PKG_LEGACY) ? PKG_LEGACY : null;
        if (pkg == null) {
            module.warn("RKP: neither " + PKG_RKPD + " nor " + PKG_LEGACY + " installed");
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED)
                    .setComponent(new ComponentName(pkg, pkg + ".BootReceiver"))
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            ctx.sendBroadcastAsUser(intent, UserHandle.getUserHandleForUid(0));
            module.info("RKP: renewal triggered via " + pkg + "/.BootReceiver");
            return true;
        } catch (Throwable t) {
            module.warn("RKP: trigger failed: " + t);
            return false;
        }
    }

    private static boolean installed(Context ctx, String pkg) {
        try {
            ctx.getPackageManager().getPackageInfo(pkg, PackageManager.MATCH_SYSTEM_ONLY
                    | PackageManager.MATCH_DISABLED_COMPONENTS);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Context systemContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            if (thread == null) return null;
            return (Context) at.getMethod("getSystemContext").invoke(thread);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sysprop(String key) {
        try {
            Method get = Class.forName("android.os.SystemProperties").getMethod("get", String.class);
            return (String) get.invoke(null, key);
        } catch (Throwable t) {
            return "";
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean networkConnected(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Throwable t) {
            return true; // let rkpd's own network constraint decide
        }
    }

    // ------------------------------------------------------------------ rkpd process

    synchronized void installProxyRoute() {
        if (routeInstalled) return;
        routeInstalled = true;
        try {
            Method open = URL.class.getDeclaredMethod("openConnection");
            module.hook(open).setId("cgpf.rkpd.openConnection").intercept(chain -> {
                URL url = (URL) chain.getThisObject();
                String host = url.getHost();
                if (host != null && host.endsWith("googleapis.com")
                        && module.rkpRenewEnabled() && module.rkpUseProxy()) {
                    int port = module.proxyPort();
                    if (proxyListening(port)) {
                        module.info("RKP: " + host + " via proxy :" + port);
                        return url.openConnection(new Proxy(Proxy.Type.HTTP,
                                new InetSocketAddress(Config.PROXY_HOST, port)));
                    }
                }
                return chain.proceed();
            });
            module.info("RKP: rkpd proxy routing installed");
        } catch (Throwable t) {
            module.warn("RKP: proxy routing hook failed: " + t);
        }
    }

    // ------------------------------------------------------------------ util

    static boolean proxyListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(Config.PROXY_HOST, port), 250);
            return true;
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
