package com.applisto.appcloner;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Screenshot bypass using Pine hooking framework directly
 */
public class ScreenshotDetectionBlocker {

    private static final String TAG = "PineScreenshotBypass";
    private static final int FLAG_SECURE = 8192;

    public static void install(Context context) {
        try {
            if (!ClonerSettings.get(context).raw().optBoolean("AllowScreenshots", false)) {
                return;
            }

            // Hook Window.setFlags
            hookWindowSetFlags();

            // Hook Window.addFlags
            hookWindowAddFlags();

            // Hook WindowManager$LayoutParams
            hookLayoutParams();

            // Hook SurfaceView.setSecure
            hookSurfaceViewSetSecure();

            // Hook Activity.setRecentsScreenshotEnabled (API 33+)
            if (Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
                hookSetRecentsScreenshotEnabled();
            }

            Log.i(TAG, "All Pine hooks installed successfully");

        } catch (Throwable t) {
            Log.e(TAG, "Failed to install Pine hooks", t);
        }
    }

    private static void hookSetRecentsScreenshotEnabled() {
        try {
            // public void setRecentsScreenshotEnabled(boolean enabled)
            Method method = Activity.class.getMethod("setRecentsScreenshotEnabled", boolean.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    // Force argument to true
                    param.args[0] = true;
                    Log.d(TAG, "Activity.setRecentsScreenshotEnabled forced to true");
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook setRecentsScreenshotEnabled", e);
        }
    }

    private static void hookWindowSetFlags() throws Throwable {
        XposedBridge.hookMethod(
            Window.class.getDeclaredMethod("setFlags", int.class, int.class),
            new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    int flags = (int) param.args[0];
                    int mask = (int) param.args[1];

                    // Remove FLAG_SECURE
                    flags &= ~FLAG_SECURE;
                    mask &= ~FLAG_SECURE;

                    param.args[0] = flags;
                    param.args[1] = mask;

                    Log.d(TAG, "Window.setFlags - removed FLAG_SECURE");
                }
            }
        );
    }

    private static void hookWindowAddFlags() throws Throwable {
        XposedBridge.hookMethod(
            Window.class.getDeclaredMethod("addFlags", int.class),
            new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    int flags = (int) param.args[0];
                    flags &= ~FLAG_SECURE;
                    param.args[0] = flags;

                    Log.d(TAG, "Window.addFlags - removed FLAG_SECURE");
                }
            }
        );
    }

    private static void hookLayoutParams() throws Throwable {
        // Hook all LayoutParams constructors
        Class<?> layoutParamsClass = WindowManager.LayoutParams.class;

        // Constructor with no args
        XposedBridge.hookMethod(
            layoutParamsClass.getDeclaredConstructor(),
            new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    clearSecureFlagFromParams(param.thisObject);
                }
            }
        );

        // Constructor with flags
        XposedBridge.hookMethod(
            layoutParamsClass.getDeclaredConstructor(int.class, int.class),
            new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    int flags = (int) param.args[1];
                    param.args[1] = flags & ~FLAG_SECURE;
                }
            }
        );
    }

    private static void hookSurfaceViewSetSecure() throws Throwable {
        XposedBridge.hookMethod(
            SurfaceView.class.getDeclaredMethod("setSecure", boolean.class),
            new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    // Always set to false (not secure)
                    param.args[0] = false;
                    Log.d(TAG, "SurfaceView.setSecure forced to false");
                }
            }
        );
    }

    private static void clearSecureFlagFromParams(Object layoutParams) {
        try {
            Field flagsField = layoutParams.getClass().getField("flags");
            flagsField.setAccessible(true);
            int flags = flagsField.getInt(layoutParams);
            flagsField.setInt(layoutParams, flags & ~FLAG_SECURE);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing flag from params", e);
        }
    }
}
