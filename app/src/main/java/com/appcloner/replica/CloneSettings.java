package com.appcloner.replica;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * CloneSettings - Hardcoded default settings class for cloner.json generation.
 * This class contains all the default settings that will be used when creating
 * a new cloner.json configuration file for cloned applications.
 */
public class CloneSettings {
    private static final String TAG = "CloneSettings";
    private static final SecureRandom random = new SecureRandom();

    // ===== Cloning Mode Settings =====
    public static final String CLONING_MODE_REPLACE = "replace_original";
    public static final String CLONING_MODE_GENERATE = "generate_new_package";
    public static final String CLONING_MODE_CUSTOM = "custom_package";
    public static final String DEFAULT_CLONING_MODE = CLONING_MODE_REPLACE;
    
    // ===== App Name and Icon Settings =====
    public static final String DEFAULT_APP_NAME = "";  // Empty means use original
    public static final String DEFAULT_ICON_COLOR = "#FFFFFF";
    public static final int DEFAULT_ICON_ROTATION = 0;  // 0, 90, 180, 270
    public static final boolean DEFAULT_ICON_FLIP_HORIZONTAL = false;
    public static final boolean DEFAULT_ICON_FLIP_VERTICAL = false;
    public static final String DEFAULT_ICON_BADGE = "";  // Empty means no badge
    public static final int DEFAULT_ICON_BADGE_POSITION = 0;  // 0=top-right, 1=top-left, 2=bottom-right, 3=bottom-left

    // ===== Device Identity Settings =====
    public static final String DEFAULT_ANDROID_ID = "settings_secure_android_id_spoof";
    public static final String DEFAULT_WIFI_MAC = "02:00:00:00:00:00";
    public static final String DEFAULT_BLUETOOTH_MAC = "";
    public static final String DEFAULT_SERIAL_NUMBER = "";  // Empty means keep original
    public static final String DEFAULT_IMEI = "";  // Empty means keep original
    public static final String DEFAULT_IMSI = "";  // Empty means keep original
    public static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";

    // ===== Build Props / Device Spoofing Settings =====
    public static final boolean DEFAULT_BUILD_PROPS_ENABLED = false;
    public static final String DEFAULT_BUILD_PROPS_DEVICE_PRESET = "";  // e.g., "samsung_s24_ultra", "pixel_8_pro"
    public static final boolean DEFAULT_BUILD_PROPS_RANDOMIZE_FINGERPRINT = false;
    public static final String DEFAULT_BUILD_MANUFACTURER = "";
    public static final String DEFAULT_BUILD_MODEL = "";
    public static final String DEFAULT_BUILD_BRAND = "";
    public static final String DEFAULT_BUILD_DEVICE = "";
    public static final String DEFAULT_BUILD_PRODUCT = "";
    public static final String DEFAULT_BUILD_FINGERPRINT = "";

    // ===== Dialog Intercept / Blocker Settings =====
    public static final boolean DEFAULT_DIALOG_BLOCKER_ENABLED = false;
    public static final boolean DEFAULT_BLOCK_UPDATE_DIALOGS = true;
    public static final boolean DEFAULT_BLOCK_RATING_DIALOGS = true;
    public static final boolean DEFAULT_BLOCK_AD_DIALOGS = true;
    public static final boolean DEFAULT_BLOCK_SUBSCRIPTION_DIALOGS = false;
    public static final String DEFAULT_DIALOG_BLOCK_KEYWORDS = "update,rate,review,premium,subscribe,upgrade,install,download";

    // ===== Fake Calculator Settings =====
    public static final boolean DEFAULT_FAKE_CALCULATOR_ENABLED = false;
    public static final String DEFAULT_FAKE_CALCULATOR_PASSCODE = "1234";  // Numbers only, no = required
    public static final boolean DEFAULT_FAKE_CALCULATOR_ASK_ONCE = false;  // Ask for passcode only once per session

    // ===== Fake Camera Settings =====
    // Settings organized to match the screenshot categories
    public static final boolean DEFAULT_FAKE_CAMERA_ENABLED = true;
    public static final boolean DEFAULT_FAKE_CAMERA_ALTERNATIVE_MODE = false;  // Alternative Mode
    public static final boolean DEFAULT_FAKE_CAMERA_APP_SUPPORT = false;       // App Support
    public static final boolean DEFAULT_FAKE_CAMERA_CLOSE_STREAM_WORKAROUND = false; // Close Stream Workaround
    public static final boolean DEFAULT_FAKE_CAMERA_FIX_ORIENTATION = false;   // Fix Orientation
    public static final boolean DEFAULT_FLIP_HORIZONTALLY = false;             // Flip Horizontally
    public static final boolean DEFAULT_FAKE_CAMERA_OPEN_STREAM_WORKAROUND = false; // Open Stream Workaround
    public static final boolean DEFAULT_RANDOMIZE_IMAGE = false;               // Randomize Picture
    public static final int DEFAULT_RANDOMIZE_STRENGTH = 25;                   // Randomize Picture Strength (default 25 as in screenshot)
    public static final boolean DEFAULT_RESIZE_IMAGE = false;                  // Resize Picture
    public static final String DEFAULT_FAKE_CAMERA_ROTATION = "NO_CHANGE";     // Rotation (NO_CHANGE default)
    public static final boolean DEFAULT_FAKE_CAMERA_USE_ORIGINAL_IMAGE_FILE = false; // Use Original Image File
    // Legacy settings kept for compatibility
    public static final boolean DEFAULT_ROTATE_IMAGE = false;
    public static final int DEFAULT_ROTATION_ANGLE = 0;
    public static final boolean DEFAULT_ADD_EXIF_ATTRIBUTES = true;
    public static final boolean DEFAULT_ALTERNATIVE_MODE = false;
    public static final boolean DEFAULT_OPEN_STREAM_WORKAROUND = false;
    public static final boolean DEFAULT_USE_RANDOM_IMAGE = false;
    public static final boolean DEFAULT_PRESERVE_ASPECT_RATIO = true;
    public static final boolean DEFAULT_CENTER_IMAGE = true;
    public static final boolean DEFAULT_FILL_IMAGE = false;
    public static final boolean DEFAULT_HOOK_LOW_LEVEL_APIS = false;
    public static final boolean DEFAULT_SYSTEM_CAMERA_WORKAROUND = false;
    public static final boolean DEFAULT_FORCED_BACK_CAMERA_ENABLED = true;

