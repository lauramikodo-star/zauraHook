package com.applisto.appcloner;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultProvider extends AbstractContentProvider {
    private static final String TAG = "DefaultProvider";

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "Context is null");
            return false;
        }

        Log.i(TAG, "Initializing hooks...");

        // Register DataExportReceiver
        try {
            IntentFilter filter = new IntentFilter(DataExportReceiver.ACTION_EXPORT_DATA);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(new DataExportReceiver(), filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(new DataExportReceiver(), filter);
            }
        } catch (Throwable t) {
             Log.e(TAG, "Failed to register DataExportReceiver", t);
        }

        // Handle bundled app data restore
        try {
             ClonerSettings settings = ClonerSettings.get(context);
             if (settings.bundleAppData()) {
                 Log.i(TAG, "bundle_app_data enabled, attempting to import bundled data...");
                 AppDataManager dataManager = new AppDataManager(context, context.getPackageName(), false);
                 dataManager.importBundledAppDataIfAvailable();
             }
        } catch (Throwable t) {
             Log.e(TAG, "Failed to handle bundled app data", t);
        }


        /* 1.  initialise the smart engine once */
        SmartHooking.init(context);   // <-- NEW
        
        new Socks5ProxyHook().init(context);
        
       // new ClonerSettings.get(context);

        /* 2.  all your existing hooks stay identical */
        new AndroidIdHook().init(context);
        new WifiMacHook().init(context);
        new BuildPropsHook().init(context);
        new WebViewUrlDataFilterHook().init(getContext());
        new UserAgentHook().init(context);
        // Add after other hooks in onCreate()
        new BackgroundMediaHook().init(context);
        
        FakeCameraHook hook = new FakeCameraHook();
        hook.init(context);

        // Initialize Fake Calculator Hook
        FakeCalculatorHook calculatorHook = new FakeCalculatorHook(context);
        calculatorHook.install(context);

        // Initialize Internal Browser Hook
        new InternalBrowserHook(context).init();

        new WebViewFilterHook().init(context);

        // Initialize location spoofing hook
        SpoofLocationHook locationHook = new SpoofLocationHook();
        locationHook.init(context);
        // Optional: Set custom location
        // SpoofLocationHook.setSpoofedLocation(40.7128, -74.0060); // New York
        // SpoofLocationHook.enableLocationSpoofing(true);

        // Device Identity Hooks
        new ImsiHook().init(context);
        new ImeiHook().init(context);  // NEW: IMEI spoofing
        new SerialHook().init(context);
        new BtMacHook().init(context);

        // Dialog Intercept and Blocker
        new DialogInterceptHook().init(context);

        new FloatingAppHook().init(context);
        AccessibleDataDirHook accessibleDirHook = new AccessibleDataDirHook();
        accessibleDirHook.init(context);

        ForcedBackCameraHook.install(context);
        ScreenshotDetectionBlocker.install(context);

        // New: UserAgent Workaround hooks for HTTP header/URL modifications
        try {
            ClonerSettings settings = ClonerSettings.get(context);
            boolean uriSchemeWorkaround = settings.raw().optBoolean("uri_scheme_workaround", false);
            UserAgentWorkaround.Utils.initPackageNames(context);
            UserAgentWorkaround.install(context, uriSchemeWorkaround);
            Log.i(TAG, "UserAgentWorkaround installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install UserAgentWorkaround", t);
        }

        // New: Firebase workaround warnings
        try {
            UserAgentWorkaroundWarning.install();
            UserAgentWorkaroundWarning.installAnalyticsWorkaround();
            UserAgentWorkaroundWarning.installCrashlyticsWorkaround();
            Log.i(TAG, "UserAgentWorkaroundWarning installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install UserAgentWorkaroundWarning", t);
        }

        

    // IPC permission for secure operations
    private static final String IPC_PERMISSION = "com.appcloner.replica.permission.REPLICA_IPC";

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        // Check if caller has IPC permission OR is the same app (for internal calls)
        if (!hasIpcPermissionOrSameApp()) {
            Log.w(TAG, "call() denied: caller lacks IPC permission");
            Bundle result = new Bundle();
            result.putBoolean("ok", false);
            result.putString("error", "Permission denied");
            return result;
        }

        try {
            if ("list_prefs".equals(method)) {
                return executeWithTimeout(() -> listPrefs(), 5000);
            } else if ("get_prefs".equals(method)) {
                final String prefFile = arg;
                return executeWithTimeout(() -> getPrefs(prefFile), 5000);
            } else if ("put_pref".equals(method)) {
                final Bundle putExtras = extras;
                return executeWithTimeout(() -> putPref(putExtras), 5000);
            } else if ("remove_pref".equals(method)) {
                final Bundle removeExtras = extras;
                return executeWithTimeout(() -> removePref(removeExtras), 5000);
            } else if ("request_export".equals(method)) {
                return requestExport(extras);
            }
        } catch (Exception e) {
            Log.e(TAG, "call() error for method: " + method, e);
            Bundle errorResult = new Bundle();
            errorResult.putBoolean("ok", false);
            errorResult.putString("error", e.getMessage());
            return errorResult;
        }
        return super.call(method, arg, extras);
    }
    
    /**
     * Execute a callable with timeout to prevent stuck operations
     */
    private interface BundleCallable {
        Bundle call() throws Exception;
    }
    
    private Bundle executeWithTimeout(BundleCallable callable, long timeoutMs) {
        final AtomicReference<Bundle> resultRef = new AtomicReference<>();
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        
        Thread worker = new Thread(() -> {
            try {
                resultRef.set(callable.call());
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });
        worker.start();
        
        try {
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                if (errorRef.get() != null) {
                    throw new RuntimeException("Operation failed", errorRef.get());
                }
                return resultRef.get();
            } else {
                // Timeout occurred
                worker.interrupt();
                Bundle timeoutResult = new Bundle();
                timeoutResult.putBoolean("ok", false);
                timeoutResult.putString("error", "Operation timed out after " + timeoutMs + "ms");
                return timeoutResult;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Bundle interruptResult = new Bundle();
            interruptResult.putBoolean("ok", false);
            interruptResult.putString("error", "Operation interrupted");
            return interruptResult;
        }
    }
    
    /**
     * Request data export via the provider (alternative to broadcast)
     */
    private Bundle requestExport(Bundle extras) {
        Bundle result = new Bundle();
        try {
            Context context = getContext();
            if (context == null) {
                result.putBoolean("ok", false);
                result.putString("error", "Context is null");
                return result;
            }
            
            String senderPackage = extras != null ? extras.getString("sender_package") : null;
            
            // Send broadcast to DataExportReceiver
            Intent exportIntent = new Intent(DataExportReceiver.ACTION_EXPORT_DATA);
            exportIntent.setPackage(context.getPackageName());
            if (senderPackage != null) {
                exportIntent.putExtra("sender_package", senderPackage);
            }
            context.sendBroadcast(exportIntent);
            
            result.putBoolean("ok", true);
            result.putString("status", "Export request sent");
            Log.i(TAG, "Export request sent via provider");
        } catch (Throwable t) {
            Log.e(TAG, "requestExport error", t);
            result.putBoolean("ok", false);
            result.putString("error", t.getMessage());
        }
        return result;
    }

    /**
     * Check if caller has IPC permission or is calling from within the same app.
     * This allows both the app cloner (with IPC permission) and internal hooks
     * (same UID) to access the provider.
     */
    private boolean hasIpcPermissionOrSameApp() {
        Context context = getContext();
        if (context == null) return false;

        // Allow same-app calls (internal hooks) - this is the most common case
        int callingUid = Binder.getCallingUid();
        int myUid = android.os.Process.myUid();
        if (callingUid == myUid) {
            Log.d(TAG, "Allowing same-app call (UID match)");
            return true;
        }
        
        // For Binder calls, if we're running in the same process, Binder.getCallingUid()
        // returns the actual calling UID, but for internal calls within the same app,
        // the UID should match. However, in some edge cases (e.g., when using Binder.clearCallingIdentity),
        // the UIDs might differ. Let's also check by package name.
        try {
            String[] callingPackages = context.getPackageManager().getPackagesForUid(callingUid);
            String myPackage = context.getPackageName();
            if (callingPackages != null) {
                for (String pkg : callingPackages) {
                    if (myPackage.equals(pkg)) {
                        Log.d(TAG, "Allowing same-package call: " + pkg);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking package for UID", e);
        }

        // Check if caller has IPC permission using both checkCallingPermission and checkCallingOrSelfPermission
        try {
            int permissionCheck = context.checkCallingPermission(IPC_PERMISSION);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "IPC permission granted via checkCallingPermission");
                return true;
            }
            // Fallback: Also check with checkCallingOrSelfPermission for edge cases
            permissionCheck = context.checkCallingOrSelfPermission(IPC_PERMISSION);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "IPC permission granted via checkCallingOrSelfPermission");
                return true;
            }
            // Log failure for debugging
            Log.w(TAG, "Permission check failed for IPC_PERMISSION. CallingUid=" + callingUid + ", MyUid=" + myUid);
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Error checking permission", e);
            return false;
        }
    }

    private Bundle listPrefs() {
        Bundle result = new Bundle();
        try {
            File prefsDir = new File(getContext().getApplicationInfo().dataDir, "shared_prefs");
            ArrayList<String> files = new ArrayList<>();
            if (prefsDir.exists() && prefsDir.isDirectory()) {
                File[] list = prefsDir.listFiles();
                if (list != null) {
                    for (File f : list) {
                        String name = f.getName();
                        if (name.endsWith(".xml")) {
                            files.add(name.substring(0, name.length() - 4));
                        }
                    }
                }
            }
            result.putStringArrayList("files", files);
        } catch (Throwable t) {
            Log.e(TAG, "listPrefs error", t);
        }
        return result;
    }

    private Bundle getPrefs(String file) {
        Bundle result = new Bundle();
        result.putBoolean("ok", true);
        
        if (file == null) {
            result.putBoolean("ok", false);
            result.putString("error", "File name is null");
            return result;
        }
        
        try {
            Context context = getContext();
            if (context == null) {
                result.putBoolean("ok", false);
                result.putString("error", "Context is null");
                return result;
            }
            
            SharedPreferences prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE);
            Map<String, ?> all = prefs.getAll();
            
            if (all == null || all.isEmpty()) {
                Log.d(TAG, "getPrefs: no preferences found in " + file);
                return result;
            }
            
            int count = 0;
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                
                if (key == null || val == null) continue;
                
                try {
                    if (val instanceof Boolean) {
                        result.putBoolean(key, (Boolean) val);
                    } else if (val instanceof Integer) {
                        result.putInt(key, (Integer) val);
                    } else if (val instanceof Long) {
                        result.putLong(key, (Long) val);
                    } else if (val instanceof Float) {
                        result.putFloat(key, (Float) val);
                    } else if (val instanceof String) {
                        result.putString(key, (String) val);
                    } else if (val instanceof Set) {
                        // Bundle doesn't support Set<String>, so we use ArrayList<String>
                        @SuppressWarnings("unchecked")
                        Set<String> stringSet = (Set<String>) val;
                        result.putStringArrayList(key, new ArrayList<>(stringSet));
                    } else {
                        // Handle unknown types by converting to string
                        result.putString(key, String.valueOf(val));
                    }
                    count++;
                } catch (Exception e) {
                    Log.w(TAG, "getPrefs: error processing key " + key, e);
                }
            }
            
            Log.d(TAG, "getPrefs: loaded " + count + " preferences from " + file);
        } catch (Throwable t) {
            Log.e(TAG, "getPrefs error", t);
            result.putBoolean("ok", false);
            result.putString("error", t.getMessage());
        }
        return result;
    }

    private Bundle putPref(Bundle extras) {
        Bundle result = new Bundle();
        if (extras == null) {
            result.putBoolean("ok", false);
            result.putString("error", "Extras is null");
            return result;
        }
        try {
            String file = extras.getString("file");
            String key = extras.getString("key");
            String type = extras.getString("type");
            if (file == null || key == null || type == null) {
                result.putBoolean("ok", false);
                result.putString("error", "Missing args: file=" + file + ", key=" + key + ", type=" + type);
                return result;
            }

            Log.d(TAG, "putPref: file=" + file + ", key=" + key + ", type=" + type);

            SharedPreferences prefs = getContext().getSharedPreferences(file, Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();

            switch (type) {
                case "Boolean":
                    edit.putBoolean(key, extras.getBoolean("value"));
                    break;
                case "Integer":
                    edit.putInt(key, extras.getInt("value"));
                    break;
                case "Long":
                    edit.putLong(key, extras.getLong("value"));
                    break;
                case "Float":
                    edit.putFloat(key, extras.getFloat("value"));
                    break;
                case "StringSet":
                    ArrayList<String> list = extras.getStringArrayList("value");
                    if (list != null) {
                        edit.putStringSet(key, new HashSet<>(list));
                    }
                    break;
                default: // String
                    edit.putString(key, extras.getString("value"));
            }
            // Use commit() instead of apply() for synchronous write and reliable result
            boolean committed = edit.commit();
            result.putBoolean("ok", committed);
            if (!committed) {
                result.putString("error", "Failed to commit SharedPreferences");
            }
            Log.d(TAG, "putPref committed=" + committed);
        } catch (Throwable t) {
            Log.e(TAG, "putPref error", t);
            result.putBoolean("ok", false);
            result.putString("error", t.getMessage());
        }
        return result;
    }

    private Bundle removePref(Bundle extras) {
        Bundle result = new Bundle();
        if (extras == null) {
            result.putBoolean("ok", false);
            result.putString("error", "Extras is null");
            return result;
        }
        try {
            String file = extras.getString("file");
            String key = extras.getString("key");
            if (file == null || key == null) {
                result.putBoolean("ok", false);
                result.putString("error", "Missing args: file=" + file + ", key=" + key);
                return result;
            }
            Log.d(TAG, "removePref: file=" + file + ", key=" + key);
            SharedPreferences prefs = getContext().getSharedPreferences(file, Context.MODE_PRIVATE);
            // Use commit() for synchronous write
            boolean committed = prefs.edit().remove(key).commit();
            result.putBoolean("ok", committed);
            if (!committed) {
                result.putString("error", "Failed to commit SharedPreferences");
            }
            Log.d(TAG, "removePref committed=" + committed);
        } catch (Throwable t) {
            Log.e(TAG, "removePref error", t);
            result.putBoolean("ok", false);
            result.putString("error", t.getMessage());
        }
        return result;
    }
}
