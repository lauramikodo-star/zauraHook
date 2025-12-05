package com.applisto.appcloner;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * Hook for spoofing IMSI (International Mobile Subscriber Identity).
 * Supports TelephonyManager.getSubscriberId() for all subscription variants.
 */
public class ImsiHook {
    private static final String TAG = "ImsiHook";
    private static String sFakeImsi = null;
    private static final String DEFAULT_FAKE_IMSI = "310260000000000"; // Default T-Mobile US

    public void init(Context context) {
        Log.i(TAG, "Initializing IMSI hook...");
        
        // Load IMSI from settings
        String configImsi = ClonerSettings.get(context).imsi();
        if (TextUtils.isEmpty(configImsi)) {
            Log.i(TAG, "No imsi configured, using default: " + DEFAULT_FAKE_IMSI);
            sFakeImsi = DEFAULT_FAKE_IMSI;
        } else {
            sFakeImsi = configImsi;
        }
        
        Log.i(TAG, "Installing IMSI hook → " + sFakeImsi);

        // Hook getSubscriberId() - no parameters version
        try {
            Method m = TelephonyManager.class.getDeclaredMethod("getSubscriberId");
            Pine.hook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    Object orig = cf.getResult();
                    cf.setResult(sFakeImsi);
                    Log.d(TAG, "IMSI spoofed: " + orig + " → " + sFakeImsi);
                }
            });
            Log.i(TAG, "✓ hooked TelephonyManager.getSubscriberId()");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook getSubscriberId()", t);
        }
        
        // Hook getSubscriberId(int subId) - subscription variant (API 22+)
        try {
            Method m = TelephonyManager.class.getDeclaredMethod("getSubscriberId", int.class);
            Pine.hook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    Object orig = cf.getResult();
                    cf.setResult(sFakeImsi);
                    Log.d(TAG, "IMSI spoofed (subId): " + orig + " → " + sFakeImsi);
                }
            });
            Log.i(TAG, "✓ hooked TelephonyManager.getSubscriberId(int)");
        } catch (Throwable t) {
            Log.w(TAG, "getSubscriberId(int) not available", t);
        }
    }
    
    /**
     * Set a custom IMSI at runtime
     */
    public static void setImsi(String imsi) {
        sFakeImsi = imsi;
        Log.i(TAG, "IMSI updated to: " + imsi);
    }
    
    /**
     * Get the current fake IMSI
     */
    public static String getImsi() {
        return sFakeImsi;
    }
    
    /**
     * Generate a random IMSI
     * @param mcc Mobile Country Code (3 digits)
     * @param mnc Mobile Network Code (2-3 digits)
     * @return Random IMSI string
     */
    public static String generateRandomImsi(String mcc, String mnc) {
        StringBuilder sb = new StringBuilder();
        sb.append(mcc);
        sb.append(mnc);
        // Fill remaining 10-11 digits randomly
        int remaining = 15 - sb.length();
        for (int i = 0; i < remaining; i++) {
            sb.append((int) (Math.random() * 10));
        }
        return sb.toString();
    }
}
