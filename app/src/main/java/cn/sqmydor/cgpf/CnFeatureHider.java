package cn.sqmydor.cgpf;

import android.content.pm.FeatureInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Hides the China-variant GMS system features from Google apps so they behave like the
 * global build: ApplicationPackageManager.hasSystemFeature(String[,int]) returns false for
 * them, and getSystemAvailableFeatures() filters them out.
 * Logic re-implemented from the idea of EX3124/UnlockCNGMS.
 */
final class CnFeatureHider {
    static final String FEATURE_CN_GMS = "cn.google.services";
    static final String FEATURE_SERVICES_UPDATER = "com.google.android.feature.services_updater";

    private final ModuleMain module;
    private boolean installed;

    CnFeatureHider(ModuleMain module) {
        this.module = module;
    }

    static boolean isHidden(Object name) {
        return FEATURE_CN_GMS.equals(name) || FEATURE_SERVICES_UPDATER.equals(name);
    }

    synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            Class<?> apm = Class.forName("android.app.ApplicationPackageManager");

            Method has1 = apm.getDeclaredMethod("hasSystemFeature", String.class);
            module.hook(has1).setId("cgpf.hasSystemFeature1").intercept(chain -> {
                if (module.hideCnFeaturesEnabled() && isHidden(chain.getArg(0))) return false;
                return chain.proceed();
            });

            Method has2 = apm.getDeclaredMethod("hasSystemFeature", String.class, int.class);
            module.hook(has2).setId("cgpf.hasSystemFeature2").intercept(chain -> {
                if (module.hideCnFeaturesEnabled() && isHidden(chain.getArg(0))) return false;
                return chain.proceed();
            });

            Method list = apm.getDeclaredMethod("getSystemAvailableFeatures");
            module.hook(list).setId("cgpf.getSystemAvailableFeatures").intercept(chain -> {
                Object result = chain.proceed();
                if (!module.hideCnFeaturesEnabled() || !(result instanceof FeatureInfo[])) return result;
                FeatureInfo[] all = (FeatureInfo[]) result;
                List<FeatureInfo> kept = new ArrayList<>(all.length);
                for (FeatureInfo f : all) {
                    if (f == null || isHidden(f.name)) continue;
                    kept.add(f);
                }
                return kept.toArray(new FeatureInfo[0]);
            });

            module.info("CN GMS feature hiding installed");
        } catch (Throwable t) {
            module.warn("CN GMS feature hiding failed: " + t);
        }
    }
}
