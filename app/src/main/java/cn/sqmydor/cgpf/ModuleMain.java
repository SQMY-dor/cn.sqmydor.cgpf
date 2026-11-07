package cn.sqmydor.cgpf;

import android.content.SharedPreferences;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

/**
 * Entry point (libxposed API 102). Combines:
 *  1. ColorOS GMS Probe Fix  - com.oplus.battery / com.oplus.athena
 *  2. CN GMS feature hiding  - Google apps (gms, Play Store, ...)
 *  3. Automatic RKP renewal  - system_server (trigger) + com.android.rkpdapp (proxy route)
 * Each feature is toggleable; the proxy port is configurable (default 7890).
 */
public final class ModuleMain extends XposedModule {
    static final String TAG = "CGPF";

    static final String PKG_BATTERY = "com.oplus.battery";
    static final String PKG_ATHENA = "com.oplus.athena";

    private String processName = "";
    private boolean systemServer;
    private volatile SharedPreferences prefs;
    private boolean prefsUnavailableLogged;

    private final ProbeFix probeFix = new ProbeFix(this);
    private final CnFeatureHider featureHider = new CnFeatureHider(this);
    private final RkpRenewer rkp = new RkpRenewer(this);

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        systemServer = param.isSystemServer();
        info("loaded in " + processName + " (" + getFrameworkName() + " "
                + getFrameworkVersion() + ", API " + getApiVersion() + ")");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (systemServer) return; // never hide features from system_server itself
        String pkg = param.getPackageName();
        if (PKG_BATTERY.equals(pkg) || PKG_ATHENA.equals(pkg)) return;
        if (RkpRenewer.PKG_RKPD.equals(pkg) || RkpRenewer.PKG_LEGACY.equals(pkg)) {
            rkp.installProxyRoute();
            return;
        }
        // Feature hiding lives in the process's own PackageManager wrapper, hook once per process.
        featureHider.install();
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        rkp.startInSystemServer();
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (!PKG_BATTERY.equals(pkg) && !PKG_ATHENA.equals(pkg)) return;
        if (!PKG_ATHENA.equals(processName)) return;
        info("package ready " + pkg + " in " + processName);
        probeFix.install(param.getClassLoader());
    }

    // ---- settings (read live from the framework's remote preferences) ----

    SharedPreferences prefs() {
        SharedPreferences p = prefs;
        if (p != null) return p;
        synchronized (this) {
            if (prefs != null) return prefs;
            try {
                prefs = getRemotePreferences(Config.GROUP);
            } catch (Throwable t) {
                if (!prefsUnavailableLogged) {
                    prefsUnavailableLogged = true;
                    warn("remote preferences unavailable, using defaults: " + t);
                }
            }
            return prefs;
        }
    }

    boolean probeEnabled() {
        SharedPreferences p = prefs();
        try {
            return p == null ? Config.DEFAULT_PROBE_ENABLED
                    : p.getBoolean(Config.KEY_PROBE_ENABLED, Config.DEFAULT_PROBE_ENABLED);
        } catch (Throwable t) {
            return Config.DEFAULT_PROBE_ENABLED;
        }
    }

    boolean hideCnFeaturesEnabled() {
        SharedPreferences p = prefs();
        try {
            return p == null ? Config.DEFAULT_HIDE_CN_FEATURES
                    : p.getBoolean(Config.KEY_HIDE_CN_FEATURES, Config.DEFAULT_HIDE_CN_FEATURES);
        } catch (Throwable t) {
            return Config.DEFAULT_HIDE_CN_FEATURES;
        }
    }

    boolean rkpRenewEnabled() {
        return bool(Config.KEY_RKP_RENEW, Config.DEFAULT_RKP_RENEW);
    }

    boolean rkpUseProxy() {
        return bool(Config.KEY_RKP_PROXY, Config.DEFAULT_RKP_PROXY);
    }

    private boolean bool(String key, boolean def) {
        SharedPreferences p = prefs();
        try {
            return p == null ? def : p.getBoolean(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    int proxyPort() {
        SharedPreferences p = prefs();
        try {
            return p == null ? Config.DEFAULT_PROXY_PORT
                    : Config.sanitizePort(p.getInt(Config.KEY_PROXY_PORT, Config.DEFAULT_PROXY_PORT));
        } catch (Throwable t) {
            return Config.DEFAULT_PROXY_PORT;
        }
    }

    void info(String msg) {
        log(Log.INFO, TAG, msg);
    }

    void warn(String msg) {
        log(Log.WARN, TAG, msg);
    }
}
