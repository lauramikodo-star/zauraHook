package com.applisto.appcloner;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class AndroidIdHook {

    private static final String TAG = "AndroidIdHook";

    public void init(Context context) {
        String fakeId = ClonerSettings.get(context).androidId();

        // If empty, do not install hook (fallback to real ID)
        if (TextUtils.isEmpty(fakeId)) {
            Log.i(TAG, "No android_id configured, skipping hook (using real ID).");
            return;
        }

        Log.i(TAG, "Installing Android-ID hook → " + fakeId);

        hookSettingsMethod(Settings.Secure.class, fakeId);
        hookSettingsMethod(Settings.System.class, fakeId);
        hookSettingsMethod(Settings.Global.class, fakeId);
    }

    private void hookSettingsMethod(Class<?> settingsClass, final String fakeId) {
        try {
            Method target = settingsClass.getDeclaredMethod(
                    "getString", ContentResolver.class, String.class);

            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String key = (String) param.args[1];
                    if (Settings.Secure.ANDROID_ID.equals(key)) {
                        Log.d(TAG, "Returning fake ANDROID_ID for " + settingsClass.getSimpleName() + ": " + fakeId);
                        param.setResult(fakeId);
                    }
                }
            });
            Log.d(TAG, "Hooked " + settingsClass.getSimpleName() + ".getString");

        } catch (Throwable t) {
            // Some classes might not have the method or might fail to hook
            Log.w(TAG, "Failed to hook " + settingsClass.getSimpleName() + ".getString: " + t.getMessage());
        }
    }
}