    // ===== Display Settings =====
    public static final boolean DEFAULT_ALLOW_SCREENSHOTS = true;

    // ===== Floating Window Settings =====
    public static final boolean DEFAULT_FLOATING_APP_ENABLED = true;
    public static final int DEFAULT_FLOATING_WINDOW_WIDTH = 800;
    public static final int DEFAULT_FLOATING_WINDOW_HEIGHT = 1000;
    public static final int DEFAULT_FLOATING_WINDOW_X = 50;
    public static final int DEFAULT_FLOATING_WINDOW_Y = 50;

    // ===== Background Media Settings =====
    public static final boolean DEFAULT_BACKGROUND_MEDIA = true;
    public static final boolean DEFAULT_BACKGROUND_MEDIA_WEBVIEW = true;
    public static final boolean DEFAULT_BACKGROUND_MEDIA_MEDIAPLAYER = true;
    public static final boolean DEFAULT_BACKGROUND_MEDIA_EXOPLAYER = true;
    public static final boolean DEFAULT_BACKGROUND_MEDIA_AUDIO_FOCUS = true;

    // ===== SOCKS Proxy Settings =====
    public static final boolean DEFAULT_SOCKS_PROXY_ENABLED = false;
    public static final String DEFAULT_SOCKS_PROXY_HOST = "";
    public static final int DEFAULT_SOCKS_PROXY_PORT = 1080;
    public static final String DEFAULT_SOCKS_PROXY_USER = "";
    public static final String DEFAULT_SOCKS_PROXY_PASS = "";

    // ===== Location Spoofing Settings =====
    public static final boolean DEFAULT_SPOOF_LOCATION = true;
    public static final double DEFAULT_SPOOF_LOCATION_LATITUDE = 48.8584;
    public static final double DEFAULT_SPOOF_LOCATION_LONGITUDE = 2.2945;
    public static final double DEFAULT_SPOOF_LOCATION_ALTITUDE = 35.0;
    public static final double DEFAULT_SPOOF_LOCATION_ACCURACY = 5.0;

    // ===== Accessible Data Directory Settings =====
    public static final boolean DEFAULT_ACCESSIBLE_DATA_DIR_INTERNAL_ENABLED = false;
    public static final boolean DEFAULT_ACCESSIBLE_DATA_DIR_EXTERNAL_ENABLED = false;
    public static final String DEFAULT_ACCESSIBLE_DATA_DIR_MODE = "normal";
    public static final boolean DEFAULT_ACCESSIBLE_DATA_DIR_ADVANCED_MODE = false;
    public static final int DEFAULT_ACCESSIBLE_DATA_DIR_ADVANCED_INTERVAL = 1000;
    public static final boolean DEFAULT_BUNDLE_APP_DATA = false;

    // ===== WebView Filter Settings =====
    public static final boolean DEFAULT_WEBVIEW_FILTER_ENABLED = false;
    public static final boolean DEFAULT_WEBVIEW_FILTER_DEBUG = false;
    public static final boolean DEFAULT_WEBVIEW_FILTER_REWRITE_RESPONSES = false;
    public static final int DEFAULT_WEBVIEW_FILTER_MAX_REWRITE_SIZE_KB = 512;
    public static final String[] DEFAULT_WEBVIEW_FILTER_REWRITE_CONTENT_TYPES = {
            "text/html",
            "application/json",
            "application/javascript",
            "text/javascript",
            "text/css",
            "text/plain"
    };

