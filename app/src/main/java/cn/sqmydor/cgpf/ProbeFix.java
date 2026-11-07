/*
 * Ported from ColorOS GMS Probe Fix (https://github.com/rkbkosp/cn.rkbkosp.colorosgmsprobefix)
 * Copyright (c) 2026 rkbkosp. Licensed under the MIT License, see LICENSE-ColorOSGmsProbeFix.
 * Changes: migrated from legacy Xposed API 82 to libxposed API 102, proxy port is read from
 * module settings (default 7890), feature can be toggled off.
 */
package cn.sqmydor.cgpf;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Real HTTP 204 verification through the local proxy; never fabricates unconditional success. */
final class ProbeFix {
    private static final String DETECTOR =
            "com.oplus.battery.restrictdynamicfeature.google.NetworkDetector";
    private static final String RESULT = DETECTOR + "$DetectorResult";
    private static final String CONTROLLER =
            "com.oplus.battery.restrictdynamicfeature.google.GoogleRestrictionController";
    private static final int TIMEOUT_MS = 3000;

    private final ModuleMain module;
    private boolean installed;
    private volatile boolean probeSucceeded;

    ProbeFix(ModuleMain module) {
        this.module = module;
    }

    synchronized void install(final ClassLoader loader) {
        if (installed) return;
        final Class<?> detector;
        try {
            detector = Class.forName(DETECTOR, false, loader);
        } catch (ClassNotFoundException missing) {
            return; // not in this package's classloader; another onPackageReady may have it
        }
        try {
            Class<?> result = Class.forName(RESULT, false, loader);
            Method method = detector.getDeclaredMethod("a", Context.class, int.class);
            if (method.getReturnType() != result)
                throw new NoSuchMethodException("unexpected detector signature");
            final Object wifiSuccess = enumValue(result, "RESULT_WIFI_SUCCESS");
            final Object mobileSuccess = enumValue(result, "RESULT_MOBILE_SUCCESS");

            module.hook(method).setId("cgpf.NetworkDetector.a").intercept(chain -> {
                Object override = null;
                if (module.probeEnabled()) {
                    try {
                        override = probe(chain.getArg(0), chain.getArg(1), loader,
                                wifiSuccess, mobileSuccess);
                    } catch (Throwable error) {
                        // Probe errors must not crash Athena or manufacture a success.
                        module.info("probe exception; continue original detector: " + error);
                    }
                }
                if (override != null) return override;
                Object ret = chain.proceed();
                module.info("detector returned=" + ret);
                return ret;
            });
            installed = true;
            module.info("installed NetworkDetector.a(Context,int)");
            installReadinessCheck(loader);
        } catch (Throwable error) {
            module.warn("firmware signature mismatch or hook failure: " + error);
        }
    }

    private Object probe(Object ctxArg, Object attempt, ClassLoader loader,
                         Object wifiSuccess, Object mobileSuccess) throws Exception {
        probeSucceeded = false;
        int port = module.proxyPort();
        module.info("detector invoked; attempt=" + attempt + "; proxy port=" + port);
        Context context = (Context) ctxArg;
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = cm == null ? null : cm.getActiveNetworkInfo();
        if (network == null || !network.isConnected()) {
            module.info("no connected network; continue original detector");
            return null;
        }
        if (!proxyListening(port)) {
            module.info("proxy 127.0.0.1:" + port + " not ready; continue original detector");
            return null;
        }
        for (String address : addresses(context, loader)) {
            if (probe204(address, port)) {
                probeSucceeded = true;
                module.info("real HTTP 204 via proxy; Google detection succeeds");
                return network.getType() == ConnectivityManager.TYPE_WIFI
                        ? wifiSuccess : mobileSuccess;
            }
        }
        module.info("proxy probes failed; continue original detector");
        return null;
    }

    private static Object enumValue(Class<?> type, String expected) {
        Object[] values = type.getEnumConstants();
        if (values != null) {
            for (Object value : values) {
                if (((Enum<?>) value).name().equals(expected)) return value;
            }
        }
        throw new IllegalStateException("missing enum value " + expected);
    }

