package com.applisto.appcloner;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Random;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * Hook for spoofing Bluetooth MAC address.
 * Supports:
 * - BluetoothAdapter.getAddress()
 * - BluetoothAdapter.getDefaultAdapter().getAddress()
 */
public final class BtMacHook {
    private static final String TAG = "BtMacHook";
    private static String sFakeMac = null;

    public void init(Context ctx) {
        String configMac = ClonerSettings.get(ctx).bluetoothMac();
        if (TextUtils.isEmpty(configMac)) {
            Log.i(TAG, "No bluetooth_mac configured, skipping hook.");
            return;
        }
        
        sFakeMac = configMac.toUpperCase();
        Log.i(TAG, "Installing Bluetooth MAC hook → " + sFakeMac);

        // BluetoothAdapter.getAddress() - Spoofs local adapter MAC
        hookGetAddress();
        
        // Try to hook via reflection for Settings.Secure approach
        hookSettingsSecure(ctx);
    }
    
    private void hookGetAddress() {
        try {
            Method m = BluetoothAdapter.class.getDeclaredMethod("getAddress");
            Pine.hook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    Object orig = cf.getResult();
                    // Only replace if result is not null (i.e. BT is enabled)
                    if (orig != null) {
                        cf.setResult(sFakeMac);
                        Log.d(TAG, "Bluetooth MAC spoofed: " + orig + " → " + sFakeMac);
                    }
                }
            });
            Log.i(TAG, "✓ hooked BluetoothAdapter.getAddress()");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook BluetoothAdapter.getAddress()", t);
        }
    }
    
    /**
     * Hook Settings.Secure.getString() for bluetooth_address
     */
    private void hookSettingsSecure(Context ctx) {
        try {
            Class<?> settingsSecureClass = Class.forName("android.provider.Settings$Secure");
            Method getStringMethod = settingsSecureClass.getDeclaredMethod("getString", 
                    android.content.ContentResolver.class, String.class);
            
            Pine.hook(getStringMethod, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    Object[] args = cf.args;
                    if (args.length >= 2 && args[1] != null) {
                        String key = (String) args[1];
                        if ("bluetooth_address".equals(key)) {
                            Object orig = cf.getResult();
                            cf.setResult(sFakeMac);
                            Log.d(TAG, "Settings.Secure bluetooth_address spoofed: " + orig + " → " + sFakeMac);
                        }
                    }
                }
            });
            Log.i(TAG, "✓ hooked Settings.Secure.getString() for bluetooth_address");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook Settings.Secure.getString()", t);
        }
    }
    
    /**
     * Set a custom Bluetooth MAC at runtime
     */
    public static void setMac(String mac) {
        sFakeMac = mac != null ? mac.toUpperCase() : null;
        Log.i(TAG, "Bluetooth MAC updated to: " + sFakeMac);
    }
    
    /**
     * Get the current fake Bluetooth MAC
     */
    public static String getMac() {
        return sFakeMac;
    }
    
    /**
     * Generate a random Bluetooth MAC address
     */
    public static String generateRandomMac() {
        Random random = new Random();
        byte[] macBytes = new byte[6];
        random.nextBytes(macBytes);
        
        // Set locally administered bit and clear multicast bit
        macBytes[0] = (byte) ((macBytes[0] & 0xFC) | 0x02);
        
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", macBytes[i] & 0xFF));
        }
        return sb.toString();
    }
}