    /**
     * Creates a new JSONObject with all default settings.
     * @return JSONObject containing all default clone settings
     */
    public static JSONObject createDefaultSettings() {
        JSONObject json = new JSONObject();
        try {
            // Cloning Mode
            json.put("cloning_mode", DEFAULT_CLONING_MODE);
            json.put("custom_package_name", "");
            
            // App Name and Icon
            json.put("app_name", DEFAULT_APP_NAME);
            json.put("icon_color", DEFAULT_ICON_COLOR);
            json.put("icon_rotation", DEFAULT_ICON_ROTATION);
            json.put("icon_flip_horizontal", DEFAULT_ICON_FLIP_HORIZONTAL);
            json.put("icon_flip_vertical", DEFAULT_ICON_FLIP_VERTICAL);
            json.put("icon_badge", DEFAULT_ICON_BADGE);
            json.put("icon_badge_position", DEFAULT_ICON_BADGE_POSITION);

            // Device Identity
            json.put("android_id", DEFAULT_ANDROID_ID);
            json.put("wifi_mac", DEFAULT_WIFI_MAC);
            json.put("bluetooth_mac", DEFAULT_BLUETOOTH_MAC);
            json.put("serial_number", DEFAULT_SERIAL_NUMBER);
            json.put("imei", DEFAULT_IMEI);
            json.put("imsi", DEFAULT_IMSI);
            json.put("user_agent", DEFAULT_USER_AGENT);

            // Build Props / Device Spoofing
            json.put("build_props_enabled", DEFAULT_BUILD_PROPS_ENABLED);
            json.put("build_props_device_preset", DEFAULT_BUILD_PROPS_DEVICE_PRESET);
            json.put("build_props_randomize_fingerprint", DEFAULT_BUILD_PROPS_RANDOMIZE_FINGERPRINT);
            json.put("build_MANUFACTURER", DEFAULT_BUILD_MANUFACTURER);
            json.put("build_MODEL", DEFAULT_BUILD_MODEL);
            json.put("build_BRAND", DEFAULT_BUILD_BRAND);
            json.put("build_DEVICE", DEFAULT_BUILD_DEVICE);
            json.put("build_PRODUCT", DEFAULT_BUILD_PRODUCT);
            json.put("build_FINGERPRINT", DEFAULT_BUILD_FINGERPRINT);

            // Dialog Blocker
            json.put("dialog_blocker_enabled", DEFAULT_DIALOG_BLOCKER_ENABLED);
            json.put("block_update_dialogs", DEFAULT_BLOCK_UPDATE_DIALOGS);
            json.put("block_rating_dialogs", DEFAULT_BLOCK_RATING_DIALOGS);
            json.put("block_ad_dialogs", DEFAULT_BLOCK_AD_DIALOGS);
            json.put("block_subscription_dialogs", DEFAULT_BLOCK_SUBSCRIPTION_DIALOGS);
            json.put("dialog_block_keywords", DEFAULT_DIALOG_BLOCK_KEYWORDS);

            // Fake Calculator
            json.put("fake_calculator_enabled", DEFAULT_FAKE_CALCULATOR_ENABLED);
            json.put("fake_calculator_passcode", DEFAULT_FAKE_CALCULATOR_PASSCODE);
            json.put("fake_calculator_ask_once", DEFAULT_FAKE_CALCULATOR_ASK_ONCE);

            // Fake Camera - organized to match screenshot categories
            json.put("FakeCamera", DEFAULT_FAKE_CAMERA_ENABLED);
            json.put("FakeCameraAlternativeMode", DEFAULT_FAKE_CAMERA_ALTERNATIVE_MODE);
            json.put("FakeCameraAppSupport", DEFAULT_FAKE_CAMERA_APP_SUPPORT);
            json.put("FakeCameraCloseStreamWorkaround", DEFAULT_FAKE_CAMERA_CLOSE_STREAM_WORKAROUND);
            json.put("FakeCameraFixOrientation", DEFAULT_FAKE_CAMERA_FIX_ORIENTATION);
            json.put("FlipHorizontally", DEFAULT_FLIP_HORIZONTALLY);
            json.put("FakeCameraOpenStreamWorkaround", DEFAULT_FAKE_CAMERA_OPEN_STREAM_WORKAROUND);
            json.put("RandomizeImage", DEFAULT_RANDOMIZE_IMAGE);
            json.put("RandomizeStrength", DEFAULT_RANDOMIZE_STRENGTH);
            json.put("ResizeImage", DEFAULT_RESIZE_IMAGE);
            json.put("FakeCameraRotation", DEFAULT_FAKE_CAMERA_ROTATION);
            json.put("FakeCameraUseOriginalImageFile", DEFAULT_FAKE_CAMERA_USE_ORIGINAL_IMAGE_FILE);
            // Legacy settings for backward compatibility
            json.put("AddExifAttributes", DEFAULT_ADD_EXIF_ATTRIBUTES);
            json.put("ForcedBackCamera", DEFAULT_FORCED_BACK_CAMERA_ENABLED);

            // Display
            json.put("AllowScreenshots", DEFAULT_ALLOW_SCREENSHOTS);

            // Floating Window
            json.put("floating_app", DEFAULT_FLOATING_APP_ENABLED);
            json.put("floating_window_width", DEFAULT_FLOATING_WINDOW_WIDTH);
            json.put("floating_window_height", DEFAULT_FLOATING_WINDOW_HEIGHT);
            json.put("floating_window_x", DEFAULT_FLOATING_WINDOW_X);
            json.put("floating_window_y", DEFAULT_FLOATING_WINDOW_Y);

            // Background Media
            json.put("background_media", DEFAULT_BACKGROUND_MEDIA);
            json.put("background_media_webview", DEFAULT_BACKGROUND_MEDIA_WEBVIEW);
            json.put("background_media_mediaplayer", DEFAULT_BACKGROUND_MEDIA_MEDIAPLAYER);
            json.put("background_media_exoplayer", DEFAULT_BACKGROUND_MEDIA_EXOPLAYER);
            json.put("background_media_audio_focus", DEFAULT_BACKGROUND_MEDIA_AUDIO_FOCUS);

            // SOCKS Proxy
            json.put("socks_proxy", DEFAULT_SOCKS_PROXY_ENABLED);
            json.put("socks_proxy_host", DEFAULT_SOCKS_PROXY_HOST);
            json.put("socks_proxy_port", DEFAULT_SOCKS_PROXY_PORT);
            json.put("socks_proxy_user", DEFAULT_SOCKS_PROXY_USER);
            json.put("socks_proxy_pass", DEFAULT_SOCKS_PROXY_PASS);

            // Location Spoofing
            json.put("SpoofLocation", DEFAULT_SPOOF_LOCATION);
            json.put("SpoofLocationLatitude", DEFAULT_SPOOF_LOCATION_LATITUDE);
            json.put("SpoofLocationLongitude", DEFAULT_SPOOF_LOCATION_LONGITUDE);
            json.put("SpoofLocationAltitude", DEFAULT_SPOOF_LOCATION_ALTITUDE);
            json.put("SpoofLocationAccuracy", DEFAULT_SPOOF_LOCATION_ACCURACY);

            // Accessible Data Directory
            json.put("accessible_data_dir_internal", DEFAULT_ACCESSIBLE_DATA_DIR_INTERNAL_ENABLED);
            json.put("accessible_data_dir_external", DEFAULT_ACCESSIBLE_DATA_DIR_EXTERNAL_ENABLED);
            json.put("accessible_data_dir_mode", DEFAULT_ACCESSIBLE_DATA_DIR_MODE);
            json.put("accessible_data_dir_advanced_mode", DEFAULT_ACCESSIBLE_DATA_DIR_ADVANCED_MODE);
            json.put("accessible_data_dir_advanced_interval", DEFAULT_ACCESSIBLE_DATA_DIR_ADVANCED_INTERVAL);
            json.put("bundle_app_data", DEFAULT_BUNDLE_APP_DATA);

            // WebView Filter
            json.put("webview_filter", createDefaultWebViewFilterSettings());

        } catch (JSONException e) {
            Log.e(TAG, "Error creating default settings JSON", e);
        }
        return json;
    }

