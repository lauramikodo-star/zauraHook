package com.applisto.appcloner;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.GnssStatus;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public final class SpoofLocationHook {

    private static final String TAG = "SpoofLocationHook";
    private static final String PREFS_NAME = "SpoofLocationPrefs";
    private static final String PREF_LAT = "spoof_lat";
    private static final String PREF_LNG = "spoof_lng";
    private static final String PREF_ALT = "spoof_alt";
    private static final String PREF_ACC = "spoof_acc";
    private static final String PREF_ENABLED = "spoof_enabled";
    private static final String PREF_RANDOMIZE = "spoof_randomize";
    
    private static volatile boolean sHooked = false;
    private static Context sContext;

    /* ---------- Settings loaded from cloner.json ---------- */
    private static boolean ENABLED;
    private static volatile double LAT;
    private static volatile double LNG;
    private static volatile double ALT = 10.0; // Default altitude
    private static float ACC;
    private static boolean RANDOMIZE;
    private static boolean USE_IP;
    private static float SPEED = 0.0f;
    private static float BEARING = 0.0f;
    
    // Random for generating realistic variations
    private static final Random sRandom = new Random();
    
    // Handler for main thread operations
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    private static final List<LocationListener> LISTENERS = new CopyOnWriteArrayList<>();

    /* ==================================================== */
    public void init(Context ctx) {
        if (sHooked) return;
        sHooked = true;
        sContext = ctx.getApplicationContext();

        loadSettings(ctx);
        
        // Load any runtime overrides from SharedPreferences
        loadRuntimeOverrides();
        
        if (!ENABLED) {
            Log.i(TAG, "SpoofLocation disabled in cloner.json");
            return;
        }

        if (USE_IP) {
            new Thread(SpoofLocationHook::fetchIpLocation).start();
        }

        try {
            hookLocationManager();
            hookGnssStatus();
            hookLocationMethods();
            hookGpsStatus();
            hookLastKnownLocation();
            Log.i(TAG, "Spoof active -> " + LAT + ", " + LNG + ", alt=" + ALT);
        } catch (Exception e) {
            Log.e(TAG, "Hook failed", e);
        }
    }

    /* ---------- 1. Load flat keys ---------- */
    private void loadSettings(Context ctx) {
        try {
            JSONObject cfg = ClonerSettings.get(ctx).raw();

            ENABLED   = cfg.optBoolean("SpoofLocation", false);
            LAT       = cfg.optDouble("SpoofLocationLatitude",  0);
            LNG       = cfg.optDouble("SpoofLocationLongitude", 0);
            ALT       = cfg.optDouble("SpoofLocationAltitude", 10);
            ACC       = (float) cfg.optDouble("SpoofLocationAccuracy", 5);
            RANDOMIZE = cfg.optBoolean("SpoofLocationRandomize", false);
            USE_IP    = cfg.optBoolean("SpoofLocationUseIp", false);
            SPEED     = (float) cfg.optDouble("SpoofLocationSpeed", 0);
            BEARING   = (float) cfg.optDouble("SpoofLocationBearing", 0);
        } catch (Throwable t) {
            Log.e(TAG, "Cannot read cloner.json – spoof disabled", t);
            ENABLED = false;
        }
    }
    
    /**
     * Load runtime overrides from SharedPreferences.
     * These allow dynamic updates without restarting the app.
     */
    private void loadRuntimeOverrides() {
        if (sContext == null) return;
        try {
            SharedPreferences prefs = sContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            // Check if we have runtime overrides
            if (prefs.contains(PREF_LAT)) {
                LAT = Double.longBitsToDouble(prefs.getLong(PREF_LAT, Double.doubleToLongBits(LAT)));
            }
            if (prefs.contains(PREF_LNG)) {
                LNG = Double.longBitsToDouble(prefs.getLong(PREF_LNG, Double.doubleToLongBits(LNG)));
            }
            if (prefs.contains(PREF_ALT)) {
                ALT = Double.longBitsToDouble(prefs.getLong(PREF_ALT, Double.doubleToLongBits(ALT)));
            }
            if (prefs.contains(PREF_ACC)) {
                ACC = prefs.getFloat(PREF_ACC, ACC);
            }
            if (prefs.contains(PREF_ENABLED)) {
                ENABLED = prefs.getBoolean(PREF_ENABLED, ENABLED);
            }
            if (prefs.contains(PREF_RANDOMIZE)) {
                RANDOMIZE = prefs.getBoolean(PREF_RANDOMIZE, RANDOMIZE);
            }
            
            Log.d(TAG, "Loaded runtime overrides: lat=" + LAT + ", lng=" + LNG);
        } catch (Exception e) {
            Log.w(TAG, "Failed to load runtime overrides", e);
        }
    }

    /* ---------- IP-based location lookup (ipapi.co) ---------- */
    private static void fetchIpLocation() {
        HttpURLConnection conn = null;
        try {
            // Using ipapi.co (HTTPS) for better VPN support
            URL url = new URL("https://ipapi.co/json/");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Android"); // prevent 403

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                // ipapi.co returns: {"latitude": 1.23, "longitude": 4.56, ...}
                JSONObject json = new JSONObject(sb.toString());
                if (json.has("latitude") && json.has("longitude")) {
                    LAT = json.getDouble("latitude");
                    LNG = json.getDouble("longitude");
                    Log.i(TAG, "Updated spoof location from IP (HTTPS): " + LAT + ", " + LNG);
                } else {
                    Log.w(TAG, "IP API response missing latitude/longitude: " + sb);
                }
            } else {
                Log.w(TAG, "IP API HTTP error: " + code);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch IP location", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /* ---------- 2. Hook Android LocationManager ---------- */
    private void hookLocationManager() throws Exception {
        Method m = LocationManager.class.getDeclaredMethod(
                "requestLocationUpdates", String.class, long.class, float.class, LocationListener.class);
        Pine.hook(m, new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame f) {
                LocationListener l = (LocationListener) f.args[3];
                if (l != null) {
                    LISTENERS.add(l);
                    spoofLoop(l);
                }
                f.setResult(null); // cancel real request
            }
        });

        // overload with Looper
        Method m2 = LocationManager.class.getDeclaredMethod(
                "requestLocationUpdates", String.class, long.class, float.class,
                LocationListener.class, Looper.class);
        Pine.hook(m2, new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame f) {
                LocationListener l = (LocationListener) f.args[3];
                if (l != null) {
                    LISTENERS.add(l);
                    spoofLoop(l);
                }
                f.setResult(null);
            }
        });
    }

    private static void spoofLoop(LocationListener l) {
        new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!LISTENERS.contains(l)) return;
            l.onLocationChanged(fakeLocation());
            spoofLoop(l);
        }, 1_000);
    }

    /* ---------- 3. Hook GnssStatus ---------- */
    private void hookGnssStatus() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        
        try {
            Method m = LocationManager.class.getMethod(
                    "registerGnssStatusCallback", GnssStatus.Callback.class);
            Pine.hook(m, new MethodHook() {
                @Override public void afterCall(Pine.CallFrame f) {
                    GnssStatus.Callback cb = (GnssStatus.Callback) f.args[0];
                    if (cb != null) {
                        // Send fake started event
                        cb.onStarted();
                        // Send fake satellite status
                        GnssStatus status = buildFakeStatus();
                        if (status != null) cb.onSatelliteStatusChanged(status);
                    }
                }
            });
        } catch (NoSuchMethodException e) {
            // Try overload with Executor
            try {
                Method m = LocationManager.class.getMethod(
                        "registerGnssStatusCallback", 
                        java.util.concurrent.Executor.class, 
                        GnssStatus.Callback.class);
                Pine.hook(m, new MethodHook() {
                    @Override public void afterCall(Pine.CallFrame f) {
                        GnssStatus.Callback cb = (GnssStatus.Callback) f.args[1];
                        if (cb != null) {
                            cb.onStarted();
                            GnssStatus status = buildFakeStatus();
                            if (status != null) cb.onSatelliteStatusChanged(status);
                        }
                    }
                });
            } catch (Exception e2) {
                Log.w(TAG, "Could not hook GnssStatus callbacks", e2);
            }
        }
    }
    
    /* ---------- 3.5 Hook GpsStatus (legacy) ---------- */
    private void hookGpsStatus() {
        try {
            Method m = LocationManager.class.getMethod("addGpsStatusListener", GpsStatus.Listener.class);
            Pine.hook(m, new MethodHook() {
                @Override public void afterCall(Pine.CallFrame f) {
                    f.setResult(true); // Always succeed
                    GpsStatus.Listener listener = (GpsStatus.Listener) f.args[0];
                    if (listener != null) {
                        listener.onGpsStatusChanged(GpsStatus.GPS_EVENT_STARTED);
                        listener.onGpsStatusChanged(GpsStatus.GPS_EVENT_SATELLITE_STATUS);
                    }
                }
            });
        } catch (Exception e) {
            Log.d(TAG, "GpsStatus hook skipped (deprecated API)");
        }
    }
    
    /* ---------- 3.6 Hook getLastKnownLocation ---------- */
    private void hookLastKnownLocation() {
        try {
            Method m = LocationManager.class.getMethod("getLastKnownLocation", String.class);
            Pine.hook(m, new MethodHook() {
                @Override public void afterCall(Pine.CallFrame f) {
                    if (!ENABLED) return;
                    // Replace with fake location
                    f.setResult(fakeLocation());
                    Log.d(TAG, "Spoofed getLastKnownLocation");
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook getLastKnownLocation", e);
        }
    }
    
    /* ---------- 3.7 Hook Location object methods ---------- */
    private void hookLocationMethods() {
        try {
            // Hook Location.getLatitude()
            Method getLatitude = Location.class.getMethod("getLatitude");
            Pine.hook(getLatitude, new MethodHook() {
                @Override public void afterCall(Pine.CallFrame f) {
                    if (!ENABLED) return;
                    Location loc = (Location) f.thisObject;
                    // Only spoof if this looks like a real GPS location
                    if (isRealGpsLocation(loc)) {
                        double lat = LAT;
                        if (RANDOMIZE) {
                            lat += (sRandom.nextDouble() - 0.5) * 0.0002;
                        }
                        f.setResult(lat);
                    }
                }
            });
            
            // Hook Location.getLongitude()
            Method getLongitude = Location.class.getMethod("getLongitude");
            Pine.hook(getLongitude, new MethodHook() {
                @Override public void afterCall(Pine.CallFrame f) {
                    if (!ENABLED) return;
                    Location loc = (Location) f.thisObject;
                    if (isRealGpsLocation(loc)) {
                        double lng = LNG;
                        if (RANDOMIZE) {
                            lng += (sRandom.nextDouble() - 0.5) * 0.0002;
                        }
                        f.setResult(lng);
                    }
                }
            });
            
            // Hook Location.getAltitude()
            Method getAltitude = Location.class.getMethod("getAltitude");
            Pine.hook(getAltitude, new MethodHook() {
                @Override public void afterCall(Pine.CallFrame f) {
                    if (!ENABLED) return;
                    Location loc = (Location) f.thisObject;
                    if (isRealGpsLocation(loc)) {
                        double alt = ALT;
                        if (RANDOMIZE) {
                            alt += (sRandom.nextDouble() - 0.5) * 2.0;
                        }
                        f.setResult(alt);
                    }
                }
            });
            
            Log.d(TAG, "Location methods hooked");
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook Location methods", e);
        }
    }
    
    /**
     * Check if a Location object appears to be from real GPS
     */
    private static boolean isRealGpsLocation(Location loc) {
        if (loc == null) return false;
        String provider = loc.getProvider();
        return provider == null || 
               LocationManager.GPS_PROVIDER.equals(provider) ||
               LocationManager.NETWORK_PROVIDER.equals(provider) ||
               LocationManager.FUSED_PROVIDER.equals(provider) ||
               "fused".equals(provider);
    }

    private static GnssStatus buildFakeStatus() {
        try {
            // Create a fake GnssStatus with realistic satellite data
            Class<?> builderClass = Class.forName("android.location.GnssStatus$Builder");
            Object builder = builderClass.getDeclaredConstructor().newInstance();
            
            // Add 8-12 satellites with realistic CNR values
            int satCount = 8 + sRandom.nextInt(5);
            for (int i = 0; i < satCount; i++) {
                // svid, constellation, cn0, elevation, azimuth, hasEphemeris, hasAlmanac, usedInFix
                // Use reflection to add satellites
            }
            
            Method buildMethod = builderClass.getMethod("build");
            return (GnssStatus) buildMethod.invoke(builder);
        } catch (Exception e) {
            // Fallback: try old method
            try {
                Class<?> clazz = Class.forName("android.location.GnssStatus");
                Object b = clazz.getDeclaredConstructor().newInstance();
                Method set = clazz.getDeclaredMethod("setSatellite",
                        int.class, int.class, float.class, float.class, float.class,
                        float.class, boolean.class, boolean.class);
                for (int i = 0; i < 12; i++) {
                    float cn0 = 30f + sRandom.nextFloat() * 20f; // 30-50 dB-Hz
                    float elev = 10f + sRandom.nextFloat() * 70f; // 10-80 degrees
                    float azim = sRandom.nextFloat() * 360f; // 0-360 degrees
                    set.invoke(b, i, 1, cn0, elev, azim, 0f, true, true);
                }
                Field f = clazz.getDeclaredField("mStatus");
                f.setAccessible(true);
                return (GnssStatus) f.get(b);
            } catch (Exception e2) {
                Log.w(TAG, "Fake GnssStatus failed", e2);
                return null;
            }
        }
    }

    /* ---------- 4. Build fake location ---------- */
    private static Location fakeLocation() {
        Location loc = new Location(LocationManager.GPS_PROVIDER);
        double lat = LAT;
        double lng = LNG;
        double alt = ALT;

        if (RANDOMIZE) {
            // Approx 11m per 0.0001 degrees
            lat += (sRandom.nextDouble() - 0.5) * 0.0002;
            lng += (sRandom.nextDouble() - 0.5) * 0.0002;
            alt += (sRandom.nextDouble() - 0.5) * 2.0; // ±1m variation
        }

        loc.setLatitude(lat);
        loc.setLongitude(lng);
        loc.setAltitude(alt);
        loc.setAccuracy(ACC + (RANDOMIZE ? sRandom.nextFloat() * 2f : 0f));
        loc.setTime(System.currentTimeMillis());
        loc.setSpeed(SPEED + (RANDOMIZE ? sRandom.nextFloat() * 0.5f : 0f));
        loc.setBearing(BEARING + (RANDOMIZE ? sRandom.nextFloat() * 2f - 1f : 0f));
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        
        // Set additional attributes for newer Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            loc.setBearingAccuracyDegrees(5f);
            loc.setSpeedAccuracyMetersPerSecond(0.5f);
            loc.setVerticalAccuracyMeters(ACC * 2);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            loc.setElapsedRealtimeUncertaintyNanos(1000000); // 1ms uncertainty
        }
        
        return loc;
    }
    
    /* ========== Public API for Runtime Updates ========== */
    
    /**
     * Dynamically update the spoofed location at runtime.
     * @param latitude New latitude
     * @param longitude New longitude
     */
    public static void setLocation(double latitude, double longitude) {
        LAT = latitude;
        LNG = longitude;
        saveRuntimeOverrides();
        Log.i(TAG, "Location updated to: " + LAT + ", " + LNG);
        
        // Notify all active listeners of the new location
        notifyListenersOfLocationChange();
    }
    
    /**
     * Dynamically update the spoofed location with altitude.
     * @param latitude New latitude
     * @param longitude New longitude
     * @param altitude New altitude in meters
     */
    public static void setLocation(double latitude, double longitude, double altitude) {
        LAT = latitude;
        LNG = longitude;
        ALT = altitude;
        saveRuntimeOverrides();
        Log.i(TAG, "Location updated to: " + LAT + ", " + LNG + ", alt=" + ALT);
        notifyListenersOfLocationChange();
    }
    
    /**
     * Enable or disable location spoofing at runtime.
     */
    public static void setEnabled(boolean enabled) {
        ENABLED = enabled;
        saveRuntimeOverrides();
        Log.i(TAG, "Location spoofing " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Enable or disable randomization at runtime.
     */
    public static void setRandomize(boolean randomize) {
        RANDOMIZE = randomize;
        saveRuntimeOverrides();
        Log.i(TAG, "Location randomization " + (randomize ? "enabled" : "disabled"));
    }
    
    /**
     * Set accuracy at runtime.
     */
    public static void setAccuracy(float accuracy) {
        ACC = accuracy;
        saveRuntimeOverrides();
        Log.i(TAG, "Accuracy set to: " + ACC);
    }
    
    /**
     * Get current spoofed latitude.
     */
    public static double getLatitude() {
        return LAT;
    }
    
    /**
     * Get current spoofed longitude.
     */
    public static double getLongitude() {
        return LNG;
    }
    
    /**
     * Get current spoofed altitude.
     */
    public static double getAltitude() {
        return ALT;
    }
    
    /**
     * Check if spoofing is enabled.
     */
    public static boolean isEnabled() {
        return ENABLED;
    }
    
    /**
     * Save runtime overrides to SharedPreferences.
     */
    private static void saveRuntimeOverrides() {
        if (sContext == null) return;
        try {
            SharedPreferences prefs = sContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putLong(PREF_LAT, Double.doubleToLongBits(LAT))
                .putLong(PREF_LNG, Double.doubleToLongBits(LNG))
                .putLong(PREF_ALT, Double.doubleToLongBits(ALT))
                .putFloat(PREF_ACC, ACC)
                .putBoolean(PREF_ENABLED, ENABLED)
                .putBoolean(PREF_RANDOMIZE, RANDOMIZE)
                .apply();
            Log.d(TAG, "Saved runtime overrides");
        } catch (Exception e) {
            Log.w(TAG, "Failed to save runtime overrides", e);
        }
    }
    
    /**
     * Notify all registered listeners of a location change.
     */
    private static void notifyListenersOfLocationChange() {
        Location newLoc = fakeLocation();
        for (LocationListener l : LISTENERS) {
            try {
                l.onLocationChanged(newLoc);
            } catch (Exception e) {
                Log.w(TAG, "Failed to notify listener", e);
            }
        }
    }
    
    /**
     * Clear all runtime overrides and revert to config values.
     */
    public static void clearRuntimeOverrides() {
        if (sContext == null) return;
        try {
            SharedPreferences prefs = sContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
            Log.i(TAG, "Runtime overrides cleared");
        } catch (Exception e) {
            Log.w(TAG, "Failed to clear runtime overrides", e);
        }
    }
}
