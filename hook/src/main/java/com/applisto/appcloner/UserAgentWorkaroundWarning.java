package com.applisto.appcloner;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * UserAgentWorkaroundWarning - Provides workarounds for Firebase-related issues
 * that may occur in cloned apps due to package name or signature mismatches.
 * 
 * This class hooks Firebase installation-related exceptions to:
 * 1. Suppress unnecessary warnings that spam the logs
 * 2. Show notifications to users when Firebase errors occur
 * 3. Allow apps to continue functioning despite Firebase initialization issues
 */
public class UserAgentWorkaroundWarning {
    private static final String TAG = "UserAgentWorkaroundWarning";
    
    // Handler for main thread operations (e.g., showing notifications)
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    
    // Whether to use high importance for notifications
    private static boolean sHighImportance = true;
    
    /**
     * Install the Firebase workaround hooks
     */
    public static void install() {
        Log.i(TAG, "install; ");
        
        try {
            // Create hook for Firebase installations exceptions
            XC_MethodHook firebaseHook = new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // Get the exception message if available
                    Object exception = param.thisObject;
                    String message = null;
                    
                    if (exception != null) {
                        try {
                            Method getMessage = exception.getClass().getMethod("getMessage");
                            message = (String) getMessage.invoke(exception);
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    
                    if (message != null) {
                        final String finalMessage = message;
                        // Post to main handler to show notification
                        sHandler.post(() -> {
                            showFirebaseWarningNotification(finalMessage);
                        });
                    }
                    
                    // After showing the first notification, reduce importance
                    // to avoid spamming the user
                    sHighImportance = false;
                }
            };
            
            // Try to hook Firebase exception classes
            hookFirebaseInstallationsException(firebaseHook);
            hookFirebaseMessagingException(firebaseHook);
            hookFirebaseAuthException(firebaseHook);
            
        } catch (Throwable t) {
            Log.w(TAG, "Error installing Firebase workaround hooks", t);
        }
    }
    
    /**
     * Hook FirebaseInstallationsException constructors
     */
    private static void hookFirebaseInstallationsException(XC_MethodHook hook) {
        try {
            Class<?> exceptionClass = Class.forName(
                    "com.google.firebase.installations.FirebaseInstallationsException");
            
            // Hook all constructors
            Constructor<?>[] constructors = exceptionClass.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                try {
                    XposedBridge.hookMethod(constructor, hook);
                    Log.d(TAG, "Hooked FirebaseInstallationsException constructor: " + 
                               constructor.toString());
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to hook constructor", t);
                }
            }
        } catch (ClassNotFoundException e) {
            // Class not found - Firebase Installations not used
            Log.d(TAG, "FirebaseInstallationsException not found, skipping");
        } catch (Throwable t) {
            Log.w(TAG, "Error hooking FirebaseInstallationsException", t);
        }
    }
    
    /**
     * Hook FirebaseMessagingException (for FCM issues)
     */
    private static void hookFirebaseMessagingException(XC_MethodHook hook) {
        try {
            Class<?> exceptionClass = Class.forName(
                    "com.google.firebase.messaging.FirebaseMessagingException");
            
            Constructor<?>[] constructors = exceptionClass.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                try {
                    XposedBridge.hookMethod(constructor, hook);
                    Log.d(TAG, "Hooked FirebaseMessagingException constructor");
                } catch (Throwable t) {
                    // Ignore individual failures
                }
            }
        } catch (ClassNotFoundException e) {
            // FCM not used
            Log.d(TAG, "FirebaseMessagingException not found, skipping");
        } catch (Throwable t) {
            Log.d(TAG, "FirebaseMessagingException hook skipped");
        }
    }
    
    /**
     * Hook FirebaseAuthException (for Auth issues)
     */
    private static void hookFirebaseAuthException(XC_MethodHook hook) {
        try {
            Class<?> exceptionClass = Class.forName(
                    "com.google.firebase.auth.FirebaseAuthException");
            
            Constructor<?>[] constructors = exceptionClass.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                try {
                    XposedBridge.hookMethod(constructor, hook);
                    Log.d(TAG, "Hooked FirebaseAuthException constructor");
                } catch (Throwable t) {
                    // Ignore individual failures
                }
            }
        } catch (ClassNotFoundException e) {
            // Firebase Auth not used
            Log.d(TAG, "FirebaseAuthException not found, skipping");
        } catch (Throwable t) {
            Log.d(TAG, "FirebaseAuthException hook skipped");
        }
    }
    
    /**
     * Show a notification warning about Firebase issues
     */
    private static void showFirebaseWarningNotification(String message) {
        try {
            // Use Utils.showNotification if available
            Class<?> utilsClass = Class.forName(
                    "com.applisto.appcloner.classes.secondary.util.Utils");
            Method showNotification = utilsClass.getMethod("showNotification", 
                    CharSequence.class, boolean.class);
            showNotification.invoke(null, message, sHighImportance);
        } catch (ClassNotFoundException e) {
            // Utils class not available, just log the message
            Log.w(TAG, "Firebase warning: " + message);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to show notification: " + message, t);
        }
    }
    
    /**
     * Hook Firebase Analytics to suppress tracking issues
     */
    public static void installAnalyticsWorkaround() {
        try {
            Class<?> analyticsClass = Class.forName(
                    "com.google.firebase.analytics.FirebaseAnalytics");
            
            // Hook getInstance to log but allow
            Method getInstance = analyticsClass.getMethod("getInstance", 
                    android.content.Context.class);
            
            XposedBridge.hookMethod(getInstance, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Log.d(TAG, "FirebaseAnalytics.getInstance() called");
                }
                
                @Override
                public void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.getResult() == null) {
                        Log.w(TAG, "FirebaseAnalytics.getInstance() returned null");
                    }
                }
            });
            
            Log.d(TAG, "Firebase Analytics workaround installed");
        } catch (ClassNotFoundException e) {
            // Firebase Analytics not used
            Log.d(TAG, "FirebaseAnalytics not found, skipping");
        } catch (Throwable t) {
            Log.d(TAG, "Firebase Analytics workaround skipped: " + t.getMessage());
        }
    }
    
    /**
     * Hook Firebase Crashlytics to suppress initialization issues
     */
    public static void installCrashlyticsWorkaround() {
        try {
            Class<?> crashlyticsClass = Class.forName(
                    "com.google.firebase.crashlytics.FirebaseCrashlytics");
            
            Method getInstance = crashlyticsClass.getMethod("getInstance");
            
            XposedBridge.hookMethod(getInstance, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.getThrowable() != null) {
                        Log.w(TAG, "Crashlytics initialization failed, suppressing");
                        param.setThrowable(null);
                        param.setResult(null);
                    }
                }
            });
            
            Log.d(TAG, "Firebase Crashlytics workaround installed");
        } catch (ClassNotFoundException e) {
            // Crashlytics not used
            Log.d(TAG, "FirebaseCrashlytics not found, skipping");
        } catch (Throwable t) {
            Log.d(TAG, "Firebase Crashlytics workaround skipped: " + t.getMessage());
        }
    }
}
