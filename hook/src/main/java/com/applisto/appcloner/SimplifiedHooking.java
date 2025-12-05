package com.applisto.appcloner;

import android.util.Log;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class SimplifiedHooking {
    private static final String TAG = "SimplifiedHooking";

    public abstract static class HookCallback extends XC_MethodHook {
        @Override
        public void beforeHookedMethod(MethodHookParam param) throws Throwable {
            before(param.thisObject, param.args, param);
        }

        @Override
        public void afterHookedMethod(MethodHookParam param) throws Throwable {
            after(param.thisObject, param.args, param);
        }

        public abstract void before(Object thisObject, Object[] args, MethodHookParam param) throws Throwable;

        public abstract void after(Object thisObject, Object[] args, MethodHookParam param) throws Throwable;
    }

    public static void hookMethod(Class<?> clazz, String methodName, HookCallback callback, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            XposedBridge.hookMethod(method, callback);
            Log.d(TAG, "Hooked method: " + clazz.getName() + "." + methodName);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Method not found: " + clazz.getName() + "." + methodName, e);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook: " + clazz.getName() + "." + methodName, t);
        }
    }

}
