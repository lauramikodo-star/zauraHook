package com.applisto.appcloner;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;

public final class ClonerSettings {
    private static final String ASSET_FILE   = "cloner.json";
    private static final String RUNTIME_FILE = "cloner.json"; // inside /data/data/<pkg>/files/
    private static ClonerSettings INSTANCE;

    private final JSONObject cfg;

    private ClonerSettings(Context c) {
        try {
            Context appContext = c.getApplicationContext();

            // 1) Try runtime override first (for root/adb push)
            File runtime = new File(appContext.getFilesDir(), RUNTIME_FILE);
            if (runtime.exists()) {
                try (InputStream in = appContext.openFileInput(RUNTIME_FILE)) {
                    byte[] buf = new byte[(int) runtime.length()];
                    int read = in.read(buf);
                    if (read > 0) {
                        cfg = new JSONObject(new String(buf, 0, read));
                    } else {
                        cfg = new JSONObject();
                    }
                    Log.i("ClonerSettings", "Loaded runtime JSON");
                    return;
                }
            }

            // 2) Fall back to the asset (normal case)
            try (InputStream in = appContext.getAssets().open(ASSET_FILE)) {
                byte[] buf = new byte[in.available()];
                int read = in.read(buf);
                if (read > 0) {
                    cfg = new JSONObject(new String(buf, 0, read));
                } else {
                    cfg = new JSONObject();
                }
                Log.i("ClonerSettings", "Loaded asset JSON");
            }

        } catch (Exception e) {
            throw new RuntimeException("Cannot load config", e);
        }
    }

    public static synchronized ClonerSettings get(Context c) {
        if (INSTANCE == null) {
            INSTANCE = new ClonerSettings(c);
        }
        return INSTANCE;
    }

    /* existing helpers */
    public String androidId()        { return cfg.optString("android_id"); }
    public String wifiMac()          { return cfg.optString("wifi_mac");   }
    public String bluetoothMac()     { return cfg.optString("bluetooth_mac"); }
    public String userAgent()        { return cfg.optString("user_agent"); }
    public JSONObject raw()          { return cfg; }

    public boolean socksProxy()      { return cfg.optBoolean("socks_proxy", false); }
    public String socksProxyHost()   { return cfg.optString("socks_proxy_host"); }
    public int    socksProxyPort()   { return cfg.optInt("socks_proxy_port", 1080); }
    public String socksProxyUser()   { return cfg.optString("socks_proxy_user"); }
    public String socksProxyPass()   { return cfg.optString("socks_proxy_pass"); }

    /* NEW: settings for AccessibleDataDirHook
     *
     * Adjust the JSON keys ("accessible_data_dir_...") if your cloner.json
     * uses different names.
     */

    // Enable making the internal data directory accessible (default: true)
    public boolean accessibleDataDirInternalEnabled() {
        return cfg.optBoolean("accessible_data_dir_internal", true);
    }

    // Enable making the external data directory accessible (default: true)
    public boolean accessibleDataDirExternalEnabled() {
        return cfg.optBoolean("accessible_data_dir_external", true);
    }

    // Access mode: "READ_ONLY" or "READ_WRITE" (default: "READ_ONLY")
    public String accessibleDataDirMode() {
        return cfg.optString("accessible_data_dir_mode", "READ_ONLY");
    }

    // Periodic re-apply enabled? (default: false)
    public boolean accessibleDataDirAdvancedMode() {
        return cfg.optBoolean("accessible_data_dir_advanced_mode", false);
    }

    // Interval in seconds for advanced mode (default: 60)
    public long accessibleDataDirAdvancedInterval() {
        return cfg.optLong("accessible_data_dir_advanced_interval", 60L);
    }

    // Enable restoring bundled app data from assets on startup (default: false)
    public boolean bundleAppData() {
        return cfg.optBoolean("bundle_app_data", false);
    }

    // Enable internal browser for http/https links (default: false)
    public boolean internalBrowserEnabled() {
        return cfg.optBoolean("internal_browser", false);
    }

    /* Fake Calculator Settings */
    
    // Enable fake calculator entrance (default: false)
    public boolean fakeCalculatorEnabled() {
        return cfg.optBoolean("fake_calculator_enabled", false);
    }
    
    // Passcode to access the real app (default: "1234")
    public String fakeCalculatorPasscode() {
        return cfg.optString("fake_calculator_passcode", "1234");
    }

