package com.applisto.appcloner;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Hook for spoofing Android device serial number.
 * Supports both Build.getSerial() method and Build.SERIAL field.
 */
public final class SerialHook {
    private static final String TAG = "SerialHook";
    private static String sFakeSerial = null;

    public void init(Context ctx) {
        // Load serial from settings
        String configSerial = ClonerSettings.get(ctx).serialNumber();
        if (TextUtils.isEmpty(configSerial)) {
            Log.i(TAG, "No serial_number configured, skipping hook.");
            return;
        }
        
        sFakeSerial = configSerial;
        Log.i(TAG, "Installing Serial hook → " + sFakeSerial);
        
        // Hook Build.getSerial() method (API 26+)
        try {
            Method m = Build.class.getDeclaredMethod("getSerial");
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override public void afterHookedMethod(MethodHookParam param) {
                    Object orig = param.getResult();
                    param.setResult(sFakeSerial);
                    Log.d(TAG, "Serial spoofed (getSerial) " + orig + " → " + sFakeSerial);
                }
            });
            Log.i(TAG, "✓ hooked Build.getSerial()");
        } catch (Throwable t) {
            Log.w(TAG, "Build.getSerial() not available", t);
        }
        
        // Also try to override the static Build.SERIAL field for older APIs
        try {
            Field serialField = Build.class.getDeclaredField("SERIAL");
            serialField.setAccessible(true);
            
            // Remove final modifier if present
            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(serialField, serialField.getModifiers() & ~Modifier.FINAL);
            } catch (NoSuchFieldException ignored) {}
            
            Object oldValue = serialField.get(null);
            serialField.set(null, sFakeSerial);
            Log.i(TAG, "✓ overrode Build.SERIAL: " + oldValue + " → " + sFakeSerial);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to override Build.SERIAL field", t);
        }
    }
    
    /**
     * Set a custom serial number at runtime
     */
    public static void setSerial(String serial) {
        sFakeSerial = serial;
        Log.i(TAG, "Serial updated to: " + serial);
    }
    
    /**
     * Get the current fake serial
     */
    public static String getSerial() {
        return sFakeSerial;
    }
}
