package com.applisto.appcloner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataExportReceiver extends BroadcastReceiver {
    private static final String TAG = "DataExportReceiver";
    public static final String ACTION_EXPORT_DATA = "com.applisto.appcloner.ACTION_EXPORT_DATA";
    private static final String IPC_PERMISSION = "com.appcloner.replica.permission.REPLICA_IPC";

    // Static lock to prevent concurrent exports
    private static final Object LOCK = new Object();
    private static final AtomicBoolean sIsExporting = new AtomicBoolean(false);
    
    // Timeout for export operations (60 seconds)
    private static final long EXPORT_TIMEOUT_MS = 60000;
    
    // Time when export started
    private static volatile long sExportStartTime = 0;
    
    // Handler for timeout checks
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_EXPORT_DATA.equals(intent.getAction())) {
            Log.i(TAG, "Received export data request.");

            // Permission check: allow if caller has IPC permission or is same app
            if (!hasPermissionOrSameApp(context)) {
                Log.w(TAG, "Export request denied: caller lacks permission");
                return;
            }

            // Check for stale export (stuck for too long)
            if (sIsExporting.get()) {
                long elapsed = System.currentTimeMillis() - sExportStartTime;
                if (elapsed > EXPORT_TIMEOUT_MS) {
                    Log.w(TAG, "Previous export timed out (" + elapsed + "ms), resetting state");
                    sIsExporting.set(false);
                } else {
                    Log.w(TAG, "Export already in progress (" + elapsed + "ms elapsed), skipping duplicate request.");
                    return;
                }
            }

            if (!sIsExporting.compareAndSet(false, true)) {
                Log.w(TAG, "Export already in progress (race condition), skipping.");
                return;
            }
            
            sExportStartTime = System.currentTimeMillis();

            final String senderPackage = intent.getStringExtra("sender_package");
            final PendingResult pendingResult = goAsync();
            final Context appContext = context.getApplicationContext();

            Thread exportThread = new Thread(() -> {
                try {
                    performExport(appContext, senderPackage);
                } catch (Throwable t) {
                    Log.e(TAG, "Fatal error in export thread", t);
                    // Send error broadcast
                    sendErrorBroadcast(appContext, senderPackage, t.getMessage());
                } finally {
                    sIsExporting.set(false);
                    sExportStartTime = 0;
                    try {
                        pendingResult.finish();
                    } catch (Exception e) {
                        Log.w(TAG, "Error finishing pending result", e);
                    }
                }
            });
            exportThread.setName("DataExportThread");
            exportThread.start();
            
            // Schedule a timeout check
            sHandler.postDelayed(() -> {
                if (sIsExporting.get() && exportThread.isAlive()) {
                    Log.w(TAG, "Export timeout reached, interrupting thread");
                    exportThread.interrupt();
                    // Force reset after a grace period
                    sHandler.postDelayed(() -> {
                        if (sIsExporting.get()) {
                            Log.w(TAG, "Force resetting export state after timeout");
                            sIsExporting.set(false);
                            sExportStartTime = 0;
                        }
                    }, 5000);
                }
            }, EXPORT_TIMEOUT_MS);
        }
    }
    
    /**
     * Send an error broadcast when export fails
     */
    private void sendErrorBroadcast(Context context, String senderPackage, String errorMessage) {
        try {
            Intent resultIntent = new Intent("com.appcloner.replica.EXPORT_COMPLETED");
            if (senderPackage != null && !senderPackage.isEmpty()) {
                resultIntent.setPackage(senderPackage);
            } else {
                resultIntent.setPackage("com.appcloner.replica");
            }
            resultIntent.putExtra("exported_package", context.getPackageName());
            resultIntent.putExtra("export_success", false);
            resultIntent.putExtra("error_message", errorMessage != null ? errorMessage : "Unknown error");
            context.sendBroadcast(resultIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send error broadcast", e);
        }
    }
    
    /**
     * Check if export is currently in progress
     */
    public static boolean isExporting() {
        return sIsExporting.get();
    }
    
    /**
     * Reset export state (for emergency use)
     */
    public static void resetExportState() {
        sIsExporting.set(false);
        sExportStartTime = 0;
        Log.i(TAG, "Export state manually reset");
    }

    private void performExport(Context context, String senderPackage) {
        String packageName = context.getPackageName();
        Log.i(TAG, "Starting export for package: " + packageName);

        Intent resultIntent = new Intent("com.appcloner.replica.EXPORT_COMPLETED");
        if (senderPackage != null && !senderPackage.isEmpty()) {
            resultIntent.setPackage(senderPackage);
        } else {
            resultIntent.setPackage("com.appcloner.replica");
        }
        resultIntent.putExtra("exported_package", packageName);

        try {
            AppDataManager dataManager = new AppDataManager(context, packageName, false);
            File exportedFile = dataManager.exportAppData();

            resultIntent.putExtra("export_success", true);
            if (exportedFile != null) {
                resultIntent.putExtra("export_path", exportedFile.getAbsolutePath());
                Log.d(TAG, "Export successful: " + exportedFile.getAbsolutePath());
            } else {
                // MediaStore case (Android 10+)
                resultIntent.putExtra("export_path", "Downloads directory");
                Log.d(TAG, "Export successful to Downloads");
            }

        } catch (Throwable t) {
            Log.e(TAG, "Export failed", t);
            resultIntent.putExtra("export_success", false);
            resultIntent.putExtra("error_message", t.getMessage());
        }

        try {
            context.sendBroadcast(resultIntent);
            Log.i(TAG, "Result broadcast sent.");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to send result broadcast", t);
        }
    }

    /**
     * Check if caller has IPC permission or is calling from within the same app.
     * This allows both the app cloner (with IPC permission) and internal operations
     * to work properly.
     */
    private boolean hasPermissionOrSameApp(Context context) {
        // Allow same-app calls
        int callingUid = Binder.getCallingUid();
        int myUid = android.os.Process.myUid();
        if (callingUid == myUid) {
            Log.d(TAG, "Allowing same-app call (UID match)");
            return true;
        }
        
        // For broadcasts, check if caller is from the same package
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

        // Check if caller has IPC permission
        try {
            // Try checkCallingPermission first
            int permissionCheck = context.checkCallingPermission(IPC_PERMISSION);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "IPC permission granted via checkCallingPermission");
                return true;
            }
            // Fallback: checkCallingOrSelfPermission
            permissionCheck = context.checkCallingOrSelfPermission(IPC_PERMISSION);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "IPC permission granted via checkCallingOrSelfPermission");
                return true;
            }
            Log.w(TAG, "Permission denied. CallingUid=" + callingUid + ", MyUid=" + myUid);
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Error checking permission", e);
            return false;
        }
    }
}