    // Ask once for passcode (default: false)
    public boolean fakeCalculatorAskOnce() {
        return cfg.optBoolean("fake_calculator_ask_once", false);
    }
    
    /* Build Props Hook Settings */
    
    // Enable build props override (default: true)
    public boolean buildPropsEnabled() {
        return cfg.optBoolean("build_props_enabled", true);
    }
    
    // Hook SystemProperties.get() for complete spoofing (default: true)
    public boolean buildPropsHookSystemProperties() {
        return cfg.optBoolean("build_props_hook_system_properties", true);
    }
    
    // Randomize fingerprint on each launch (default: false)
    public boolean buildPropsRandomizeFingerprint() {
        return cfg.optBoolean("build_props_randomize_fingerprint", false);
    }
    
    // Device preset to use (e.g., "samsung_s24_ultra", "pixel_8_pro")
    public String buildPropsDevicePreset() {
        return cfg.optString("build_props_device_preset", null);
    }
    
    /* Fake Camera Settings - organized to match screenshot categories */
    
    // Enable fake camera (default: false)
    public boolean fakeCameraEnabled() {
        return cfg.optBoolean("FakeCamera", false);
    }
    
    // Alternative Mode (default: false)
    public boolean fakeCameraAlternativeMode() {
        return cfg.optBoolean("FakeCameraAlternativeMode", false) ||
               cfg.optBoolean("AlternativeMode", false); // Legacy support
    }
    
    // App Support - hooks for camera-using apps (default: false)
    public boolean fakeCameraAppSupport() {
        return cfg.optBoolean("FakeCameraAppSupport", false);
    }
    
    // Close Stream Workaround (default: false)
    public boolean fakeCameraCloseStreamWorkaround() {
        return cfg.optBoolean("FakeCameraCloseStreamWorkaround", false);
    }
    
    // Fix Orientation (default: false)
    public boolean fakeCameraFixOrientation() {
        return cfg.optBoolean("FakeCameraFixOrientation", false);
    }
    
    // Flip Horizontally (default: false)
    public boolean fakeCameraFlipHorizontally() {
        return cfg.optBoolean("FlipHorizontally", false);
    }
    
    // Open Stream Workaround (default: false)
    public boolean fakeCameraOpenStreamWorkaround() {
        return cfg.optBoolean("FakeCameraOpenStreamWorkaround", false) ||
               cfg.optBoolean("OpenStreamWorkaround", false); // Legacy support
    }
    
    // Randomize the fake image slightly (default: false)
    public boolean fakeCameraRandomizeImage() {
        return cfg.optBoolean("RandomizeImage", false);
    }
    
    // Randomization strength (default: 25)
    public int fakeCameraRandomizeStrength() {
        return cfg.optInt("RandomizeStrength", 25);
    }
    
    // Resize Picture (default: false)
    public boolean fakeCameraResizeImage() {
        return cfg.optBoolean("ResizeImage", false);
    }
    
    // Rotation setting (default: "NO_CHANGE")
    public String fakeCameraRotation() {
        return cfg.optString("FakeCameraRotation", "NO_CHANGE");
    }
    
    // Use Original Image File (default: false)
    public boolean fakeCameraUseOriginalImageFile() {
        return cfg.optBoolean("FakeCameraUseOriginalImageFile", false);
    }
    
    // Legacy settings for backward compatibility
    
    // Fake camera image path (default: "fake_camera.jpg")
    public String fakeCameraImagePath() {
        return cfg.optString("FakeCameraImagePath", "fake_camera.jpg");
    }
    
    // Add EXIF attributes to fake photos (default: true)
    public boolean fakeCameraAddExifAttributes() {
        return cfg.optBoolean("AddExifAttributes", true);
    }
    
    // Add spoofed location to EXIF (default: false)
    public boolean fakeCameraAddSpoofedLocation() {
        return cfg.optBoolean("AddSpoofedLocation", false);
    }

    /* Device Identity Spoofing Settings */
    
    // Serial number spoofing (empty means keep original)
    public String serialNumber() {
        return cfg.optString("serial_number", "");
    }
    
    // IMEI spoofing (empty means keep original)
    public String imei() {
        return cfg.optString("imei", "");
    }
    
