package com.applisto.appcloner;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicReference;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * UserAgentWorkaround - Hooks HTTP connections to modify User-Agent headers
 * and URL scheme handling for cloned apps.
 * 
 * This class provides workarounds for:
 * 1. User-Agent header modifications to match original app
 * 2. Package name replacement in URLs/headers
 * 3. Version code/name replacement
 * 4. URI scheme workarounds for app links
 */
public class UserAgentWorkaround {
    private static final String TAG = "UserAgentWorkaround";
    
    /**
     * ThreadLocal to temporarily disable onUriString processing
     * (used to prevent recursive calls or during certain operations)
     */
    public static final ThreadLocal<Boolean> sOnUriStringDisabled = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    
    private static Context sContext;
    private static boolean sUriSchemeWorkaround;
    
    /**
     * Install the User-Agent workaround hooks
     * 
     * @param context The application context
     * @param uriSchemeWorkaround Whether to enable URI scheme workarounds
     */
    public static void install(Context context, boolean uriSchemeWorkaround) {
        Log.i(TAG, "install; uriSchemeWorkaround: " + uriSchemeWorkaround);
        
        sContext = context.getApplicationContext();
        sUriSchemeWorkaround = uriSchemeWorkaround;
        
        // Install Header Hook (for User-Agent modifications)
        new HeaderHook(context).install();
        
        // Install URL Hook (for URL modifications)
        new UrlHook(context).install();
        
        // Install URI Scheme Hook if enabled
        if (uriSchemeWorkaround) {
            new UriSchemeHook().install();
        }
    }
    
    /**
     * Replace values in a string to match the original app:
     * - Replace cloned package name with original
     * - Replace version code with meta version code
     * - Replace version name with meta version name
     * 
     * @param context The context
     * @param value The string value to process
     * @return The processed string with replacements
     */
    public static String replaceValue(Context context, String value) {
        if (TextUtils.isEmpty(value)) {
            return value;
        }
        
        // Skip if already an App Cloner identifier
        if (value.startsWith("App Cloner Clone/")) {
            return value;
        }
        
        // Replace package name with original package name
        value = Utils.replacePackageNameByOriginalPackageName(value);
        
        // Replace version code if different
        int versionCode = Utils.getMyVersionCode(context);
        int metaVersionCode = Utils.getMyMetaVersionCode(context);
        
        if (versionCode != metaVersionCode) {
            String metaVersionCodeStr = String.valueOf(metaVersionCode);
            String versionCodeStr = String.valueOf(versionCode);
            value = value.replace(metaVersionCodeStr, versionCodeStr);
        }
        
        // Replace version name if different
        String versionName = Utils.getMyVersionName(context);
        String metaVersionName = Utils.getMyMetaVersionName(context);
        
        if (versionName != null && metaVersionName != null && 
            !TextUtils.equals(versionName, metaVersionName)) {
            value = value.replace(metaVersionName, versionName);
        }
        
        return value;
    }
    
    /**
     * Base class for URL/Header hooks
     */
    public static abstract class BaseHook {
        protected Context context;
        
        public BaseHook(Context context) {
            this.context = context != null ? context.getApplicationContext() : null;
        }
        
        public BaseHook() {
            this.context = sContext;
        }
        
        public abstract void install();
    }
    
    /**
     * Header Hook - Intercepts HTTP header modifications
     * Modifies User-Agent and other headers to match original app
     */
    public static class HeaderHook extends BaseHook {
        
        public HeaderHook(Context context) {
            super(context);
        }
        
        @Override
        public void install() {
            try {
                // Hook URLConnection.setRequestProperty
                Method setRequestProperty = URLConnection.class.getDeclaredMethod(
                        "setRequestProperty", String.class, String.class);
                
                Pine.hook(setRequestProperty, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        String key = (String) frame.args[0];
                        String value = (String) frame.args[1];
                        
                        if (key != null && value != null) {
                            // Process the header value
                            AtomicReference<String> valueRef = new AtomicReference<>(value);
                            onHeader(key, valueRef);
                            frame.args[1] = valueRef.get();
                        }
                    }
                });
                
                // Hook URLConnection.addRequestProperty
                Method addRequestProperty = URLConnection.class.getDeclaredMethod(
                        "addRequestProperty", String.class, String.class);
                