    /**
     * Creates the default WebView filter settings object.
     * @return JSONObject containing WebView filter settings
     */
    public static JSONObject createDefaultWebViewFilterSettings() throws JSONException {
        JSONObject webviewFilter = new JSONObject();
        webviewFilter.put("enabled", DEFAULT_WEBVIEW_FILTER_ENABLED);
        webviewFilter.put("debug", DEFAULT_WEBVIEW_FILTER_DEBUG);
        webviewFilter.put("rewrite_responses", DEFAULT_WEBVIEW_FILTER_REWRITE_RESPONSES);
        webviewFilter.put("max_rewrite_size_kb", DEFAULT_WEBVIEW_FILTER_MAX_REWRITE_SIZE_KB);
        webviewFilter.put("rewrite_content_types", new JSONArray(DEFAULT_WEBVIEW_FILTER_REWRITE_CONTENT_TYPES));

        // Default rules
        JSONArray rules = new JSONArray();
        JSONObject defaultRule = new JSONObject();
        defaultRule.put("url_regex", "https?://(www\\.)?example\\.com/.*");
        defaultRule.put("url_block_if_matching", true);
        defaultRule.put("url_replacement", "");
        defaultRule.put("url_encode_replacement", false);
        defaultRule.put("data_regex", "");
        defaultRule.put("data_ignore_case", false);
        defaultRule.put("data_block_if_matching", false);
        defaultRule.put("data_replacement", "");
        defaultRule.put("data_replace_all", false);
        rules.put(defaultRule);
        webviewFilter.put("rules", rules);

        return webviewFilter;
    }

