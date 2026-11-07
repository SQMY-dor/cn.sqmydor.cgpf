package cn.sqmydor.cgpf;

/** Keys shared by the settings UI (writer) and the hooked processes (reader). */
public final class Config {
    private Config() {}

    /** Remote preference group, stored by the Xposed framework (libxposed service). */
    public static final String GROUP = "config";

    public static final String KEY_PROBE_ENABLED = "probe_fix_enabled";
    public static final String KEY_HIDE_CN_FEATURES = "hide_cn_features_enabled";
    public static final String KEY_PROXY_PORT = "proxy_port";
    public static final String KEY_RKP_RENEW = "rkp_renew_enabled";
    public static final String KEY_RKP_PROXY = "rkp_use_proxy";

    public static final boolean DEFAULT_PROBE_ENABLED = true;
    public static final boolean DEFAULT_HIDE_CN_FEATURES = true;
    public static final int DEFAULT_PROXY_PORT = 7890;
    public static final boolean DEFAULT_RKP_RENEW = true;
    public static final boolean DEFAULT_RKP_PROXY = true;
    public static final String PROXY_HOST = "127.0.0.1";

    public static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }

    /** Returns the port if valid, otherwise the default 7890. */
    public static int sanitizePort(int port) {
        return isValidPort(port) ? port : DEFAULT_PROXY_PORT;
    }
}