    private void installReadinessCheck(ClassLoader loader) {
        try {
            Class<?> controller = Class.forName(CONTROLLER, false, loader);
            final Method recheck = controller.getDeclaredMethod("R", long.class, int.class);
            recheck.setAccessible(true);
            Constructor<?> ctor = controller.getDeclaredConstructor(Context.class);
            module.hook(ctor).setId("cgpf.GoogleRestrictionController.<init>").intercept(chain -> {
                chain.proceed();
                try {
                    startReadinessLoop(chain.getThisObject(), recheck);
                } catch (Throwable error) {
                    module.warn("controller capture failed: " + error);
                }
                return null;
            });
        } catch (Throwable error) {
            module.warn("readiness hook unavailable; natural network checks remain: " + error);
        }
    }

    private void startReadinessLoop(final Object instance, final Method recheck) throws Exception {
        Handler found = null;
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.getType() == Handler.class) {
                field.setAccessible(true);
                found = (Handler) field.get(instance);
                break;
            }
        }
        if (found == null) throw new IllegalStateException("controller handler missing");
        final Handler handler = found;
        handler.postDelayed(new Runnable() {
            private boolean wasListening;
            private int retries;
            private int lastPort = -1;

            @Override
            public void run() {
                try {
                    if (!module.probeEnabled()) {
                        wasListening = false;
                        retries = 0;
                        return;
                    }
                    int port = module.proxyPort();
                    if (port != lastPort) { // port changed in settings: treat as fresh start
                        wasListening = false;
                        lastPort = port;
                    }
                    boolean listening = proxyListening(port);
                    if (!listening) {
                        retries = 0;
                    } else if (!wasListening || (!probeSucceeded && retries < 3)) {
                        if (!wasListening) retries = 0;
                        retries++;
                        // Use the original controller's check queue. boot=1 reapplies
                        // the real result even if persisted policies disagree with memory.
                        recheck.invoke(instance, 0L, 1);
                        module.info("queued original controller check; proxy :" + port
                                + " ready; retry=" + retries);
                    }
                    wasListening = listening;
                } catch (Throwable error) {
                    module.warn("readiness check failed: " + error);
                } finally {
                    handler.postDelayed(this, 30000L);
                }
            }
        }, 10000L);
        module.info("controller captured; proxy readiness recheck enabled");
    }

    private static boolean proxyListening(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(Config.PROXY_HOST, port), 250);
            return true;
        } catch (Exception unavailable) {
            return false;
        }
    }

    private List<String> addresses(Context context, ClassLoader loader) {
        ArrayList<String> urls = new ArrayList<>();
        try {
            // h6.a is the raw RUS helper class in the original author's Battery.apk build.
            Class<?> helperClass = Class.forName("h6.a", false, loader);
            Method d = helperClass.getDeclaredMethod("d", Context.class);
            d.setAccessible(true);
            Object helper = d.invoke(null, context);
            Method b = findMethod(helper.getClass(), "b", String.class);
            Object configured = b.invoke(helper, "google_address_list");
            if (configured instanceof List<?>) {
                for (Object value : new ArrayList<Object>((List<?>) configured)) {
                    if (value instanceof String) {
                        URL url = new URL((String) value);
                        if ("http".equals(url.getProtocol()) || "https".equals(url.getProtocol()))
                            urls.add((String) value);
                    }
                }
            }
        } catch (Throwable error) {
            module.info("RUS address lookup failed; use extracted default URLs: " + error);
        }
        if (urls.isEmpty()) {
            urls.add("https://www.google.com/generate_204");
            urls.add("http://www.google.com/gen_204");
        }
        return urls;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params)
            throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private boolean probe204(String address, int port) {
        HttpURLConnection connection = null;
        try {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(Config.PROXY_HOST, port));
            connection = (HttpURLConnection) new URL(address).openConnection(proxy);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            int code = connection.getResponseCode();
            module.info("probe response=" + code);
            return code == 204;
        } catch (Exception error) {
            module.info("probe failed: " + error.getClass().getSimpleName());
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