                Pine.hook(addRequestProperty, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        String key = (String) frame.args[0];
                        String value = (String) frame.args[1];
                        
                        if (key != null && value != null) {
                            AtomicReference<String> valueRef = new AtomicReference<>(value);
                            onHeader(key, valueRef);
                            frame.args[1] = valueRef.get();
                        }
                    }
                });
                
                Log.d(TAG, "HeaderHook installed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to install HeaderHook", e);
            }
        }
        
        protected void onHeader(String key, AtomicReference<String> value) {
            if (context == null || value.get() == null) return;
            
            // Process User-Agent and other relevant headers
            if ("User-Agent".equalsIgnoreCase(key) ||
                "X-Requested-With".equalsIgnoreCase(key) ||
                "X-App-Version".equalsIgnoreCase(key)) {
                
                String original = value.get();
                String replaced = replaceValue(context, original);
                
                if (!TextUtils.equals(original, replaced)) {
                    Log.d(TAG, "Header modified - " + key + ": " + original + " -> " + replaced);
                    value.set(replaced);
                }
            }
        }
    }
    
    /**
     * URL Hook - Intercepts URL connections and modifications
     */
    public static class UrlHook extends BaseHook {
        
        public UrlHook(Context context) {
            super(context);
        }
        
        @Override
        public void install() {
            try {
                // Hook URL.openConnection
                Method openConnection = URL.class.getDeclaredMethod("openConnection");
                
                Pine.hook(openConnection, new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame frame) throws Throwable {
                        URLConnection connection = (URLConnection) frame.getResult();
                        URL url = (URL) frame.thisObject;
                        
                        if (connection != null && url != null) {
                            AtomicReference<String> urlRef = new AtomicReference<>(url.toString());
                            onUriString(urlRef);
                            // Note: Can't change URL after connection is opened,
                            // but we can log and track
                        }
                    }
                });
                
                // Hook HttpURLConnection specific methods if needed
                hookHttpURLConnectionMethods();
                
                // Hook OkHttp if present
                hookOkHttpMethods();
                
                Log.d(TAG, "UrlHook installed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to install UrlHook", e);
            }
        }
        
        private void hookHttpURLConnectionMethods() {
            try {
                // Hook getResponseCode to track HTTP responses
                Method getResponseCode = HttpURLConnection.class.getDeclaredMethod("getResponseCode");
                
                Pine.hook(getResponseCode, new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame frame) throws Throwable {
                        HttpURLConnection conn = (HttpURLConnection) frame.thisObject;
                        Integer responseCode = (Integer) frame.getResult();
                        
                        // Track response codes for debugging
                        if (responseCode != null && responseCode >= 400) {
                            URL url = conn.getURL();
                            Log.d(TAG, "HTTP Error " + responseCode + " for: " + 
                                       (url != null ? url.toString() : "unknown"));
                        }
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, "HttpURLConnection methods hook skipped: " + e.getMessage());
            }
        }
        
        private void hookOkHttpMethods() {
            try {
                // Try to hook OkHttp3 Request.Builder
                Class<?> requestBuilderClass = Class.forName("okhttp3.Request$Builder");
                
                // Hook url(String) method
                Method urlMethod = requestBuilderClass.getDeclaredMethod("url", String.class);
                
                Pine.hook(urlMethod, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        if (sOnUriStringDisabled.get()) return;
                        
                        String url = (String) frame.args[0];
                        if (url != null) {
                            AtomicReference<String> urlRef = new AtomicReference<>(url);
                            onUriString(urlRef);
                            
                            if (!TextUtils.equals(url, urlRef.get())) {
                                frame.args[0] = urlRef.get();
                            }
                        }
                    }
                });
                
                // Hook url(URL) method
                try {
                    Method urlUrlMethod = requestBuilderClass.getDeclaredMethod("url", URL.class);
                    
                    Pine.hook(urlUrlMethod, new MethodHook() {
                        @Override
                        public void beforeCall(Pine.CallFrame frame) throws Throwable {
                            if (sOnUriStringDisabled.get()) return;
                            
                            URL url = (URL) frame.args[0];
                            if (url != null) {
                                AtomicReference<String> urlRef = new AtomicReference<>(url.toString());
                                onUriString(urlRef);
                                
                                String newUrl = urlRef.get();
                                if (!TextUtils.equals(url.toString(), newUrl)) {
                                    frame.args[0] = new URL(newUrl);
                                }
                            }
                        }
                    });
                } catch (NoSuchMethodException e) {
                    // Method may not exist in all OkHttp versions
                }
                
                Log.d(TAG, "OkHttp hooks installed");
            } catch (ClassNotFoundException e) {
                Log.d(TAG, "OkHttp not found, skipping OkHttp hooks");
            } catch (Exception e) {
                Log.w(TAG, "Failed to hook OkHttp methods", e);
            }
        }
        
        protected void onUriString(AtomicReference<String> uriRef) {
            if (sOnUriStringDisabled.get()) return;
            if (context == null || uriRef.get() == null) return;
            
            String original = uriRef.get();
            String replaced = replaceValue(context, original);
            
            if (!TextUtils.equals(original, replaced)) {
                Log.d(TAG, "URI modified: " + original + " -> " + replaced);
                uriRef.set(replaced);
            }
        }
    }
    
    /**
     * URI Scheme Hook - Handles deep link and URI scheme workarounds
     * This helps cloned apps handle app-specific URI schemes
     */
    public static class UriSchemeHook extends BaseHook {
        
        public UriSchemeHook() {
            super();
        }
        
        @Override
        public void install() {
            try {
                // Hook android.net.Uri.parse to intercept URI parsing
                Class<?> uriClass = android.net.Uri.class;
                Method parseMethod = uriClass.getDeclaredMethod("parse", String.class);
                
                Pine.hook(parseMethod, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        if (sOnUriStringDisabled.get()) return;
                        
                        String uriString = (String) frame.args[0];
                        if (uriString != null && shouldProcessUri(uriString)) {
                            AtomicReference<String> uriRef = new AtomicReference<>(uriString);
                            onUriString(uriRef);
                            
                            String newUri = uriRef.get();
                            if (!TextUtils.equals(uriString, newUri)) {
                                frame.args[0] = newUri;
                                Log.d(TAG, "URI scheme modified: " + uriString + " -> " + newUri);
                            }
                        }
                    }
                });
                
                // Hook Intent URI handling
                hookIntentUriMethods();
                
                Log.d(TAG, "UriSchemeHook installed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Failed to install UriSchemeHook", e);
            }
        }
        
        private void hookIntentUriMethods() {
            try {
                // Hook Intent.parseUri
                Class<?> intentClass = android.content.Intent.class;
                Method parseUriMethod = intentClass.getDeclaredMethod("parseUri", String.class, int.class);
                
                Pine.hook(parseUriMethod, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        if (sOnUriStringDisabled.get()) return;
                        
                        String uri = (String) frame.args[0];
                        if (uri != null) {
                            AtomicReference<String> uriRef = new AtomicReference<>(uri);
                            onUriString(uriRef);
                            
                            if (!TextUtils.equals(uri, uriRef.get())) {
                                frame.args[0] = uriRef.get();
                            }
                        }
                    }
                });
            } catch (Exception e) {
                Log.d(TAG, "Intent URI method hook skipped: " + e.getMessage());
            }
        }
        
        private boolean shouldProcessUri(String uri) {
            if (uri == null) return false;
            
            // Process custom scheme URIs and certain HTTP URIs
            return !uri.startsWith("http://") && !uri.startsWith("https://") ||
                   uri.contains("package=") || uri.contains("app_id=");
        }
        
        protected void onUriString(AtomicReference<String> uriRef) {
            if (sOnUriStringDisabled.get()) return;
            if (sContext == null || uriRef.get() == null) return;
            
            String original = uriRef.get();
            String replaced = replaceValue(sContext, original);
            
            if (!TextUtils.equals(original, replaced)) {
                uriRef.set(replaced);
            }
        }
    }
    
    /**
     * Utility class for package/version operations
     */
    public static class Utils {
        private static String sOriginalPackageName;
        private static String sClonedPackageName;
        private static int sMyVersionCode = -1;
        private static int sMetaVersionCode = -1;
        private static String sMyVersionName;
        private static String sMetaVersionName;
        
        /**
         * Replace the cloned package name with the original package name in a string
         */
        public static String replacePackageNameByOriginalPackageName(String value) {
            if (value == null || sOriginalPackageName == null || sClonedPackageName == null) {
                return value;
            }
            
            if (sOriginalPackageName.equals(sClonedPackageName)) {
                return value;
            }
            
            return value.replace(sClonedPackageName, sOriginalPackageName);
        }
        
        /**
         * Get the current app's version code
         */
        public static int getMyVersionCode(Context context) {
            if (sMyVersionCode < 0) {
                try {
                    sMyVersionCode = context.getPackageManager()
                            .getPackageInfo(context.getPackageName(), 0).versionCode;
                } catch (Exception e) {
                    sMyVersionCode = 1;
                }
            }
            return sMyVersionCode;
        }
        
        /**
         * Get the meta (original) version code from cloner settings
         */
        public static int getMyMetaVersionCode(Context context) {
            if (sMetaVersionCode < 0) {
                try {
                    ClonerSettings settings = ClonerSettings.get(context);
                    sMetaVersionCode = settings.raw().optInt("original_version_code", 
                                                              getMyVersionCode(context));
                } catch (Exception e) {
                    sMetaVersionCode = getMyVersionCode(context);
                }
            }
            return sMetaVersionCode;
        }
        
        /**
         * Get the current app's version name
         */
        public static String getMyVersionName(Context context) {
            if (sMyVersionName == null) {
                try {
                    sMyVersionName = context.getPackageManager()
                            .getPackageInfo(context.getPackageName(), 0).versionName;
                } catch (Exception e) {
                    sMyVersionName = "1.0";
                }
            }
            return sMyVersionName;
        }
        
        /**
         * Get the meta (original) version name from cloner settings
         */
        public static String getMyMetaVersionName(Context context) {
            if (sMetaVersionName == null) {
                try {
                    ClonerSettings settings = ClonerSettings.get(context);
                    sMetaVersionName = settings.raw().optString("original_version_name", 
                                                                 getMyVersionName(context));
                } catch (Exception e) {
                    sMetaVersionName = getMyVersionName(context);
                }
            }
            return sMetaVersionName;
        }
        
        /**
         * Initialize package name mappings
         */
        public static void initPackageNames(Context context) {
            try {
                sClonedPackageName = context.getPackageName();
                ClonerSettings settings = ClonerSettings.get(context);
                sOriginalPackageName = settings.raw().optString("original_package_name", 
                                                                 sClonedPackageName);
                Log.d(TAG, "Package names initialized: cloned=" + sClonedPackageName + 
                           ", original=" + sOriginalPackageName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize package names", e);
            }
        }
    }
}
