package com.applisto.appcloner;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * FloatingAppHook - Enables floating window support for cloned applications.
 * 
 * This hook intercepts WindowManager operations to enable floating window behavior,
 * allowing apps to run in overlay/floating mode similar to picture-in-picture or
 * split-screen functionality.
 * 
 * Configuration (cloner.json):
 * - floating_app_enabled: boolean (default: false) - Enable/disable floating windows
 * - floating_window_width: int (default: 600) - Initial window width in pixels
 * - floating_window_height: int (default: 800) - Initial window height in pixels  
 * - floating_window_x: int (default: 100) - Initial X position
 * - floating_window_y: int (default: 100) - Initial Y position
 * - floating_override_permission: boolean (default: false) - Override system alert permission
 */
public class FloatingAppHook {
    private static final String TAG = "FloatingAppHook";
    private static volatile boolean sHooked = false;
    private static volatile boolean sEnabled = false;
    
    // Configuration fields
    private static int sWindowWidth = 600;
    private static int sWindowHeight = 800;
    private static int sWindowX = 100;
    private static int sWindowY = 100;
    private static boolean sOverrideSystemAlertWindowPermission = false;
    
    // WindowManager.LayoutParams flags for floating windows
    private static final int FLOATING_WINDOW_FLAGS = 
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

    /**
     * Initialize the floating app hook with configuration from ClonerSettings.
     * @param context Application context
     */
    public void init(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null, cannot initialize FloatingAppHook");
            return;
        }
        
        if (sHooked) {
            Log.d(TAG, "FloatingAppHook already initialized");
            return;
        }
        
        // Load configuration
        loadSettings(context);
        
        if (!sEnabled) {
            Log.i(TAG, "Floating app hook disabled via config");
            return;
        }
        
        sHooked = true;
        
