/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceco.pie.gravitybox;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ResourceProxy {
    private static final String TAG = "GB:ResourceProxy";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static SparseArray<ResourceSpec> sCache = new SparseArray<>();
    private static int getCacheKey(String pkgName, int resId) {
        return (pkgName + "_" + resId).hashCode();
    }

    public static class ResourceSpec {
        private Interceptor interceptor;
        public int resId;
        public String name;
        public Object value;
        private boolean isProcessed;
        private boolean isOverridden;

        private ResourceSpec(Interceptor interceptor, int resId, String name, Object value) {
            this.interceptor = interceptor;
            this.resId = resId;
            this.name = name;
            this.value = value;
        }

        public String getPackageName() {
            return interceptor.supportedPackageName;
        }

        @Override
        public String toString() {
            return "ResourceSpec{" +
                    "pkgName=" + getPackageName() +
                    ", resId=" + resId +
                    ", name='" + name + '\'' +
                    ", value=" + value +
                    '}';
        }
    }

    static abstract class Interceptor {
        private String supportedPackageName;
        private List<String> supportedResourceNames;
        private List<Integer> supportedFakeResIds;
        private boolean isFramework;
        private Pattern packageNamePattern;

        Interceptor(String packageName ,List<String> supportedResourceNames,
                    List<Integer> supportedFakeResIds) {
            this.supportedPackageName = packageName;
            this.packageNamePattern = Pattern.compile("^" +
                    this.supportedPackageName.replace(".", "\\.") +
                    "+((?:\\.\\w+)+)?$");
            this.isFramework = "android".equals(packageName);
            this.supportedResourceNames = supportedResourceNames;
            this.supportedFakeResIds = supportedFakeResIds;
        }

        Interceptor(String packageName, List<String> supportedResourceNames) {
            this(packageName, supportedResourceNames, new ArrayList<>());
        }

        abstract boolean onIntercept(ResourceSpec resourceSpec);
        Object onGetFakeResource(Context gbContext, int fakeResId) { return null; }
    }

    static int getFakeResId(String resourceName) {
        return 0x7e000000 | (resourceName.hashCode() & 0x00ffffff);
    }

    private static Context getGbContext(Configuration config) {
        try {
            Class<?> atClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object currentAt = XposedHelpers.callStaticMethod(atClass, "currentActivityThread");
            Context systemContext = (Context) XposedHelpers.callMethod(currentAt, "getSystemContext");
            return Utils.getGbContext(systemContext, config);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
            return null;
        }
    }

    private final List<Interceptor> mInterceptors = new ArrayList<>();

    ResourceProxy() {
        createIntegerHook();
        createBooleanHook();
        createDimensionHook();
        createDimensionPixelOffsetHook();
        createDimensionPixelSizeHook();
        createStringHook();
        createDrawableHook();
    }

    void addInterceptor(Interceptor interceptor) {
        synchronized (mInterceptors) {
            if (!mInterceptors.contains(interceptor)) {
                mInterceptors.add(interceptor);
            }
        }
    }

    private Interceptor findInterceptorForFramework() {
        synchronized (mInterceptors) {
            return mInterceptors.stream().filter((i) -> i.isFramework)
                    .findFirst().orElse(null);
        }
    }

    private Interceptor findInterceptorForResource(String packageName, String resName) {
        if (packageName == null || resName == null) return null;
        Interceptor fwi = findInterceptorForFramework();
        synchronized (mInterceptors) {
            for (Interceptor i : mInterceptors) {
                if (i.packageNamePattern.matcher(packageName).matches()) {
                    if (i.supportedResourceNames.contains(resName)) {
                        return i;
                    } else if (fwi != null && fwi.supportedResourceNames.contains(resName)) {
                        return  fwi;
                    }
                }
            }
        }
        return null;
    }

    private Interceptor findInterceptorForFakeResourceId(int fakeResId) {
        synchronized (mInterceptors) {
            return mInterceptors.stream().filter((i) ->
                    i.supportedFakeResIds.contains(fakeResId))
                    .findFirst().orElse(null);
        }
    }

    private XC_MethodHook mInterceptHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            final int resId = (int)param.args[0];
            Interceptor i = findInterceptorForFakeResourceId(resId);
            if (i == null) return;

            Context gbContext = getGbContext(((Resources) param.thisObject).getConfiguration());
            if (gbContext != null) {
                Object value = i.onGetFakeResource(gbContext, resId);
                if (value != null) {
                    if (DEBUG) log("onGetFakeResource: resId=" + resId + "; value=" + value);
                    param.setResult(value);
                    param.getExtra().putBoolean("returnEarly", true);
                }
            }
        }
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.getExtra().getBoolean("returnEarly")) {
                if (DEBUG) log(param.method.getName() + " after hook suppressed by before hook");
                return;
            }
            Object value = param.getResult();
            ResourceSpec spec = getOrCreateResourceSpec((Resources)param.thisObject,
                    (int)param.args[0], value);
            if (spec != null) {
                if (spec.isProcessed) {
                    if (spec.isOverridden) {
                        param.setResult(spec.value);
                    }
                    return;
                }
                if (spec.interceptor.onIntercept(spec) &&
                        value.getClass().isAssignableFrom(spec.value.getClass())) {
                    if (DEBUG) log(param.method.getName() + ": onIntercept: " + spec.toString());
                    spec.isOverridden = true;
                    param.setResult(spec.value);
                }
                spec.isProcessed = true;
            }
        }
    };

    private ResourceSpec getOrCreateResourceSpec(Resources res, int resId, Object value) {
        String pkgName = getResourcePackageName(res, resId);
        if (pkgName == null) return null;

        int cacheKey = getCacheKey(pkgName, resId);
        if (sCache.get(cacheKey) != null)
            return sCache.get(cacheKey);

        String resName = getResourceEntryName(res, resId);
        if (resName == null) return null;

        Interceptor i = findInterceptorForResource(pkgName, resName);
        if (i == null) return null;

        ResourceSpec spec = new ResourceSpec(i, resId, resName, value);
        if (DEBUG) log("New " + spec.toString());
        sCache.put(cacheKey, spec);
        return spec;
    }

    private static String getResourcePackageName(Resources res, int id) {
        try {
            return res.getResourcePackageName(id);
        } catch (Resources.NotFoundException e) {
            if (DEBUG) GravityBox.log(TAG, "Error in getResourcePackageName:", e);
            return null;
        }
    }

    private static String getResourceEntryName(Resources res, int id) {
        try {
            return res.getResourceEntryName(id);
        } catch (Resources.NotFoundException e) {
            if (DEBUG) GravityBox.log(TAG, "Error in getResourceEntryName:", e);
            return null;
        }
    }

    private void createIntegerHook() {
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getInteger",
                    int.class, mInterceptHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void createBooleanHook() {
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getBoolean",
                    int.class, mInterceptHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void createDimensionHook() {
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getDimension",
                    int.class, mInterceptHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void createDimensionPixelOffsetHook() {
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getDimensionPixelOffset",
                    int.class, mInterceptHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void createDimensionPixelSizeHook() {
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getDimensionPixelSize",
                    int.class, mInterceptHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void createStringHook() {
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getText",
                    int.class, mInterceptHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void createDrawableHook() {
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getDrawableForDensity",
                    int.class, int.class, Resources.Theme.class, mInterceptHook);
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }
}
