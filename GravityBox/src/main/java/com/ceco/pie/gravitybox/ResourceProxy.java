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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ResourceProxy {
    private static final String TAG = "GB:ResourceProxy";
    private static final boolean DEBUG = false;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static Map<String,SparseArray<ResourceSpec>> sCache = new HashMap<>();

    public static class ResourceSpec {
        public String pkgName;
        public int id;
        public String name;
        public Object value;
        private boolean isProcessed;
        private boolean isOverridden;

        private ResourceSpec(String pkgName, int id, String name, Object value) {
            this.pkgName = pkgName;
            this.id = id;
            this.name = name;
            this.value = value;
        }

        @Override
        public String toString() {
            return "ResourceSpec{" +
                    "pkg=" + pkgName +
                    ", id=" + id +
                    ", name='" + name + '\'' +
                    ", value=" + value +
                    '}';
        }
    }

    static abstract class Interceptor {
        private List<String> mSupportedResourceNames;
        private List<Integer> mSupportedFakeResIds;

        Interceptor(List<String> supportedResourceNames, List<Integer> supportedFakeResIds) {
            mSupportedResourceNames = supportedResourceNames;
            mSupportedFakeResIds = supportedFakeResIds;
        }

        Interceptor(List<String> supportedResourceNames) {
            this(supportedResourceNames, new ArrayList<>());
        }

        List<String> getSupportedResourceNames() {
            return mSupportedResourceNames;
        }

        List<Integer> getSupportedFakeResIds() {
            return mSupportedFakeResIds;
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

    private final Map<String, Interceptor> mInterceptors = new HashMap<>();

    ResourceProxy() {
        createIntegerHook();
        createBooleanHook();
        createDimensionHook();
        createDimensionPixelOffsetHook();
        createDimensionPixelSizeHook();
        createStringHook();
        createDrawableHook();
    }

    void addInterceptor(String pkgName, Interceptor interceptor) {
        synchronized (mInterceptors) {
            if (!mInterceptors.containsKey(pkgName)) {
                mInterceptors.put(pkgName, interceptor);
            }
        }
    }

    private XC_MethodHook mInterceptHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            final int resId = (int)param.args[0];
            synchronized (mInterceptors) {
                for (Interceptor interceptor : mInterceptors.values()) {
                    if (interceptor.getSupportedFakeResIds().contains(resId)) {
                        Context gbContext = getGbContext(((Resources) param.thisObject).getConfiguration());
                        if (gbContext != null) {
                            Object value = interceptor.onGetFakeResource(gbContext, resId);
                            if (value != null) {
                                if (DEBUG) log("onGetFakeResource: resId=" + resId + "; value=" + value);
                                param.setResult(value);
                                return;
                            }
                        }
                    }
                }
            }
        }
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
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
                synchronized (mInterceptors) {
                    if (mInterceptors.containsKey(spec.pkgName)) {
                        if (mInterceptors.get(spec.pkgName).onIntercept(spec) &&
                                value.getClass().isAssignableFrom(spec.value.getClass())) {
                            if (DEBUG) log(param.method.getName() + ": " + spec.toString());
                            spec.isOverridden = true;
                            param.setResult(spec.value);
                        }
                    }
                }
                spec.isProcessed = true;
            }
        }
    };

    private ResourceSpec getOrCreateResourceSpec(Resources res, int id, Object value) {
        final String resPkgName = getResourcePackageName(res, id);
        if (resPkgName == null)
            return null;
        if (sCache.containsKey(resPkgName) && sCache.get(resPkgName).get(id) != null)
            return sCache.get(resPkgName).get(id);

        final String name = getResourceEntryName(res, id);
        if (name == null)
            return null;

        ResourceSpec spec = null;
        synchronized (mInterceptors) {
            for (Map.Entry<String,Interceptor> entry : mInterceptors.entrySet()) {
                if (entry.getKey().equals(resPkgName) &&
                        entry.getValue().getSupportedResourceNames().contains(name)) {
                    spec = new ResourceSpec(resPkgName, id, name, value);
                    if (DEBUG) log("New " + spec.toString());
                }
            }
            // handle potential alias pointing to Framework res
            if (spec == null && mInterceptors.containsKey("android") &&
                    mInterceptors.containsKey(resPkgName) &&
                    mInterceptors.get("android").getSupportedResourceNames().contains(name)) {
                spec = new ResourceSpec("android", id, name, value);
                if (DEBUG) log("Using android interceptor for " + resPkgName + ": " + spec.toString());
            }
            if (spec != null) {
                if (!sCache.containsKey(resPkgName)) {
                    sCache.put(resPkgName, new SparseArray<>());
                }
                sCache.get(resPkgName).put(id, spec);
            }
        }

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
