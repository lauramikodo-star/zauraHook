package com.applisto.appcloner;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccessibleDataDirHook {
    private static final String TAG = "AccessibleDataDirHook";

    public enum AccessMode {
        READ_ONLY,   // Directories 755, files 644 (default, safer)
        READ_WRITE   // Directories 777, files 666 (world-writable; opt-in, riskier)
    }

    // Defaults can be tuned via setters or cloner.json
    private volatile boolean internalEnabled = true;
    private volatile boolean externalEnabled = true;
    private volatile AccessMode accessMode = AccessMode.READ_ONLY;
    private volatile boolean advancedMode = false;
    private volatile long advancedIntervalSec = 60;

    private Context appContext;
    private ScheduledThreadPoolExecutor scheduler;

    // Names/prefixes we DO NOT relax (left owner-only). Extend as needed.
    private final Set<String> restrictedNamePrefixes = new LinkedHashSet<>(Arrays.asList(
            "cloneSettings",
            "com.applisto.appcloner.classes"
    ));
    // Directories we typically don't need to expose broadly; extend prudently.
    private final Set<String> restrictedDirNames = new LinkedHashSet<>(Arrays.asList(
            "code_cache", "no_backup" // Removed "cache" from restrictions to be more permissive if needed
    ));
    
    // Lock to prevent concurrent operations that might conflict with data export
    private static final Object PERMISSION_LOCK = new Object();
    private static final AtomicBoolean sExportInProgress = new AtomicBoolean(false);
    
    // SharedPreferences file tracking for proper permission management
    private static final String SHARED_PREFS_DIR = "shared_prefs";
    
    // Handler for delayed re-application after export
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void init(Context context) {
        this.appContext = context.getApplicationContext();

        // Read settings
        ClonerSettings settings = ClonerSettings.get(context);
        this.internalEnabled = settings.accessibleDataDirInternalEnabled();
        this.externalEnabled = settings.accessibleDataDirExternalEnabled();
        try {
            this.accessMode = AccessMode.valueOf(settings.accessibleDataDirMode());
        } catch (IllegalArgumentException e) {
            this.accessMode = AccessMode.READ_ONLY;
        }
        this.advancedMode = settings.accessibleDataDirAdvancedMode();
        this.advancedIntervalSec = settings.accessibleDataDirAdvancedInterval();

        Log.i(TAG, "Initializing accessible data dir hook; mode=" + accessMode +
                ", internal=" + internalEnabled + ", external=" + externalEnabled +
                ", advanced=" + advancedMode + " (" + advancedIntervalSec + "s)");

        try {
            // Delay initial application to allow other hooks to initialize first
            // This helps prevent conflicts with SharedPreferences operations
            mainHandler.postDelayed(() -> {
                applyAccessibility();
                if (advancedMode) startAdvancedMode();
                Log.i(TAG, "Accessible data directory hook initialized");
            }, 1000);  // 1 second delay
        } catch (Throwable t) {
            Log.e(TAG, "Initialization failed", t);
        }
    }
    
    /**
     * Signal that a data export operation is starting.
     * This pauses permission modifications to prevent conflicts.
     */
    public static void onExportStarting() {
        Log.i(TAG, "Export starting - pausing permission modifications");
        sExportInProgress.set(true);
    }
    
    /**
     * Signal that a data export operation has completed.
     * This resumes normal permission modifications.
     */
    public static void onExportCompleted() {
        Log.i(TAG, "Export completed - resuming permission modifications");
        sExportInProgress.set(false);
    }
    
    /**
     * Check if we should skip permission modifications (e.g., during export)
     */
    private boolean shouldSkipModifications() {
        return sExportInProgress.get();
    }

    // Public controls
    public void setInternalEnabled(boolean enabled) {
        this.internalEnabled = enabled;
        Log.i(TAG, "Internal enabled = " + enabled);
    }

    public void setExternalEnabled(boolean enabled) {
        this.externalEnabled = enabled;
        Log.i(TAG, "External enabled = " + enabled);
    }

    public void setAccessMode(AccessMode mode) {
        this.accessMode = mode != null ? mode : AccessMode.READ_ONLY;
        Log.i(TAG, "Access mode = " + this.accessMode);
    }

    public void setAdvancedMode(boolean enabled) {
        this.advancedMode = enabled;
        Log.i(TAG, "Advanced mode = " + enabled);
        if (enabled) startAdvancedMode(); else stopAdvancedMode();
    }

    public void setAdvancedIntervalSeconds(long seconds) {
        this.advancedIntervalSec = Math.max(10, seconds);
        Log.i(TAG, "Advanced interval = " + this.advancedIntervalSec + "s");
        if (scheduler != null) {
            stopAdvancedMode();
            startAdvancedMode();
        }
    }

    public boolean isInternalEnabled() { return internalEnabled; }
    public boolean isExternalEnabled() { return externalEnabled; }
    public AccessMode getAccessMode() { return accessMode; }
    public boolean isAdvancedMode() { return advancedMode; }

    // Manual trigger (runs on a worker)
    public void applyAccessibility() {
        if (appContext == null) return;
        
        // Skip if export is in progress to prevent conflicts
        if (shouldSkipModifications()) {
            Log.d(TAG, "Skipping applyAccessibility - export in progress");
            return;
        }
        
        new Thread(() -> {
            synchronized (PERMISSION_LOCK) {
                long t0 = System.currentTimeMillis();
                try {
                    // Double-check export status inside the lock
                    if (shouldSkipModifications()) {
                        Log.d(TAG, "Skipping applyAccessibility (inside lock) - export in progress");
                        return;
                    }
                    
                    List<File> touchedRoots = new ArrayList<>();
                    if (internalEnabled) {
                        // Context.getDataDir() (API 24+) or parent of filesDir
                        File dataDir;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            dataDir = appContext.getDataDir();
                        } else {
                            dataDir = parentOf(appContext.getFilesDir());
                        }
                        dataDir = safeCanonical(dataDir);

                        if (dataDir != null) {
                            touchedRoots.add(dataDir);
                            // Walk everything except restricted, with special handling for shared_prefs
                            makeTreeAccessible(dataDir);
                        }
                    }
                    if (externalEnabled) {
                        for (File f : ContextCompat.getExternalFilesDirs(appContext, null)) {
                            if (f == null) continue;
                            File external = safeCanonical(f.getParentFile()); // .../Android/data/<pkg>
                            if (external != null) {
                                touchedRoots.add(external);
                                makeTreeAccessible(external);
                            }
                        }
                    }
                    Log.i(TAG, "Applied accessibility to " + touchedRoots + " in "
                            + (System.currentTimeMillis() - t0) + " ms");
                } catch (Throwable t) {
                    Log.e(TAG, "Error applying accessibility", t);
                }
            }
        }, "AccessibleDataDirHook-apply").start();
    }
    
    /**
     * Ensure SharedPreferences files are accessible for the DefaultProvider.
     * This is called after SharedPreferences operations to ensure they remain accessible.
     */
    public void ensureSharedPrefsAccessible() {
        if (appContext == null || !internalEnabled) return;
        
        new Thread(() -> {
            synchronized (PERMISSION_LOCK) {
                if (shouldSkipModifications()) return;
                
                try {
                    File dataDir;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        dataDir = appContext.getDataDir();
                    } else {
                        dataDir = parentOf(appContext.getFilesDir());
                    }
                    
                    if (dataDir != null) {
                        File sharedPrefsDir = new File(dataDir, SHARED_PREFS_DIR);
                        if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory()) {
                            makeTreeAccessible(sharedPrefsDir);
                            Log.d(TAG, "SharedPreferences directory permissions refreshed");
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Error ensuring SharedPrefs accessible", t);
                }
            }
        }, "AccessibleDataDirHook-prefs").start();
    }

    // Internals

    private void startAdvancedMode() {
        if (scheduler != null) return;
        if (appContext == null) {
            Log.w(TAG, "startAdvancedMode() called before init; ignoring.");
            return;
        }
        scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "AccessibleDataDirHook-adv");
            t.setDaemon(true);
            return t;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.scheduleWithFixedDelay(this::safeApplyOnce, advancedIntervalSec, advancedIntervalSec, TimeUnit.SECONDS);
        Log.i(TAG, "Advanced mode started (every " + advancedIntervalSec + "s).");
    }

    private void stopAdvancedMode() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            Log.i(TAG, "Advanced mode stopped.");
        }
    }

    private void safeApplyOnce() {
        if (appContext == null) return;
        try {
            long t0 = System.currentTimeMillis();
            if (internalEnabled) {
                File dataDir = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        ? appContext.getDataDir()
                        : parentOf(appContext.getFilesDir());
                if (dataDir != null) makeTreeAccessible(safeCanonical(dataDir));
            }
            if (externalEnabled) {
                for (File f : ContextCompat.getExternalFilesDirs(appContext, null)) {
                    File ext = (f != null) ? safeCanonical(f.getParentFile()) : null;
                    if (ext != null) makeTreeAccessible(ext);
                }
            }
            Log.d(TAG, "Advanced cycle took " + (System.currentTimeMillis() - t0) + " ms");
        } catch (Throwable t) {
            Log.w(TAG, "Advanced cycle error", t);
        }
    }

    private static File parentOf(File f) { return f != null ? f.getParentFile() : null; }

    private static File safeCanonical(File f) {
        if (f == null) return null;
        try { return f.getCanonicalFile(); }
        catch (IOException e) { return f.getAbsoluteFile(); }
    }

    private void makeTreeAccessible(File root) {
        if (root == null) return;
        // Log.v(TAG, "Walking: " + root); // verbose

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Files.walkFileTree(root.toPath(),
                        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                        Integer.MAX_VALUE,
                        new PermissiveVisitor(accessMode, restrictedNamePrefixes, restrictedDirNames));
                return;
            } catch (Throwable t) {
                Log.w(TAG, "walkFileTree failed for " + root + ", falling back", t);
            }
        }
        legacyRecurse(root, accessMode, restrictedNamePrefixes, restrictedDirNames);
    }

    private static class PermissiveVisitor extends SimpleFileVisitor<Path> {
        private final AccessMode mode;
        private final Set<String> restrictedPrefixes;
        private final Set<String> restrictedDirNames;

        PermissiveVisitor(AccessMode mode,
                          Set<String> restrictedPrefixes,
                          Set<String> restrictedDirNames) {
            this.mode = mode;
            this.restrictedPrefixes = restrictedPrefixes != null ? restrictedPrefixes : Collections.emptySet();
            this.restrictedDirNames = restrictedDirNames != null ? restrictedDirNames : Collections.emptySet();
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            File f = dir.toFile();
            if (shouldRestrictDir(f)) {
                setOwnerOnly(f, true);
                return FileVisitResult.SKIP_SUBTREE;
            }
            setDirPerms(f, mode);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            File f = file.toFile();
            if (shouldRestrictFile(f)) {
                setOwnerOnly(f, false);
            } else {
                setFilePerms(f, mode);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
        }

        private boolean shouldRestrictDir(File f) {
            String name = f.getName();
            return restrictedDirNames.contains(name) || hasRestrictedPrefix(name);
        }

        private boolean shouldRestrictFile(File f) {
            String name = f.getName();
            return hasRestrictedPrefix(name);
        }

        private boolean hasRestrictedPrefix(String name) {
            for (String p : restrictedPrefixes) {
                if (name.startsWith(p)) return true;
            }
            return false;
        }

        private static void setDirPerms(File dir, AccessMode mode) {
            Set<PosixFilePermission> posix = new HashSet<>();
            posix.add(PosixFilePermission.OWNER_READ);
            posix.add(PosixFilePermission.OWNER_WRITE);
            posix.add(PosixFilePermission.OWNER_EXECUTE);
            posix.add(PosixFilePermission.GROUP_READ);
            posix.add(PosixFilePermission.GROUP_EXECUTE);
            posix.add(PosixFilePermission.OTHERS_READ);
            posix.add(PosixFilePermission.OTHERS_EXECUTE);
            if (mode == AccessMode.READ_WRITE) {
                posix.add(PosixFilePermission.GROUP_WRITE);
                posix.add(PosixFilePermission.OTHERS_WRITE);
            }
            tryPosix(dir.toPath(), posix);

            dir.setReadable(true, false);
            dir.setExecutable(true, false);
            if (mode == AccessMode.READ_WRITE) dir.setWritable(true, false);
        }

        private static void setFilePerms(File file, AccessMode mode) {
            Set<PosixFilePermission> posix = new HashSet<>();
            posix.add(PosixFilePermission.OWNER_READ);
            posix.add(PosixFilePermission.OWNER_WRITE);
            posix.add(PosixFilePermission.GROUP_READ);
            posix.add(PosixFilePermission.OTHERS_READ);
            if (mode == AccessMode.READ_WRITE) {
                posix.add(PosixFilePermission.GROUP_WRITE);
                posix.add(PosixFilePermission.OTHERS_WRITE);
            }

            tryPosix(file.toPath(), posix);

            file.setReadable(true, false);
            if (mode == AccessMode.READ_WRITE) {
                file.setWritable(true, false);
            }
        }

        private static void setOwnerOnly(File f, boolean directory) {
            Set<PosixFilePermission> posix = new HashSet<>();
            posix.add(PosixFilePermission.OWNER_READ);
            posix.add(PosixFilePermission.OWNER_WRITE);
            if (directory) posix.add(PosixFilePermission.OWNER_EXECUTE);
            tryPosix(f.toPath(), posix);

            f.setReadable(true, true);
            f.setWritable(true, true);
            if (directory) f.setExecutable(true, true);
        }

        private static void tryPosix(Path p, Set<PosixFilePermission> perms) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
            try {
                PosixFileAttributeView view = Files.getFileAttributeView(p, PosixFileAttributeView.class);
                if (view != null) {
                    Files.setPosixFilePermissions(p, perms);
                }
            } catch (Throwable ignored) {}
        }
    }

    // Legacy recursive fallback for pre-26
    private static void legacyRecurse(File root,
                                      AccessMode mode,
                                      Set<String> restrictedPrefixes,
                                      Set<String> restrictedDirNames) {
        if (root == null) return;
        String name = root.getName();
        boolean isDir = root.isDirectory();

        if (isDir && (restrictedDirNames.contains(name) || hasRestrictedPrefix(name, restrictedPrefixes))) {
            setOwnerOnlyLegacy(root, true);
            return;
        }

        if (isDir) {
            setDirPermsLegacy(root, mode);
            File[] children = root.listFiles();
            if (children != null) {
                for (File c : children) {
                    legacyRecurse(c, mode, restrictedPrefixes, restrictedDirNames);
                }
            }
        } else {
            if (hasRestrictedPrefix(name, restrictedPrefixes)) {
                setOwnerOnlyLegacy(root, false);
            } else {
                setFilePermsLegacy(root, mode);
            }
        }
    }

    private static boolean hasRestrictedPrefix(String name, Set<String> prefixes) {
        if (prefixes == null) return false;
        for (String p : prefixes) if (name.startsWith(p)) return true;
        return false;
    }

    private static void setDirPermsLegacy(File dir, AccessMode mode) {
        dir.setReadable(true, false);
        dir.setExecutable(true, false);
        if (mode == AccessMode.READ_WRITE) dir.setWritable(true, false);
    }

    private static void setFilePermsLegacy(File file, AccessMode mode) {
        file.setReadable(true, false);
        if (mode == AccessMode.READ_WRITE) file.setWritable(true, false);
    }

    private static void setOwnerOnlyLegacy(File f, boolean directory) {
        f.setReadable(true, true);
        f.setWritable(true, true);
        if (directory) f.setExecutable(true, true);
    }
}