        try {
            hookWindowManagerMethods();
            hookActivityMethods();
            Log.i(TAG, "Floating app hook initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize floating app hook", e);
            sHooked = false;
        }
    }
    
    /**
     * Load settings from ClonerSettings/cloner.json
     */
    private void loadSettings(Context context) {
        try {
            ClonerSettings settings = ClonerSettings.get(context);
            org.json.JSONObject cfg = settings.raw();
            
            sEnabled = cfg.optBoolean("floating_app", false);
            sWindowWidth = cfg.optInt("floating_window_width", 600);
            sWindowHeight = cfg.optInt("floating_window_height", 800);
            sWindowX = cfg.optInt("floating_window_x", 100);
            sWindowY = cfg.optInt("floating_window_y", 100);
            sOverrideSystemAlertWindowPermission = cfg.optBoolean("floating_override_permission", false);
            
            Log.d(TAG, "FloatingAppHook config loaded: enabled=" + sEnabled + 
                  ", size=" + sWindowWidth + "x" + sWindowHeight +
                  ", position=" + sWindowX + "," + sWindowY);
        } catch (Exception e) {
            Log.w(TAG, "Failed to load floating app settings, using defaults", e);
            sEnabled = false;
        }
    }

    /**
     * Hook WindowManager methods to intercept and modify window creation.
     */
    private void hookWindowManagerMethods() {
        try {
            // Hook WindowManagerImpl.addView to modify window parameters
            Class<?> windowManagerImplClass = Class.forName("android.view.WindowManagerImpl");
            Method addView = windowManagerImplClass.getDeclaredMethod(
                "addView", View.class, android.view.ViewGroup.LayoutParams.class);
            
            XposedBridge.hookMethod(addView, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    if (!sEnabled) return;
                    
                    try {
                        Object params = param.args[1];
                        if (params instanceof WindowManager.LayoutParams) {
                            WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) params;
                            View view = (View) param.args[0];
                            
                            if (shouldMakeFloating(layoutParams)) {
                                Context context = view != null ? view.getContext() : null;
                                if (context != null && !hasFloatingPermission(context)) {
                                    Log.w(TAG, "Missing SYSTEM_ALERT_WINDOW permission, skipping floating window for: " +
                                          view.getClass().getSimpleName());
                                    return;
                                }

                                modifyLayoutParamsForFloating(layoutParams);
                                Log.d(TAG, "Modified window params for floating: " + 
                                      (view != null ? view.getClass().getSimpleName() : "null"));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error in addView hook", e);
                    }
                }
            });

            // Hook WindowManagerImpl.updateViewLayout to maintain floating params
            try {
                Method updateViewLayout = windowManagerImplClass.getDeclaredMethod(
                    "updateViewLayout", View.class, android.view.ViewGroup.LayoutParams.class);
                
                XposedBridge.hookMethod(updateViewLayout, new XC_MethodHook() {
                    @Override
                    public void beforeHookedMethod(MethodHookParam param) {
                        if (!sEnabled) return;
                        
                        try {
                            Object params = param.args[1];
                            if (params instanceof WindowManager.LayoutParams) {
                                WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) params;
                                
                                // Preserve floating type on updates
                                if (isFloatingType(layoutParams.type)) {
                                    ensureFloatingFlags(layoutParams);
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error in updateViewLayout hook", e);
                        }
                    }
                });
            } catch (NoSuchMethodException e) {
                Log.w(TAG, "updateViewLayout method not found, skipping hook");
            }
            
            Log.d(TAG, "WindowManager methods hooked successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error hooking WindowManager methods", e);
        }
    }

    /**
     * Hook Activity lifecycle methods to handle orientation and focus in floating mode.
     */
    private void hookActivityMethods() {
        try {
            Class<?> activityClass = Class.forName("android.app.Activity");
            
            // Hook setRequestedOrientation to allow orientation changes in floating mode
            Method setRequestedOrientation = activityClass.getDeclaredMethod(
                "setRequestedOrientation", int.class);
            
            XposedBridge.hookMethod(setRequestedOrientation, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    if (!sEnabled) return;
                    // Allow all orientation changes for floating activities
                    Log.d(TAG, "Allowing orientation change for floating activity");
                }
            });

            // Hook onWindowFocusChanged to handle focus changes gracefully
            Method onWindowFocusChanged = activityClass.getDeclaredMethod(
                "onWindowFocusChanged", boolean.class);
            
            XposedBridge.hookMethod(onWindowFocusChanged, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    if (!sEnabled) return;
                    
                    try {
                        Boolean hasFocus = (Boolean) param.args[0];
                        if (hasFocus != null) {
                            Log.d(TAG, "Floating activity focus: " + hasFocus);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error in onWindowFocusChanged hook", e);
                    }
                }
            });
            
            Log.d(TAG, "Activity methods hooked successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error hooking Activity methods", e);
        }
    }

    /**
     * Determine if a window with the given params should be made floating.
     */
    private boolean shouldMakeFloating(WindowManager.LayoutParams params) {
        if (params == null) return false;
        
        int type = params.type;
        
        // Check for common dialog/popup types
        if (type == WindowManager.LayoutParams.TYPE_APPLICATION_PANEL ||
            type == WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL ||
            type == WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG) {
            return true;
        }
        
        // Check for overlay window types (already floating-like)
        if (type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
            return true;
        }
        
        // Deprecated types for older Android versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (type == WindowManager.LayoutParams.TYPE_PHONE ||
                type == WindowManager.LayoutParams.TYPE_SYSTEM_ALERT ||
                type == WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY) {
                return true;
            }
        }
        
        // Check for windows positioned at corners (likely floating-intended)
        if (params.gravity != Gravity.NO_GRAVITY && params.gravity != Gravity.FILL) {
            int gravity = params.gravity;
            if ((gravity & Gravity.TOP) != 0 || (gravity & Gravity.BOTTOM) != 0) {
                if ((gravity & Gravity.LEFT) != 0 || (gravity & Gravity.RIGHT) != 0) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if the window type is a floating type.
     */
    private boolean isFloatingType(int type) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return type == WindowManager.LayoutParams.TYPE_PHONE ||
                   type == WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
    }

    /**
     * Modify layout parameters for floating window behavior.
     */
    private void modifyLayoutParamsForFloating(WindowManager.LayoutParams params) {
        if (params == null) return;
        
        try {
            // Set appropriate window type for overlay
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                params.type = WindowManager.LayoutParams.TYPE_PHONE;
            }
            
            // Apply floating window flags
            ensureFloatingFlags(params);
            
            // Set translucent format for better visual
            params.format = PixelFormat.TRANSLUCENT;
            
            // Set gravity for positioning
            params.gravity = Gravity.TOP | Gravity.LEFT;
            
            // Apply configured position
            params.x = sWindowX;
            params.y = sWindowY;
            
            // Apply configured size if not explicitly set
            if (params.width <= 0 || params.width == WindowManager.LayoutParams.MATCH_PARENT) {
                params.width = sWindowWidth;
            }
            if (params.height <= 0 || params.height == WindowManager.LayoutParams.MATCH_PARENT) {
                params.height = sWindowHeight;
            }
            
            Log.d(TAG, "Modified layout params: type=" + params.type + 
                  ", flags=" + params.flags + 
                  ", size=" + params.width + "x" + params.height +
                  ", position=" + params.x + "," + params.y);
                  
        } catch (Exception e) {
            Log.w(TAG, "Failed to modify layout params for floating", e);
        }
    }
    
    /**
     * Ensure floating window flags are applied.
     */
    private void ensureFloatingFlags(WindowManager.LayoutParams params) {
        params.flags |= FLOATING_WINDOW_FLAGS;
        
        // Ensure the window can receive touch events when needed
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    }

    /**
     * Check if the app has permission to draw overlays (SYSTEM_ALERT_WINDOW).
     */
    private boolean hasFloatingPermission(Context context) {
        if (sOverrideSystemAlertWindowPermission) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true; // Before API 23, permission is granted at install time (mostly)
    }

    // ==================== Public API ====================

    /**
     * Enable or disable floating windows at runtime.
     */
    public static void setEnabled(boolean enable) {
        sEnabled = enable;
        Log.i(TAG, "Floating windows " + (enable ? "enabled" : "disabled"));
    }
    
    /**
     * Check if floating windows are enabled.
     */
    public static boolean isEnabled() {
        return sEnabled;
    }
    
    /**
     * Set window dimensions.
     */
    public static void setWindowSize(int width, int height) {
        sWindowWidth = Math.max(100, width);
        sWindowHeight = Math.max(100, height);
        Log.i(TAG, "Window size set to: " + sWindowWidth + "x" + sWindowHeight);
    }
    
    /**
     * Set window position.
     */
    public static void setWindowPosition(int x, int y) {
        sWindowX = Math.max(0, x);
        sWindowY = Math.max(0, y);
        Log.i(TAG, "Window position set to: " + sWindowX + "," + sWindowY);
    }

    /**
     * Add a floating view programmatically.
     * @param context Application context
     * @param view The view to add as floating
     * @param params Layout parameters (will be modified for floating)
     */
    public static void addFloatingView(Context context, View view, WindowManager.LayoutParams params) {
        if (context == null || view == null || params == null) {
            Log.e(TAG, "Invalid parameters for addFloatingView");
            return;
        }
        
        if (!sEnabled) {
            Log.w(TAG, "Floating windows are disabled");
            return;
        }
        
        try {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) {
                Log.e(TAG, "WindowManager service not available");
                return;
            }
            
            // Apply floating configuration
            FloatingAppHook instance = new FloatingAppHook();
            instance.modifyLayoutParamsForFloating(params);
            
            windowManager.addView(view, params);
            Log.i(TAG, "Added floating view: " + view.getClass().getSimpleName());
            
        } catch (WindowManager.BadTokenException e) {
            Log.e(TAG, "Failed to add floating view - bad token (invalid context or missing permission)", e);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to add floating view - permission denied (SYSTEM_ALERT_WINDOW required)", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to add floating view", e);
        }
    }

    /**
     * Remove a floating view.
     * @param context Application context
     * @param view The view to remove
     */
    public static void removeFloatingView(Context context, View view) {
        if (context == null || view == null) {
            Log.w(TAG, "Invalid parameters for removeFloatingView");
            return;
        }
        
        try {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                windowManager.removeView(view);
                Log.i(TAG, "Removed floating view: " + view.getClass().getSimpleName());
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "View not attached to window manager", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove floating view", e);
        }
    }
    
    /**
     * Update a floating view's position.
     * @param context Application context
     * @param view The view to update
     * @param x New X position
     * @param y New Y position
     */
    public static void updateFloatingViewPosition(Context context, View view, int x, int y) {
        if (context == null || view == null) return;
        
        try {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) return;
            
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();
            if (params != null) {
                params.x = x;
                params.y = y;
                windowManager.updateViewLayout(view, params);
                Log.d(TAG, "Updated floating view position to: " + x + "," + y);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to update floating view position", e);
        }
    }
}