    // IMSI spoofing (empty means keep original)
    public String imsi() {
        return cfg.optString("imsi", "");
    }

    /* Dialog Blocker Settings */
    
    // Enable dialog blocking (default: false)
    public boolean dialogBlockerEnabled() {
        return cfg.optBoolean("dialog_blocker_enabled", false);
    }
    
    // Block update dialogs (default: true when blocker enabled)
    public boolean blockUpdateDialogs() {
        return cfg.optBoolean("block_update_dialogs", true);
    }
    
    // Block rating dialogs (default: true when blocker enabled)
    public boolean blockRatingDialogs() {
        return cfg.optBoolean("block_rating_dialogs", true);
    }
    
    // Block ad dialogs (default: true when blocker enabled)
    public boolean blockAdDialogs() {
        return cfg.optBoolean("block_ad_dialogs", true);
    }
    
    // Block subscription/premium dialogs (default: false)
    public boolean blockSubscriptionDialogs() {
        return cfg.optBoolean("block_subscription_dialogs", false);
    }
    
    // Custom keywords to block (comma-separated)
    public String dialogBlockKeywords() {
        return cfg.optString("dialog_block_keywords", "");
    }
    
    /* Location Spoofing Settings */
    
    // Enable location spoofing (default: false)
    public boolean spoofLocationEnabled() {
        return cfg.optBoolean("SpoofLocation", false);
    }
    
    // Spoofed latitude
    public double spoofLocationLatitude() {
        return cfg.optDouble("SpoofLocationLatitude", 0.0);
    }
    
    // Spoofed longitude
    public double spoofLocationLongitude() {
        return cfg.optDouble("SpoofLocationLongitude", 0.0);
    }
    
    // Spoofed altitude
    public double spoofLocationAltitude() {
        return cfg.optDouble("SpoofLocationAltitude", 10.0);
    }
    
    // Location accuracy
    public float spoofLocationAccuracy() {
        return (float) cfg.optDouble("SpoofLocationAccuracy", 5.0);
    }
    
    // Randomize location
    public boolean spoofLocationRandomize() {
        return cfg.optBoolean("SpoofLocationRandomize", false);
    }
    
    // Use IP-based location
    public boolean spoofLocationUseIp() {
        return cfg.optBoolean("SpoofLocationUseIp", false);
    }
    
    /**
     * Get a human-readable display name for a settings key.
     * This helps show user-friendly names in UI instead of raw config keys.
     */
    public static String getDisplayName(String key) {
        if (key == null) return "Unknown";
        
        switch (key) {
            // Fake Camera
            case "FakeCamera": return "Fake Camera";
            case "FakeCameraAlternativeMode": return "Alternative Mode";
            case "FakeCameraAppSupport": return "App Support";
            case "FakeCameraCloseStreamWorkaround": return "Close Stream Workaround";
            case "FakeCameraFixOrientation": return "Fix Orientation";
            case "FakeCameraOpenStreamWorkaround": return "Open Stream Workaround";
            case "FakeCameraRotation": return "Rotation";
            case "FlipHorizontally": return "Flip Horizontally";
            case "RandomizeImage": return "Randomize Picture";
            case "RandomizeStrength": return "Randomization Strength";
            case "ResizeImage": return "Resize Picture";
            case "AddExifAttributes": return "Add EXIF Attributes";
            
            // Device Identity
            case "android_id": return "Android ID";
            case "wifi_mac": return "Wi-Fi MAC";
            case "bluetooth_mac": return "Bluetooth MAC";
            case "serial_number": return "Serial Number";
            case "imei": return "IMEI";
            case "imsi": return "IMSI";
            case "user_agent": return "User Agent";
            
            // Build Props
            case "build_props_device_preset": return "Device Preset";
            case "build_MANUFACTURER": return "Manufacturer";
            case "build_MODEL": return "Model";
            case "build_BRAND": return "Brand";
            
            default:
                // Convert snake_case or camelCase to readable format
                return formatAsDisplayName(key);
        }
    }
    
    private static String formatAsDisplayName(String key) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : key.toCharArray()) {
            if (c == '_' || c == '-') {
                sb.append(' ');
                capitalizeNext = true;
            } else if (Character.isUpperCase(c) && sb.length() > 0 && 
                       sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
                sb.append(c);
                capitalizeNext = false;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        
        return sb.toString();
    }
}
