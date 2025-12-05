package com.applisto.appcloner;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Member;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class SmartHooking {

    private static final String TAG = "SmartHooking";

    /* ----------------------------------------------------------
       One-time init – call once from DefaultProvider.onCreate()
       ---------------------------------------------------------- */
    public static void init(Context ctx) {
        // AliuHook does not require initialization, but we keep this method
        // to avoid breaking existing callers.
        Log.i(TAG, "SmartHooking init called (AliuHook engine)");
    }

    /* ----------------------------------------------------------
       Public API – delegates to AliuHook (XposedBridge)
       ---------------------------------------------------------- */
    public static void hook(Member target, XC_MethodHook callback) {
        if (target == null || callback == null) return;

        try {
            XposedBridge.hookMethod(target, callback);
            Log.d(TAG, "Hooked: " + target);
        } catch (Throwable t) {
            Log.e(TAG, "Hook failed: " + target, t);
        }
    }
}