    /**
     * Generates a cloner.json file with default settings at the specified location.
     * @param context The application context
     * @param outputFile The file where cloner.json should be written
     * @return true if file was created successfully, false otherwise
     */
    public static boolean generateClonerJson(Context context, File outputFile) {
        if (outputFile == null) {
            Log.e(TAG, "Output file cannot be null");
            return false;
        }

        try {
            JSONObject settings = createDefaultSettings();
            String jsonString = settings.toString(2);

            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Log.e(TAG, "Failed to create parent directory: " + parent);
                return false;
            }

            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(jsonString);
                Log.d(TAG, "Successfully generated cloner.json at: " + outputFile.getAbsolutePath());
                return true;
            }
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Error generating cloner.json", e);
            return false;
        }
    }

    /**
     * Generates a cloner.json file in the app's cache directory.
     * @param context The application context
     * @return The generated file, or null if generation failed
     */
    public static File generateClonerJsonInCache(Context context) {
        if (context == null) {
            Log.e(TAG, "Context cannot be null");
            return null;
        }

        File cacheFile = new File(context.getCacheDir(), "cloner.json");
        if (generateClonerJson(context, cacheFile)) {
            return cacheFile;
        }
        return null;
    }

    /**
     * Generates a random 16-character hexadecimal Android ID.
     * @return Random Android ID string
     */
    public static String generateRandomAndroidId() {
        return String.format(Locale.US, "%016X", random.nextLong());
    }

    /**
     * Generates a random locally-administered MAC address.
     * @return Random MAC address string in format XX:XX:XX:XX:XX:XX
     */
    public static String generateRandomMac() {
        byte[] macAddress = new byte[6];
        random.nextBytes(macAddress);
        // Set the locally administered bit and unicast bit
        macAddress[0] = (byte) ((macAddress[0] & (byte) 0xFC) | (byte) 0x02);
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < macAddress.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", macAddress[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Generates a random latitude value between -90 and 90 degrees.
     * @return Random latitude string
     */
    public static String generateRandomLatitude() {
        double lat = (random.nextDouble() * 180.0) - 90.0;
        return String.format(Locale.US, "%.6f", lat);
    }

    /**
     * Generates a random longitude value between -180 and 180 degrees.
     * @return Random longitude string
     */
    public static String generateRandomLongitude() {
        double lon = (random.nextDouble() * 360.0) - 180.0;
        return String.format(Locale.US, "%.6f", lon);
    }
    
    /**
     * Generates a random IMEI number (15 digits).
     * @return Random IMEI string
     */
    public static String generateRandomImei() {
        // IMEI is 15 digits: TAC (8 digits) + Serial (6 digits) + Luhn checksum (1 digit)
        StringBuilder sb = new StringBuilder();
        // TAC (Type Allocation Code) - use realistic prefixes
        String[] tacPrefixes = {"35", "86", "01", "49"};
        sb.append(tacPrefixes[random.nextInt(tacPrefixes.length)]);
        for (int i = 0; i < 12; i++) {
            sb.append(random.nextInt(10));
        }
        // Add Luhn checksum digit
        sb.append(calculateLuhnChecksum(sb.toString()));
        return sb.toString();
    }
    
    /**
     * Generates a random IMSI number (15 digits).
     * @return Random IMSI string
     */
    public static String generateRandomImsi() {
        // IMSI is 15 digits: MCC (3) + MNC (2-3) + MSIN (9-10)
        StringBuilder sb = new StringBuilder();
        // MCC (Mobile Country Code) - use common codes
        String[] mccs = {"310", "311", "234", "262", "460"}; // US, UK, Germany, China
        sb.append(mccs[random.nextInt(mccs.length)]);
        // MNC (Mobile Network Code) - 2-3 digits
        sb.append(String.format("%02d", random.nextInt(100)));
        // MSIN - remaining digits
        for (int i = sb.length(); i < 15; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
    
    /**
     * Generates a random serial number.
     * @return Random serial number string (alphanumeric, 10-16 chars)
     */
    public static String generateRandomSerialNumber() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int length = 10 + random.nextInt(7); // 10-16 characters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * Calculate Luhn checksum digit for IMEI validation.
     */
    private static int calculateLuhnChecksum(String digits) {
        int sum = 0;
        boolean alternate = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(digits.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }
    
    /**
     * Generate all random privacy data at once.
     * @return JSONObject containing all randomized identity values
     */
    public static JSONObject generateAllRandomPrivacyData() {
        JSONObject data = new JSONObject();
        try {
            data.put("android_id", generateRandomAndroidId());
            data.put("wifi_mac", generateRandomMac());
            data.put("bluetooth_mac", generateRandomMac());
            data.put("serial_number", generateRandomSerialNumber());
            data.put("imei", generateRandomImei());
            data.put("imsi", generateRandomImsi());
        } catch (JSONException e) {
            Log.e(TAG, "Error generating random privacy data", e);
        }
        return data;
    }

    /**
     * Category display names for settings UI.
     */
    public static class CategoryNames {
        public static final String CLONING = "Cloning Options";
        public static final String IDENTITY = "Device Identity";
        public static final String FAKE_CAMERA = "Fake Camera";
        public static final String LOCATION = "Location Spoofing";
        public static final String NETWORK = "Network & Proxy";
        public static final String DISPLAY = "Display & Window";
        public static final String MEDIA = "Background Media";
        public static final String DATA = "Data Management";
        public static final String WEBVIEW = "WebView Filter";
        public static final String OTHER = "Other Settings";
        public static final String BUILD_PROPS = "Device Profile";
    }
    
    /**
     * Available device presets for Build.props spoofing.
     * Returns array of [preset_key, display_name] pairs.
     */
    public static String[][] getDevicePresets() {
        return new String[][] {
            {"", "-- None (Keep Original) --"},
            {"samsung_s24_ultra", "Samsung Galaxy S24 Ultra"},
            {"pixel_8_pro", "Google Pixel 8 Pro"},
            {"oneplus_12", "OnePlus 12"},
            {"xiaomi_14_pro", "Xiaomi 14 Pro"},
            {"huawei_mate60_pro", "Huawei Mate 60 Pro"},
            {"sony_xperia_1v", "Sony Xperia 1 V"},
            {"oppo_find_x7_ultra", "OPPO Find X7 Ultra"},
            {"vivo_x100_pro", "Vivo X100 Pro"}
        };
    }

    private static final java.util.Map<String, DeviceProfile> DEVICE_PROFILES = new java.util.HashMap<>();
    static {
        // Samsung Galaxy S24 Ultra
        DEVICE_PROFILES.put("samsung_s24_ultra", new DeviceProfile(
            "Samsung", "SM-S928B", "Galaxy S24 Ultra", "samsung",
            "s24ultra", "samsung/s24ultrxx/s24ultra:14/UP1A.231005.007/S928BXXU1AWLM:user/release-keys",
            34, "14", "UP1A.231005.007", "pineapple", "qcom"
            34, "14", "UP1A.231005.007"
        ));

        // Google Pixel 8 Pro
        DEVICE_PROFILES.put("pixel_8_pro", new DeviceProfile(
            "Google", "Pixel 8 Pro", "Pixel 8 Pro", "google",
            "husky", "google/husky/husky:14/UD1A.231105.004/11010374:user/release-keys",
            34, "14", "UD1A.231105.004", "husky", "husky"
            34, "14", "UD1A.231105.004"
        ));

        // OnePlus 12
        DEVICE_PROFILES.put("oneplus_12", new DeviceProfile(
            "OnePlus", "CPH2573", "OnePlus 12", "oneplus",
            "aston", "OnePlus/CPH2573/OP5913L1:14/UKQ1.230924.001/T.18d1b7f_17e7_19:user/release-keys",
            34, "14", "UKQ1.230924.001", "pineapple", "qcom"
            34, "14", "UKQ1.230924.001"
        ));

        // Xiaomi 14 Pro
        DEVICE_PROFILES.put("xiaomi_14_pro", new DeviceProfile(
            "Xiaomi", "23116PN5BC", "Xiaomi 14 Pro", "xiaomi",
            "shennong", "Xiaomi/shennong/shennong:14/UKQ1.231003.002/V816.0.5.0.UNACNXM:user/release-keys",
            34, "14", "UKQ1.231003.002"
        ));

        // Huawei Mate 60 Pro
        DEVICE_PROFILES.put("huawei_mate60_pro", new DeviceProfile(
            "HUAWEI", "ALN-AL00", "HUAWEI Mate 60 Pro", "huawei",
            "ALN", "HUAWEI/ALN-AL00/HWALN:12/HUAWEIALN-AL00/105.0.0.73C00:user/release-keys",
            31, "12", "HUAWEIALN-AL00", "ALN", "kirin9000s"
            31, "12", "HUAWEIALN-AL00"
        ));

        // Sony Xperia 1 V
        DEVICE_PROFILES.put("sony_xperia_1v", new DeviceProfile(
            "Sony", "XQ-DQ72", "Xperia 1 V", "sony",
            "pdx234", "Sony/XQ-DQ72/XQ-DQ72:14/67.2.A.2.118/067002A002011800301508470:user/release-keys",
            34, "14", "67.2.A.2.118", "kalama", "qcom"
            34, "14", "67.2.A.2.118"
        ));

        // OPPO Find X7 Ultra
        DEVICE_PROFILES.put("oppo_find_x7_ultra", new DeviceProfile(
            "OPPO", "PHZ110", "OPPO Find X7 Ultra", "oppo",
            "PHZ110", "OPPO/PHZ110/OP5D3BL1:14/UP1A.231005.007/S.17f2e97_1e89_8:user/release-keys",
            34, "14", "UP1A.231005.007", "pineapple", "qcom"
            34, "14", "UP1A.231005.007"
        ));

        // Vivo X100 Pro
        DEVICE_PROFILES.put("vivo_x100_pro", new DeviceProfile(
            "vivo", "V2324A", "vivo X100 Pro", "vivo",
            "PD2324", "vivo/PD2324/PD2324:14/UP1A.231005.007/compiler11211512:user/release-keys",
            34, "14", "UP1A.231005.007", "k6989v1_64", "mt6989"
            34, "14", "UP1A.231005.007"
        ));
    }

    public static class DeviceProfile {
        public final String manufacturer;
        public final String model;
        public final String product;
        public final String brand;
        public final String device;
        public final String fingerprint;
        public final int sdkInt;
        public final String release;
        public final String displayId;
        public final String board;
        public final String hardware;

        public DeviceProfile(String manufacturer, String model, String product, String brand,
                      String device, String fingerprint, int sdkInt, String release, String displayId,
                      String board, String hardware) {

        public DeviceProfile(String manufacturer, String model, String product, String brand,
                      String device, String fingerprint, int sdkInt, String release, String displayId) {
            this.manufacturer = manufacturer;
            this.model = model;
            this.product = product;
            this.brand = brand;
            this.device = device;
            this.fingerprint = fingerprint;
            this.sdkInt = sdkInt;
            this.release = release;
            this.displayId = displayId;
            this.board = board;
            this.hardware = hardware;
        }
    }

    public static DeviceProfile getDeviceProfile(String key) {
        return DEVICE_PROFILES.get(key);
    }
    
    /**
     * Get human-readable display name for a setting key.
     * Converts internal keys like "FakeCameraAlternativeMode" to "Alternative Mode".
     */
    public static String getDisplayNameForKey(String key) {
        if (key == null) return "Unknown";
        
        // Define mappings for common settings
        switch (key) {
            // Fake Camera settings
            case "FakeCamera": return "Fake Camera";
            case "FakeCameraAlternativeMode": return "Alternative Mode";
            case "FakeCameraAppSupport": return "App Support";
            case "FakeCameraCloseStreamWorkaround": return "Close Stream Workaround";
            case "FakeCameraFixOrientation": return "Fix Orientation";
            case "FakeCameraOpenStreamWorkaround": return "Open Stream Workaround";
            case "FakeCameraRotation": return "Rotation";
            case "FakeCameraUseOriginalImageFile": return "Use Original Image File";
            case "FakeCameraImagePath": return "Image Path";
            case "FlipHorizontally": return "Flip Horizontally";
            case "RandomizeImage": return "Randomize Picture";
            case "RandomizeStrength": return "Randomize Strength";
            case "ResizeImage": return "Resize Picture";
            case "AddExifAttributes": return "Add EXIF Attributes";
            case "AddSpoofedLocation": return "Add Spoofed Location to EXIF";
            case "ForcedBackCamera": return "Forced Back Camera";
            
            // Location settings
            case "SpoofLocation": return "Spoof Location";
            case "SpoofLocationLatitude": return "Latitude";
            case "SpoofLocationLongitude": return "Longitude";
            case "SpoofLocationAltitude": return "Altitude";
            case "SpoofLocationAccuracy": return "Accuracy";
            case "SpoofLocationRandomize": return "Randomize Position";
            case "SpoofLocationUseIp": return "Use IP-based Location";
            
            // Device Identity settings
            case "android_id": return "Android ID";
            case "wifi_mac": return "Wi-Fi MAC";
            case "bluetooth_mac": return "Bluetooth MAC";
            case "serial_number": return "Serial Number";
            case "imei": return "IMEI";
            case "imsi": return "IMSI";
            case "user_agent": return "User Agent";
            
            // Build props settings
            case "build_props_enabled": return "Enable Device Spoofing";
            case "build_props_device_preset": return "Device Preset";
            case "build_props_randomize_fingerprint": return "Randomize Fingerprint";
            case "build_MANUFACTURER": return "Manufacturer";
            case "build_MODEL": return "Model";
            case "build_BRAND": return "Brand";
            case "build_DEVICE": return "Device";
            case "build_PRODUCT": return "Product";
            case "build_FINGERPRINT": return "Fingerprint";
            
            // Display settings
            case "AllowScreenshots": return "Allow Screenshots";
            case "floating_app": return "Floating Window Mode";
            
            // Background Media
            case "background_media": return "Background Media";
            case "background_media_webview": return "WebView Audio";
            case "background_media_mediaplayer": return "MediaPlayer Audio";
            case "background_media_exoplayer": return "ExoPlayer Audio";
            case "background_media_audio_focus": return "Audio Focus";
            
            // Data Access
            case "accessible_data_dir_internal": return "Internal Data Access";
            case "accessible_data_dir_external": return "External Data Access";
            case "accessible_data_dir_mode": return "Access Mode";
            case "accessible_data_dir_advanced_mode": return "Advanced Mode";
            case "bundle_app_data": return "Bundle App Data";
            
            // Dialog Blocker
            case "dialog_blocker_enabled": return "Enable Dialog Blocker";
            case "block_update_dialogs": return "Block Update Dialogs";
            case "block_rating_dialogs": return "Block Rating Dialogs";
            case "block_ad_dialogs": return "Block Ad Dialogs";
            case "block_subscription_dialogs": return "Block Subscription Dialogs";
            
            // Fake Calculator
            case "fake_calculator_enabled": return "Enable Fake Calculator";
            case "fake_calculator_passcode": return "Passcode";
            case "fake_calculator_ask_once": return "Ask Once Per Session";
            
            // SOCKS Proxy
            case "socks_proxy": return "Enable SOCKS Proxy";
            case "socks_proxy_host": return "Proxy Host";
            case "socks_proxy_port": return "Proxy Port";
            case "socks_proxy_user": return "Proxy Username";
            case "socks_proxy_pass": return "Proxy Password";
            
            default:
                // Convert camelCase or snake_case to Title Case
                return formatKeyAsDisplayName(key);
        }
    }
    
    /**
     * Format a raw key as a display name by converting camelCase/snake_case to Title Case.
     */
    private static String formatKeyAsDisplayName(String key) {
        if (key == null || key.isEmpty()) return "Unknown";
        
        // Replace underscores with spaces
        String result = key.replace("_", " ");
        
        // Insert spaces before capital letters (camelCase)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(result.charAt(i - 1))) {
                sb.append(' ');
            }
            sb.append(c);
        }
        result = sb.toString();
        
        // Capitalize first letter of each word
        String[] words = result.split(" ");
        sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            String word = words[i].toLowerCase();
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1));
                }
            }
        }
        
        return sb.toString();
    }

    /**
     * Category descriptions for more descriptive subtitles.
     */
    public static class CategoryDescriptions {
        public static final String CLONING = "Configure package name and cloning behavior";
        public static final String IDENTITY = "Spoof device identifiers like Android ID and MAC";
        public static final String FAKE_CAMERA = "Replace camera feed with custom images";
        public static final String LOCATION = "Set fake GPS coordinates and accuracy";
        public static final String NETWORK = "Proxy settings and DNS configuration";
        public static final String DISPLAY = "Floating window and screenshot controls";
        public static final String MEDIA = "Background audio and video playback";
        public static final String DATA = "App data directory access settings";
        public static final String WEBVIEW = "Filter and modify WebView requests";
        public static final String OTHER = "Additional configuration options";
    }

    /**
     * Gets a descriptive subtitle for a category based on its configured values.
     * @param categoryKey The category key
     * @param json The current settings JSON
     * @return A descriptive subtitle string
     */
    public static String getCategorySubtitle(String categoryKey, JSONObject json) {
        if (json == null || categoryKey == null) {
            return "Tap to configure";
        }

        try {
            switch (categoryKey) {
                case "cat_cloning":
                    String mode = json.optString("cloning_mode", DEFAULT_CLONING_MODE);
                    if (CLONING_MODE_GENERATE.equals(mode)) {
                        return "Generate new package variant";
                    } else if (CLONING_MODE_CUSTOM.equals(mode)) {
                        String customPkg = json.optString("custom_package_name", "");
                        return customPkg.isEmpty() ? "Custom package name (not set)" : "Custom: " + customPkg;
                    }
                    return "Keep original package name";

                case "cat_identity":
                    String androidId = json.optString("android_id", "");
                    String wifiMac = json.optString("wifi_mac", "");
                    String btMac = json.optString("bluetooth_mac", "");
                    String serial = json.optString("serial_number", "");
                    String imei = json.optString("imei", "");
                    String imsi = json.optString("imsi", "");
                    int configured = 0;
                    if (!androidId.isEmpty() && !androidId.equals(DEFAULT_ANDROID_ID)) configured++;
                    if (!wifiMac.isEmpty() && !wifiMac.equals(DEFAULT_WIFI_MAC)) configured++;
                    if (!btMac.isEmpty()) configured++;
                    if (!serial.isEmpty()) configured++;
                    if (!imei.isEmpty()) configured++;
                    if (!imsi.isEmpty()) configured++;
                    return configured == 0 ? "Using default identifiers" : configured + " identifier(s) customized";

                case "cat_fake_camera":
                    boolean cameraEnabled = json.optBoolean("FakeCamera", DEFAULT_FAKE_CAMERA_ENABLED);
                    return cameraEnabled ? "Camera spoofing enabled" : "Camera spoofing disabled";

                case "cat_location":
                    boolean locationEnabled = json.optBoolean("SpoofLocation", DEFAULT_SPOOF_LOCATION);
                    if (locationEnabled) {
                        double lat = json.optDouble("SpoofLocationLatitude", DEFAULT_SPOOF_LOCATION_LATITUDE);
                        double lon = json.optDouble("SpoofLocationLongitude", DEFAULT_SPOOF_LOCATION_LONGITUDE);
                        return String.format(Locale.US, "Spoofing to %.4f, %.4f", lat, lon);
                    }
                    return "Location spoofing disabled";

                case "cat_network":
                    boolean proxyEnabled = json.optBoolean("socks_proxy", DEFAULT_SOCKS_PROXY_ENABLED);
                    if (proxyEnabled) {
                        String host = json.optString("socks_proxy_host", "");
                        return host.isEmpty() ? "Proxy enabled (no host)" : "Proxy: " + host;
                    }
                    return "Direct connection (no proxy)";

                case "cat_display":
                    boolean floatingEnabled = json.optBoolean("floating_app", DEFAULT_FLOATING_APP_ENABLED);
                    boolean screenshots = json.optBoolean("AllowScreenshots", DEFAULT_ALLOW_SCREENSHOTS);
                    if (floatingEnabled) {
                        return "Floating window mode available";
                    } else if (!screenshots) {
                        return "Screenshots blocked";
                    }
                    return "Default display settings";

                case "cat_media":
                    boolean mediaEnabled = json.optBoolean("background_media", DEFAULT_BACKGROUND_MEDIA);
                    return mediaEnabled ? "Background playback enabled" : "Background playback disabled";

                case "cat_data":
                    boolean internalEnabled = json.optBoolean("accessible_data_dir_internal", false);
                    boolean externalEnabled = json.optBoolean("accessible_data_dir_external", false);
                    if (internalEnabled && externalEnabled) {
                        return "Internal and external data accessible";
                    } else if (internalEnabled) {
                        return "Internal data accessible";
                    } else if (externalEnabled) {
                        return "External data accessible";
                    }
                    return "Data directory access restricted";

                case "cat_webview":
                    JSONObject webview = json.optJSONObject("webview_filter");
                    if (webview != null && webview.optBoolean("enabled", false)) {
                        JSONArray rules = webview.optJSONArray("rules");
                        int ruleCount = rules != null ? rules.length() : 0;
                        return ruleCount + " filter rule(s) active";
                    }
                    return "WebView filtering disabled";

                case "cat_fake_calculator":
                    boolean calcEnabled = json.optBoolean("fake_calculator_enabled", false);
                    if (calcEnabled) {
                        String passcode = json.optString("fake_calculator_passcode", "1234");
                        boolean askOnce = json.optBoolean("fake_calculator_ask_once", false);
                         mode = askOnce ? "Ask once" : "Ask every time";
                        return "Enabled (" + passcode + "), " + mode;
                    }
                    return "Calculator lock disabled";

                case "cat_build_props":
                    boolean buildPropsEnabled = json.optBoolean("build_props_enabled", false);
                    if (buildPropsEnabled) {
                        String preset = json.optString("build_props_device_preset", "");
                        if (!preset.isEmpty()) {
                            return "Device preset: " + preset;
                        }
                        String model = json.optString("build_MODEL", "");
                        if (!model.isEmpty()) {
                            return "Custom device: " + model;
                        }
                        return "Build props spoofing enabled";
                    }
                    return "Build props spoofing disabled";

                case "cat_dialog_blocker":
                    boolean dialogBlockerEnabled = json.optBoolean("dialog_blocker_enabled", false);
                    if (dialogBlockerEnabled) {
                        int blockedTypes = 0;
                        if (json.optBoolean("block_update_dialogs", true)) blockedTypes++;
                        if (json.optBoolean("block_rating_dialogs", true)) blockedTypes++;
                        if (json.optBoolean("block_ad_dialogs", true)) blockedTypes++;
                        if (json.optBoolean("block_subscription_dialogs", false)) blockedTypes++;
                        return "Blocking " + blockedTypes + " dialog type(s)";
                    }
                    return "Dialog blocker disabled";

                default:
                    return "Tap to configure";
            }
        } catch (Exception e) {
            Log.w(TAG, "Error generating category subtitle", e);
            return "Tap to configure";
        }
    }
}
