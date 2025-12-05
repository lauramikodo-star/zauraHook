package com.applisto.appcloner;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class FakeCameraHook {
    private static final String TAG = "FakeCameraHook";
    private static volatile boolean sHooked = false;

    /* ---------- Settings loaded from cloner.json ---------- */
    private static boolean ENABLED;
    private static String FAKE_IMAGE_PATH;
    private static boolean ROTATE_IMAGE;
    private static int ROTATION_ANGLE;
    private static boolean FLIP_HORIZONTALLY;
    private static boolean RESIZE_IMAGE;
    private static boolean ADD_EXIF_ATTRIBUTES;
    private static boolean RANDOMIZE_IMAGE;
    private static int RANDOMIZE_STRENGTH;
    private static boolean ALTERNATIVE_MODE;
    private static boolean OPEN_STREAM_WORKAROUND;
    private static boolean USE_RANDOM_IMAGE;
    private static boolean PRESERVE_ASPECT_RATIO;
    private static boolean CENTER_IMAGE;
    private static boolean FILL_IMAGE;
    private static String[] FAKE_IMAGE_PATHS;
    private static boolean HOOK_LOW_LEVEL_APIS;
    private static boolean SYSTEM_CAMERA_WORKAROUND;
    private static boolean ADD_SPOOFED_LOCATION;
    /* ==================================================== */

    // Cached fake images
    private static Bitmap sFakeBitmap;
    private static byte[] sFakeJpegData;
    private static List<Bitmap> sFakeBitmaps = new ArrayList<>();
    private static int sCurrentImageIndex = 0;
    
    // Thread pool for async bitmap operations
    private static final ExecutorService sBitmapExecutor = Executors.newFixedThreadPool(2);
    
    // Bitmap cache to avoid recomputation
    private static final ConcurrentHashMap<String, Bitmap> sBitmapCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, byte[]> sJpegCache = new ConcurrentHashMap<>();
    
    // Flag to indicate if bitmap loading is in progress
    private static final AtomicBoolean sLoadingInProgress = new AtomicBoolean(false);
    
    // Pre-cached resized bitmaps for common resolutions
    private static volatile Bitmap sCachedResizedBitmap;
    private static volatile int sCachedWidth = 0;
    private static volatile int sCachedHeight = 0;

    // Random for image randomization
    private static Random sRandom = new Random();

    // Handler for UI operations
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    // Notification ID
    private static final int NOTIFICATION_ID = 556712456;

    // Strings properties for notifications
    private static Properties sStringsProperties;

    // Time tracking
    private static long sPictureTakenMillis;
    private static long sPictureTakenNanos;

    // For "Fake Camera Active" logic
    private static long sImageSetMillis;

    // System camera workaround flag
    private static boolean sSystemCameraWorkaroundActive = false;

    private static Context sContext;

    // Static instance for helper access
    private static FakeCameraHook sInstance;

    public void init(Context ctx) {
        if (sHooked) return;
        sHooked = true;
        sContext = ctx;
        sInstance = this;

        loadSettings(ctx);
        if (!ENABLED) {
            Log.i(TAG, "FakeCameraHook disabled in cloner.json");
            return;
        }

        try {
            loadStringsProperties(ctx);
            loadFakeImages(ctx);
            hookCameraAPIs();
            
            // Additional hooks for enhanced compatibility
            if (HOOK_LOW_LEVEL_APIS) {
                hookLowLevelCameraAPIs();
            }
            
            // Activate system camera workaround if enabled
            if (SYSTEM_CAMERA_WORKAROUND) {
                activateSystemCameraWorkaround(ctx);
            }
            
            // Install App Support for intents
            FakeCameraAppSupport.install(ctx);

            // Show notification on startup
            showNotification();

            Log.i(TAG, "FakeCameraHook active with system camera workaround");
        } catch (Exception e) {
            Log.e(TAG, "Hook failed", e);
        }
    }

    /* ---------- Public Accessors for Helper Classes ---------- */
    public static void setFakeBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            sFakeBitmap = bitmap;
            // Clear the list so getCurrentFakeImage() uses this new bitmap instead of cycling assets
            sFakeBitmaps.clear();
            
            // Clear cached resized bitmap to force regeneration
            sCachedResizedBitmap = null;
            sCachedWidth = 0;
            sCachedHeight = 0;
            sBitmapCache.clear();
            sJpegCache.clear();
            
            // Convert to JPEG asynchronously to avoid blocking
            sBitmapExecutor.execute(() -> {
                sFakeJpegData = bitmapToJpeg(bitmap);
                // Pre-cache some common resolutions
                preCacheCommonResolutions(bitmap);
            });
            
            sImageSetMillis = System.currentTimeMillis();
            Log.i(TAG, "New fake bitmap set. Size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            showNotification();
        }
    }
    
    /**
     * Pre-cache resized bitmaps for common camera resolutions to reduce delay
     */
    private static void preCacheCommonResolutions(Bitmap sourceBitmap) {
        if (sourceBitmap == null) return;
        
        // Common camera resolutions
        int[][] resolutions = {
            {1920, 1080}, // Full HD
            {1280, 720},  // HD
            {640, 480},   // VGA
            {1080, 1920}, // Portrait Full HD
            {720, 1280},  // Portrait HD
        };
        
        for (int[] res : resolutions) {
            String cacheKey = res[0] + "x" + res[1];
            if (!sBitmapCache.containsKey(cacheKey)) {
                try {
                    Bitmap resized = createResizedBitmapInternal(sourceBitmap, res[0], res[1]);
                    sBitmapCache.put(cacheKey, resized);
                    sJpegCache.put(cacheKey, bitmapToJpeg(resized));
                    Log.d(TAG, "Pre-cached resolution: " + cacheKey);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to pre-cache resolution: " + cacheKey, e);
                }
            }
        }
    }

    public static Bitmap getFakeBitmap() {
        return sFakeBitmap;
    }

    public static byte[] getFakeJpegData() {
        return sFakeJpegData;
    }

    public static boolean isFakeCameraActive() {
        // Active if an image was set recently (e.g., within 30 seconds)
        // or if we have a static image and are just always active?
        // The attachment logic suggested a timeout.
        return sFakeJpegData != null && (System.currentTimeMillis() - sImageSetMillis < 30000);
    }

    public static void showNotification() {
        if (sContext == null) return;
        sHandler.post(() -> {
            try {
                NotificationManager nm = (NotificationManager) sContext.getSystemService(Context.NOTIFICATION_SERVICE);
                String title = sStringsProperties.getProperty("fake_camera_title", "Fake Camera");
                String text = "Tap to change image.";

                Intent intent = new Intent(sContext, FakeCameraActivity.class);
                intent.putExtra("fake_camera_app", false);
                PendingIntent pi = PendingIntent.getActivity(sContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                Notification.Builder builder = new Notification.Builder(sContext);
                builder.setContentTitle(title)
                       .setContentText(text)
                       .setSmallIcon(android.R.drawable.ic_menu_camera)
                       .setContentIntent(pi)
                       .setAutoCancel(false)
                       .setOngoing(true);

                // Add Rotate action
                Intent rotateIntent = new Intent(sContext, CameraControlReceiver.class);
                rotateIntent.setAction(CameraControlReceiver.ACTION_ROTATE_CLOCKWISE);
                PendingIntent rotatePi = PendingIntent.getBroadcast(sContext, 1, rotateIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                builder.addAction(android.R.drawable.ic_menu_rotate, "ROTATE", rotatePi);

                // Add Flip action
                Intent flipIntent = new Intent(sContext, CameraControlReceiver.class);
                flipIntent.setAction(CameraControlReceiver.ACTION_FLIP_HORIZONTALLY);
                PendingIntent flipPi = PendingIntent.getBroadcast(sContext, 2, flipIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                builder.addAction(android.R.drawable.ic_menu_directions, "FLIP", flipPi);

                // Add Scale action
                Intent scaleIntent = new Intent(sContext, CameraControlReceiver.class);
                scaleIntent.setAction(CameraControlReceiver.ACTION_TOGGLE_SCALE);
                PendingIntent scalePi = PendingIntent.getBroadcast(sContext, 3, scaleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                builder.addAction(android.R.drawable.ic_menu_crop, FILL_IMAGE ? "FIT" : "FILL", scalePi);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                     String channelId = "fake_camera_status";
                     android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, "Fake Camera Status", NotificationManager.IMPORTANCE_LOW);
                     nm.createNotificationChannel(channel);
                     builder.setChannelId(channelId);
                }

                if (sFakeBitmap != null) {
                    Notification.BigPictureStyle style = new Notification.BigPictureStyle();
                    style.bigPicture(sFakeBitmap);
                    style.setSummaryText(text);
                    builder.setStyle(style);
                }

                nm.notify(NOTIFICATION_ID + 1, builder.build());
            } catch (Exception e) {
                Log.w(TAG, "Failed to show notification", e);
            }
        });
    }

    public static void performRotate(boolean clockwise) {
        if (sFakeBitmap == null) return;

        Matrix matrix = new Matrix();
        matrix.postRotate(clockwise ? 90 : -90);

        Bitmap rotatedBitmap = Bitmap.createBitmap(sFakeBitmap, 0, 0, sFakeBitmap.getWidth(), sFakeBitmap.getHeight(), matrix, true);
        setFakeBitmap(rotatedBitmap);
        showNotification();
    }

    public static void performFlip() {
        if (sFakeBitmap == null) return;

        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1, sFakeBitmap.getWidth() / 2f, sFakeBitmap.getHeight() / 2f);

        Bitmap flippedBitmap = Bitmap.createBitmap(sFakeBitmap, 0, 0, sFakeBitmap.getWidth(), sFakeBitmap.getHeight(), matrix, true);
        setFakeBitmap(flippedBitmap);
        showNotification();
    }

    public static void toggleScale() {
        FILL_IMAGE = !FILL_IMAGE;
        showNotification();
        Toast.makeText(sContext, FILL_IMAGE ? "Scale: Fill Screen" : "Scale: Fit Image", Toast.LENGTH_SHORT).show();
    }

    /**
     * Processes an image by saving to temp file, adding EXIF, and reading back.
     */
    public static byte[] processImageWithExif(Context context, byte[] jpegData, int width, int height) {
        if (sInstance == null || !ADD_EXIF_ATTRIBUTES) {
            return jpegData;
        }

        File tempFile = sInstance.saveToTempFile(jpegData);
        if (tempFile != null) {
            sInstance.setGeneralExifAttributes(tempFile, width, height);
            if (ADD_SPOOFED_LOCATION) {
                sInstance.setLocationExifAttributes(tempFile);
            }

            try (FileInputStream fis = new FileInputStream(tempFile)) {
                 byte[] newData = new byte[(int) tempFile.length()];
                 fis.read(newData);
                 jpegData = newData;
            } catch (IOException e) {
                Log.w(TAG, "Failed to read back temp file", e);
            }
            tempFile.delete();
        }
        return jpegData;
    }

    /* ---------- 1. Load settings ---------- */
    private void loadSettings(Context ctx) {
        try {
            ClonerSettings settings = ClonerSettings.get(ctx);
            JSONObject cfg = settings.raw();
            
            // Main enable toggle
            ENABLED = settings.fakeCameraEnabled();
            FAKE_IMAGE_PATH = settings.fakeCameraImagePath();
            
            // New organized settings matching screenshot categories
            ALTERNATIVE_MODE = settings.fakeCameraAlternativeMode();
            FLIP_HORIZONTALLY = settings.fakeCameraFlipHorizontally();
            OPEN_STREAM_WORKAROUND = settings.fakeCameraOpenStreamWorkaround();
            RANDOMIZE_IMAGE = settings.fakeCameraRandomizeImage();
            RANDOMIZE_STRENGTH = settings.fakeCameraRandomizeStrength();
            RESIZE_IMAGE = settings.fakeCameraResizeImage();
            
            // Rotation setting - parse from string
            String rotation = settings.fakeCameraRotation();
            if ("NO_CHANGE".equals(rotation)) {
                ROTATE_IMAGE = false;
                ROTATION_ANGLE = 0;
            } else {
                ROTATE_IMAGE = true;
                try {
                    ROTATION_ANGLE = Integer.parseInt(rotation);
                } catch (NumberFormatException e) {
                    ROTATION_ANGLE = 0;
                }
            }
            
            // Legacy settings for backward compatibility
            ADD_EXIF_ATTRIBUTES = settings.fakeCameraAddExifAttributes();
            ADD_SPOOFED_LOCATION = settings.fakeCameraAddSpoofedLocation();
            
            // Additional settings from raw config (not in new organized list)
            USE_RANDOM_IMAGE = cfg.optBoolean("UseRandomImage", false);
            PRESERVE_ASPECT_RATIO = cfg.optBoolean("PreserveAspectRatio", true);
            CENTER_IMAGE = cfg.optBoolean("CenterImage", true);
            FILL_IMAGE = cfg.optBoolean("FillImage", false);
            HOOK_LOW_LEVEL_APIS = cfg.optBoolean("HookLowLevelAPIs", false);
            SYSTEM_CAMERA_WORKAROUND = cfg.optBoolean("SystemCameraWorkaround", false);
            
            // Load multiple image paths if available
            if (cfg.has("FakeImagePaths")) {
                org.json.JSONArray pathsArray = cfg.optJSONArray("FakeImagePaths");
                if (pathsArray != null) {
                    FAKE_IMAGE_PATHS = new String[pathsArray.length()];
                    for (int i = 0; i < pathsArray.length(); i++) {
                        FAKE_IMAGE_PATHS[i] = pathsArray.optString(i, "fake_camera.jpg");
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Cannot read cloner.json – hook disabled", t);
            ENABLED = false;
        }
    }

    /* ---------- 2. Load strings properties ---------- */
    private void loadStringsProperties(Context ctx) {
        sStringsProperties = new Properties();
        
        // Set default values first
        sStringsProperties.setProperty("fake_camera_title", "Fake Camera");
        sStringsProperties.setProperty("fake_camera_text", "Tap to change image");
        sStringsProperties.setProperty("fake_camera_active", "Camera Active");
        sStringsProperties.setProperty("fake_camera_inactive", "Camera Inactive");
        sStringsProperties.setProperty("fake_camera_notification_text", "Fake camera is active. Tap to change the image.");
        sStringsProperties.setProperty("action_rotate", "Rotate");
        sStringsProperties.setProperty("action_flip", "Flip");
        sStringsProperties.setProperty("action_scale_fill", "Fill");
        sStringsProperties.setProperty("action_scale_fit", "Fit");
        sStringsProperties.setProperty("toast_image_rotated", "Image rotated");
        sStringsProperties.setProperty("toast_image_flipped", "Image flipped");
        sStringsProperties.setProperty("toast_scale_fill", "Scale: Fill Screen");
        sStringsProperties.setProperty("toast_scale_fit", "Scale: Fit Image");
        sStringsProperties.setProperty("toast_image_set", "Fake image set successfully");
        sStringsProperties.setProperty("error_no_image", "No fake image available");
        sStringsProperties.setProperty("error_load_failed", "Failed to load fake image");
        
        // Try to load from assets (will override defaults if file exists)
        try {
            InputStream is = ctx.getAssets().open("strings.properties");
            sStringsProperties.load(is);
            is.close();
            Log.d(TAG, "Loaded strings.properties from assets");
        } catch (IOException e) {
            Log.d(TAG, "strings.properties not found in assets, using defaults");
        }
    }

    /* ---------- 3. Load fake images ---------- */
    private void loadFakeImages(Context ctx) throws IOException {
        try {
            // Try to load multiple images first
            if (FAKE_IMAGE_PATHS != null && FAKE_IMAGE_PATHS.length > 0) {
                loadMultipleFakeImages(ctx);
            } else {
                // Fallback to single image
                loadSingleFakeImage(ctx);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load fake images, using fallback", e);
            createFallbackImage();
        }
    }

    private void loadMultipleFakeImages(Context ctx) throws IOException {
        sFakeBitmaps.clear();
        
        for (String path : FAKE_IMAGE_PATHS) {
            try (InputStream is = ctx.getAssets().open(path)) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) {
                    sFakeBitmaps.add(applyImageTransformations(bitmap));
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to load image: " + path, e);
            }
        }
        
        if (!sFakeBitmaps.isEmpty()) {
            sFakeBitmap = sFakeBitmaps.get(0);
            sFakeJpegData = bitmapToJpeg(sFakeBitmap);
        } else {
            throw new IOException("No valid images found");
        }
    }

    private void loadSingleFakeImage(Context ctx) throws IOException {
        // Use optimized bitmap loading with inSampleSize
        BitmapFactory.Options options = new BitmapFactory.Options();
        
        // First, decode bounds only to determine optimal sample size
        options.inJustDecodeBounds = true;
        try (InputStream is = ctx.getAssets().open(FAKE_IMAGE_PATH)) {
            BitmapFactory.decodeStream(is, null, options);
        }
        
        // Calculate inSampleSize - don't need images larger than 4K
        options.inSampleSize = calculateInSampleSize(options, 3840, 2160);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        
        // Now decode the actual bitmap with optimal size
        try (InputStream is = ctx.getAssets().open(FAKE_IMAGE_PATH)) {
            sFakeBitmap = BitmapFactory.decodeStream(is, null, options);
            if (sFakeBitmap == null) {
                throw new IOException("Failed to decode bitmap from assets: " + FAKE_IMAGE_PATH);
            }

            // Apply transformations if needed
            sFakeBitmap = applyImageTransformations(sFakeBitmap);

            // Convert to JPEG for still captures
            sFakeJpegData = bitmapToJpeg(sFakeBitmap);
            
            // Pre-cache common resolutions asynchronously
            sBitmapExecutor.execute(() -> preCacheCommonResolutions(sFakeBitmap));

            Log.d(TAG, "Loaded fake image: " + sFakeBitmap.getWidth() + "x" + sFakeBitmap.getHeight());
        }
    }
    
    /**
     * Calculate optimal sample size for bitmap decoding
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private void createFallbackImage() {
        // Create a simple colored bitmap as fallback
        sFakeBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sFakeBitmap);
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        canvas.drawRect(0, 0, sFakeBitmap.getWidth(), sFakeBitmap.getHeight(), paint);
        
        // Add text
        paint.setColor(Color.WHITE);
        paint.setTextSize(32);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("FAKE CAMERA", sFakeBitmap.getWidth()/2, sFakeBitmap.getHeight()/2, paint);
        
        sFakeJpegData = bitmapToJpeg(sFakeBitmap);
        Log.d(TAG, "Created fallback image");
    }

    /* ---------- 4. Hook Camera APIs ---------- */
    private void hookCameraAPIs() throws Exception {
        // Hook Camera1 APIs
        hookCamera1APIs();

        // Hook Camera2 APIs if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            hookCamera2APIs();
        }

        // Hook ImageReader APIs (Unconditionally, as CameraX relies on them)
        hookImageReaderAPIs();
        
        // Hook Image Plane APIs for additional coverage
        hookImagePlaneAPIs();

        // Hook ContentResolver APIs if workaround is enabled
        if (OPEN_STREAM_WORKAROUND) {
            hookContentResolverAPIs();
        }

        // Hook Video Recording APIs
        hookVideoRecordingAPIs();
        
        // Hook SurfaceTexture for preview
        hookSurfaceTextureAPIs();
        
        // Hook Bitmap-based camera capture APIs
        hookBitmapCaptureAPIs();
    }

    /* ---------- Camera1 API Hooking ---------- */
    private void hookCamera1APIs() throws Exception {
        // Hook Camera.startPreview
        Method startPreviewMethod = Camera.class.getMethod("startPreview");
        XposedBridge.hookMethod(startPreviewMethod, new XC_MethodHook() {
            @Override
            public void beforeHookedMethod(MethodHookParam param) {
                Log.d(TAG, "Camera.startPreview hooked");
            }
        });

        // Hook Camera.release
        Method releaseMethod = Camera.class.getMethod("release");
        XposedBridge.hookMethod(releaseMethod, new XC_MethodHook() {
            @Override
            public void beforeHookedMethod(MethodHookParam param) {
                Log.d(TAG, "Camera.release hooked");
            }
        });

        // Hook Camera.takePicture
        Method takePictureMethod = Camera.class.getMethod("takePicture",
                ShutterCallback.class, PictureCallback.class, PictureCallback.class);

        XposedBridge.hookMethod(takePictureMethod, new XC_MethodHook() {
            @Override
            public void beforeHookedMethod(MethodHookParam param) {
                Camera camera = (Camera) param.thisObject;
                ShutterCallback shutterCallback = (ShutterCallback) param.args[0];
                PictureCallback jpegCallback = (PictureCallback) param.args[2];

                Log.d(TAG, "Camera.takePicture hooked");

                // Get camera resolution
                Point cameraResolution = null;
                if (RESIZE_IMAGE) {
                    cameraResolution = getCameraResolution(camera);
                }

                // Process the fake image with the correct resolution
                byte[] jpegData;
                Bitmap currentBitmap = getCurrentFakeImage();
                
                if (RESIZE_IMAGE && cameraResolution != null) {
                    Bitmap resizedBitmap = resizeBitmap(currentBitmap, cameraResolution.x, cameraResolution.y);
                    jpegData = bitmapToJpeg(resizedBitmap);
                } else {
                    jpegData = bitmapToJpeg(currentBitmap);
                }

                // Save to temp file to add EXIF
                File tempFile = saveToTempFile(jpegData);
                if (tempFile != null) {
                    // Add attributes
                    int width = (RESIZE_IMAGE && cameraResolution != null) ? cameraResolution.x : currentBitmap.getWidth();
                    int height = (RESIZE_IMAGE && cameraResolution != null) ? cameraResolution.y : currentBitmap.getHeight();

                    setGeneralExifAttributes(tempFile, width, height);
                    if (ADD_SPOOFED_LOCATION) {
                        setLocationExifAttributes(tempFile);
                    }

                    // Read back
                    try (FileInputStream fis = new FileInputStream(tempFile)) {
                         byte[] newData = new byte[(int) tempFile.length()];
                         fis.read(newData);
                         jpegData = newData;
                    } catch (IOException e) {
                        Log.w(TAG, "Failed to read back temp file", e);
                    }
                    tempFile.delete();
                }

                final byte[] finalJpegData = jpegData;

                // Call the callbacks with our fake image
                sHandler.post(() -> {
                    try {
                        if (shutterCallback != null) {
                            shutterCallback.onShutter();
                        }

                        if (jpegCallback != null) {
                            jpegCallback.onPictureTaken(finalJpegData, camera);
                        }

                        sPictureTakenMillis = System.currentTimeMillis();
                        sPictureTakenNanos = System.nanoTime();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in takePicture callback", e);
                    }
                });

                // Skip the original method
                param.setResult(null);
            }
        });
    }

    private File saveToTempFile(byte[] data) {
        try {
            File temp = File.createTempFile("fake_cam_", ".jpg", sContext.getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(temp)) {
                fos.write(data);
            }
            return temp;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save temp file", e);
            return null;
        }
    }

    /* ---------- Camera2 API Hooking ---------- */
    private void hookCamera2APIs() throws Exception {
        String methodName = "openCamera";
        Class<?>[] paramTypes;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            paramTypes = new Class<?>[]{String.class, Executor.class, CameraDevice.StateCallback.class};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            paramTypes = new Class<?>[]{String.class, Executor.class, CameraDevice.StateCallback.class};
        } else {
            paramTypes = new Class<?>[]{String.class, CameraDevice.StateCallback.class, Handler.class};
        }

        Method openCameraMethod = null;
        try {
            openCameraMethod = CameraManager.class.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    openCameraMethod = CameraManager.class.getMethod(methodName, String.class, CameraDevice.StateCallback.class, Executor.class);
                } else {
                    openCameraMethod = CameraManager.class.getMethod(methodName, String.class, CameraDevice.StateCallback.class, Handler.class);
                }
            } catch (NoSuchMethodException e2) {
                Log.e(TAG, "Failed to find any openCamera method signature.", e2);
                return;
            }
        }

        XposedBridge.hookMethod(openCameraMethod, new XC_MethodHook() {
            @Override
            public void beforeHookedMethod(MethodHookParam param) {
                String cameraId = (String) param.args[0];
                Log.d(TAG, "CameraManager.openCamera hooked for camera: " + cameraId);
            }
        });

        try {
            Class<?> cameraDeviceImplClass = Class.forName("android.hardware.camera2.impl.CameraDeviceImpl");
            Method closeMethod = cameraDeviceImplClass.getMethod("close");
            XposedBridge.hookMethod(closeMethod, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "CameraDeviceImpl.close hooked");
                }
            });
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "CameraDeviceImpl class not found, skipping hook");
        }
    }

    /* ---------- ImageReader API Hooking ---------- */
    private void hookImageReaderAPIs() throws Exception {
        Method acquireLatestImageMethod = ImageReader.class.getMethod("acquireLatestImage");
        XposedBridge.hookMethod(acquireLatestImageMethod, new XC_MethodHook() {
            @Override
            public void afterHookedMethod(MethodHookParam param) {
                Log.d(TAG, "ImageReader.acquireLatestImage hooked (afterCall)");
                Image realImage = (Image) param.getResult();
                if (realImage != null) {
                    overwriteImageWithFakeData(realImage);
                }
            }
        });

        Method acquireNextImageMethod = ImageReader.class.getMethod("acquireNextImage");
        XposedBridge.hookMethod(acquireNextImageMethod, new XC_MethodHook() {
            @Override
            public void afterHookedMethod(MethodHookParam param) {
                Log.d(TAG, "ImageReader.acquireNextImage hooked (afterCall)");
                Image realImage = (Image) param.getResult();
                if (realImage != null) {
                    overwriteImageWithFakeData(realImage);
                }
            }
        });
        
        // Hook ImageReader.newInstance to log what formats are being requested
        try {
            Method newInstanceMethod = ImageReader.class.getMethod("newInstance", 
                    int.class, int.class, int.class, int.class);
            XposedBridge.hookMethod(newInstanceMethod, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    int width = (int) param.args[0];
                    int height = (int) param.args[1];
                    int format = (int) param.args[2];
                    int maxImages = (int) param.args[3];
                    Log.d(TAG, "ImageReader.newInstance: " + width + "x" + height + 
                               ", format=" + ImageUtils.getFormatName(format) + 
                               ", maxImages=" + maxImages);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook ImageReader.newInstance", e);
        }
        
        // Hook additional Image acquisition methods if available (API 29+)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // ImageReader.acquireNextImageNoThrowISE (API 29+)
                Method acquireNextImageNoThrowMethod = ImageReader.class.getMethod("acquireNextImageNoThrowISE");
                XposedBridge.hookMethod(acquireNextImageNoThrowMethod, new XC_MethodHook() {
                    @Override
                    public void afterHookedMethod(MethodHookParam param) {
                        Log.d(TAG, "ImageReader.acquireNextImageNoThrowISE hooked (afterCall)");
                        Image realImage = (Image) param.getResult();
                        if (realImage != null) {
                            overwriteImageWithFakeData(realImage);
                        }
                    }
                });
            }
        } catch (Exception e) {
            Log.d(TAG, "acquireNextImageNoThrowISE not available on this API level");
        }
    }
    
    /* ---------- Image Plane API Hooking ---------- */
    private void hookImagePlaneAPIs() throws Exception {
        // Hook Image.getPlanes to intercept plane data access
        try {
            Method getPlanesMethod = Image.class.getMethod("getPlanes");
            XposedBridge.hookMethod(getPlanesMethod, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    Image image = (Image) param.thisObject;
                    if (image != null) {
                        int format = image.getFormat();
                        Log.d(TAG, "Image.getPlanes called for format: " + 
                                   ImageUtils.getFormatName(format));
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook Image.getPlanes", e);
        }
    }

    /* ---------- ContentResolver API Hooking ---------- */
    private void hookContentResolverAPIs() throws Exception {
        Method openFileDescriptorMethod = ContentResolver.class.getMethod("openFileDescriptor",
                Uri.class, String.class, CancellationSignal.class);

        XposedBridge.hookMethod(openFileDescriptorMethod, new XC_MethodHook() {
            @Override
            public void beforeHookedMethod(MethodHookParam param) {
                Uri uri = (Uri) param.args[0];
                Log.d(TAG, "ContentResolver.openFileDescriptor hooked for URI: " + uri);
            }
        });

        Method openInputStreamMethod = ContentResolver.class.getMethod("openInputStream", Uri.class);
        XposedBridge.hookMethod(openInputStreamMethod, new XC_MethodHook() {
            @Override
            public void beforeHookedMethod(MethodHookParam param) {
                Uri uri = (Uri) param.args[0];
                Log.d(TAG, "ContentResolver.openInputStream hooked for URI: " + uri);
                
                if (isCameraImageUri(uri)) {
                    Log.d(TAG, "Camera image URI detected: " + uri);
                }
            }
        });
    }

    /* ---------- Video Recording API Hooking ---------- */
    private void hookVideoRecordingAPIs() throws Exception {
        try {
            Method startMethod = MediaRecorder.class.getMethod("start");
            XposedBridge.hookMethod(startMethod, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "MediaRecorder.start hooked - starting video recording");
                }
            });
            
            Method stopMethod = MediaRecorder.class.getMethod("stop");
            XposedBridge.hookMethod(stopMethod, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "MediaRecorder.stop hooked - video recording stopped");
                }
            });
            
            Method setOutputFileMethod = MediaRecorder.class.getMethod("setOutputFile", String.class);
            XposedBridge.hookMethod(setOutputFileMethod, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    String path = (String) param.args[0];
                    Log.d(TAG, "MediaRecorder.setOutputFile hooked: " + path);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook MediaRecorder APIs", e);
        }
    }

    /* ---------- SurfaceTexture API Hooking ---------- */
    private void hookSurfaceTextureAPIs() throws Exception {
        try {
            Class<?> surfaceTextureClass = Class.forName("android.graphics.SurfaceTexture");
            
            Method updateTexImageMethod = surfaceTextureClass.getMethod("updateTexImage");
            XposedBridge.hookMethod(updateTexImageMethod, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    if (sSystemCameraWorkaroundActive) {
                        Log.d(TAG, "System camera workaround: Simulating successful updateTexImage");
                    }
                }
            });
            
            Method getTransformMatrixMethod = surfaceTextureClass.getMethod("getTransformMatrix", float[].class);
            XposedBridge.hookMethod(getTransformMatrixMethod, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    if (sSystemCameraWorkaroundActive) {
                        float[] matrix = (float[]) param.args[0];
                        if (matrix != null && matrix.length >= 16) {
                            for (int i = 0; i < 16; i++) {
                                matrix[i] = (i % 5 == 0) ? 1.0f : 0.0f;
                            }
                            Log.d(TAG, "System camera workaround: Providing default transform matrix");
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook SurfaceTexture APIs", e);
        }
    }

    /* ---------- Bitmap Capture API Hooking ---------- */
    private void hookBitmapCaptureAPIs() throws Exception {
        // Hook PixelCopy APIs if available (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Class<?> pixelCopyClass = Class.forName("android.view.PixelCopy");
                // Note: PixelCopy.request has multiple overloads, we hook the most common ones
                Log.d(TAG, "PixelCopy class found, monitoring for camera captures");
            } catch (ClassNotFoundException e) {
                Log.d(TAG, "PixelCopy not available");
            }
        }
        
        // Hook BitmapFactory.decodeByteArray to intercept JPEG decoding from camera
        try {
            Method decodeByteArrayMethod = BitmapFactory.class.getMethod("decodeByteArray", 
                    byte[].class, int.class, int.class);
            XposedBridge.hookMethod(decodeByteArrayMethod, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    // Only intercept during active camera capture
                    if (isFakeCameraActive()) {
                        byte[] data = (byte[]) param.args[0];
                        // Check if this looks like camera JPEG data
                        if (data != null && data.length > 2 && 
                            data[0] == (byte) 0xFF && data[1] == (byte) 0xD8) {
                            Log.d(TAG, "BitmapFactory.decodeByteArray intercepted (JPEG data)");
                            // Optionally replace with fake bitmap
                            Bitmap fakeBitmap = getCurrentFakeImage();
                            if (fakeBitmap != null) {
                                Bitmap result = (Bitmap) param.getResult();
                                if (result != null) {
                                    // If dimensions match roughly, consider replacing
                                    float widthRatio = (float) result.getWidth() / fakeBitmap.getWidth();
                                    float heightRatio = (float) result.getHeight() / fakeBitmap.getHeight();
                                    if (widthRatio > 0.5f && widthRatio < 2.0f && 
                                        heightRatio > 0.5f && heightRatio < 2.0f) {
                                        Log.d(TAG, "Camera JPEG decode detected, fake bitmap available");
                                    }
                                }
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook BitmapFactory.decodeByteArray", e);
        }
    }
    
    /* ---------- Low-Level API Hooking ---------- */
    private void hookLowLevelCameraAPIs() throws Exception {
        try {
            Method nativeSetupMethod = Camera.class.getDeclaredMethod("native_setup", Object.class);
            XposedBridge.hookMethod(nativeSetupMethod, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "Camera.native_setup hooked");
                }
            });
            
            Class<?> cameraServiceClass = Class.forName("android.hardware.camera2.CameraManager$CameraServiceBinderDecorator");
            Method binderDecorateMethod = cameraServiceClass.getMethod("decorate", Object.class);
            XposedBridge.hookMethod(binderDecorateMethod, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "CameraService binder decorate hooked");
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook low-level camera APIs", e);
        }
    }

    /* ---------- System Camera Workaround ---------- */
    private void activateSystemCameraWorkaround(Context context) {
        sSystemCameraWorkaroundActive = true;
        Log.i(TAG, "System camera workaround activated");
        
        try {
            Class<?> packageManagerClass = Class.forName("android.content.pm.PackageManager");
            Method hasSystemFeatureMethod = packageManagerClass.getMethod("hasSystemFeature", String.class);
            
            XposedBridge.hookMethod(hasSystemFeatureMethod, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    String feature = (String) param.args[0];
                    if (feature != null && 
                        (feature.startsWith("android.hardware.camera") || 
                         feature.contains("camera"))) {
                        Log.d(TAG, "PackageManager.hasSystemFeature intercepted for: " + feature);
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook PackageManager for system camera workaround", e);
        }
        
        try {
            Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
            Method getMemoryInfoMethod = activityManagerClass.getMethod("getMemoryInfo", 
                Class.forName("android.app.ActivityManager$MemoryInfo"));
            
            XposedBridge.hookMethod(getMemoryInfoMethod, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    Log.d(TAG, "ActivityManager.getMemoryInfo intercepted");
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Could not hook ActivityManager for system camera workaround", e);
        }
    }

    /* ---------- Helper Methods ---------- */

    private void overwriteImageWithFakeData(Image image) {
        int format = image.getFormat();
        
        // Check if format is supported using ImageUtils
        if (!ImageUtils.isFormatSupported(format)) {
            // Try to handle unsupported formats with fallback
            Log.w(TAG, "Attempting fallback for unsupported image format: " + 
                       ImageUtils.getFormatName(format));
            
            Image.Plane[] planes = image.getPlanes();
            if (planes != null && planes.length > 0) {
                Bitmap currentBitmap = getCurrentFakeImage();
                Bitmap resizedBitmap = resizeBitmap(currentBitmap, image.getWidth(), image.getHeight());
                
                // Try RGBA fallback for single-plane formats
                if (planes.length == 1) {
                    boolean success = ImageUtils.writeRGBAToPlanes(resizedBitmap, planes, 
                                                                    image.getWidth(), image.getHeight());
                    if (success) {
                        Log.d(TAG, "Successfully overwrote image using RGBA fallback for format: " + 
                                   ImageUtils.getFormatName(format));
                        return;
                    }
                }
                
                // Try YUV fallback for multi-plane formats
                if (planes.length >= 3) {
                    try {
                        byte[] yuvData = ImageUtils.bitmapToNV21(resizedBitmap);
                        ImageUtils.writeYuvToPlanes(yuvData, image.getWidth(), image.getHeight(), planes);
                        Log.d(TAG, "Successfully overwrote image using YUV fallback for format: " + 
                                   ImageUtils.getFormatName(format));
                        return;
                    } catch (Exception e) {
                        Log.w(TAG, "YUV fallback failed", e);
                    }
                }
            }
            
            Log.w(TAG, "Cannot overwrite unsupported image format: " + ImageUtils.getFormatName(format));
            return;
        }

        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            Log.e(TAG, "Image has no planes to overwrite.");
            return;
        }

        Bitmap currentBitmap = getCurrentFakeImage();
        Bitmap resizedBitmap = resizeBitmap(currentBitmap, image.getWidth(), image.getHeight());

        // Use the unified ImageUtils method for all supported formats
        boolean success = ImageUtils.writeFakeDataToImage(image, resizedBitmap);
        
        if (success) {
            Log.d(TAG, "Successfully overwrote Image buffer with fake " + 
                       ImageUtils.getFormatName(format) + " data.");
        } else {
            Log.e(TAG, "Failed to overwrite Image buffer for format: " + 
                       ImageUtils.getFormatName(format));
        }
    }

    private boolean isCameraImageUri(Uri uri) {
        if (uri == null) return false;
        String uriString = uri.toString().toLowerCase();
        return uriString.contains("dcim") || uriString.contains("camera") ||
                uriString.contains("pictures") || uriString.contains(".jpg") ||
                uriString.contains(".jpeg") || uriString.contains("photos");
    }

    private Point getCameraResolution(Camera camera) {
        try {
            Camera.Size pictureSize = camera.getParameters().getPictureSize();
            int width = pictureSize.width;
            int height = pictureSize.height;
            Log.d(TAG, "Camera1 resolution: " + width + "x" + height);
            return adjustCameraResolution(width, height);
        } catch (Exception e) {
            Log.e(TAG, "Error getting Camera1 resolution", e);
            return null;
        }
    }

    private Point adjustCameraResolution(int width, int height) {
        Canvas canvas = new Canvas();
        int maxBitmapWidth = canvas.getMaximumBitmapWidth();
        int maxBitmapHeight = canvas.getMaximumBitmapHeight();
        int maxSize = Math.min(maxBitmapWidth, maxBitmapHeight);

        Log.d(TAG, "Adjusting resolution: " + width + "x" + height +
                ", max size: " + maxSize);

        if (width > maxSize || height > maxSize) {
            float ratio = (width > height) ? (float) maxSize / width : (float) maxSize / height;
            width = (int) (width * ratio);
            height = (int) (height * ratio);
        }

        Log.d(TAG, "Adjusted resolution: " + width + "x" + height);
        return new Point(width, height);
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int targetWidth, int targetHeight) {
        if (bitmap == null || targetWidth <= 0 || targetHeight <= 0) {
            return createFallbackImage(targetWidth, targetHeight);
        }

        if (bitmap.getWidth() == targetWidth && bitmap.getHeight() == targetHeight) {
            return bitmap;
        }
        
        // Check if we have a cached version for this resolution
        String cacheKey = targetWidth + "x" + targetHeight;
        Bitmap cached = sBitmapCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            Log.d(TAG, "Using cached bitmap for resolution: " + cacheKey);
            return cached;
        }
        
        // Check if the cached resized bitmap matches
        if (sCachedResizedBitmap != null && !sCachedResizedBitmap.isRecycled() &&
            sCachedWidth == targetWidth && sCachedHeight == targetHeight) {
            Log.d(TAG, "Using pre-cached resized bitmap");
            return sCachedResizedBitmap;
        }

        // Create resized bitmap
        Bitmap resultBitmap = createResizedBitmapInternal(bitmap, targetWidth, targetHeight);
        
        // Update cache
        sCachedResizedBitmap = resultBitmap;
        sCachedWidth = targetWidth;
        sCachedHeight = targetHeight;
        sBitmapCache.put(cacheKey, resultBitmap);
        
        // Cache JPEG version asynchronously
        sBitmapExecutor.execute(() -> {
            if (!sJpegCache.containsKey(cacheKey)) {
                sJpegCache.put(cacheKey, bitmapToJpeg(resultBitmap));
            }
        });
        
        return resultBitmap;
    }
    
    /**
     * Internal method to create a resized bitmap
     */
    private static Bitmap createResizedBitmapInternal(Bitmap bitmap, int targetWidth, int targetHeight) {
        Bitmap resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(resultBitmap);

        float scaleX = (float) targetWidth / bitmap.getWidth();
        float scaleY = (float) targetHeight / bitmap.getHeight();

        // If FILL_IMAGE is true, we use max scale to fill the screen (cropping edges).
        // Otherwise, we use min scale to fit the image (showing black bars).
        // Both preserve aspect ratio (no stretching).
        float scale = FILL_IMAGE ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY);

        float scaledWidth = scale * bitmap.getWidth();
        float scaledHeight = scale * bitmap.getHeight();

        float left = (targetWidth - scaledWidth) / 2;
        float top = (targetHeight - scaledHeight) / 2;

        RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bitmap, null, targetRect, paint);

        return resultBitmap;
    }
    
    /**
     * Get cached JPEG data for a specific resolution, or null if not cached
     */
    public static byte[] getCachedJpegData(int width, int height) {
        String cacheKey = width + "x" + height;
        return sJpegCache.get(cacheKey);
    }

    private Bitmap createFallbackImage(int width, int height) {
        Bitmap fallback = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fallback);
        Paint paint = new Paint();
        paint.setColor(Color.GRAY);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(20);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("No Image", width / 2, height / 2, paint);
        return fallback;
    }

    /* ---------- Image Processing ---------- */
    private Bitmap applyImageTransformations(Bitmap bitmap) {
        if (!ROTATE_IMAGE && !FLIP_HORIZONTALLY && !RANDOMIZE_IMAGE) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        if (ROTATE_IMAGE && ROTATION_ANGLE != 0) {
            matrix.postRotate(ROTATION_ANGLE);
        }
        if (FLIP_HORIZONTALLY) {
            matrix.postScale(-1.0f, 1.0f);
        }
        Bitmap transformedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        if (RANDOMIZE_IMAGE) {
            transformedBitmap = randomizePicture(transformedBitmap);
        }

        return transformedBitmap;
    }

    private static byte[] bitmapToJpeg(Bitmap bitmap) {
        java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream);
        return stream.toByteArray();
    }

    private Bitmap randomizePicture(Bitmap bitmap) {
        if (!RANDOMIZE_IMAGE) {
            return bitmap;
        }

        float strength = ((float) RANDOMIZE_STRENGTH) / 100.0f;
        Log.d(TAG, "Randomizing picture with strength: " + strength);

        float angle = nextRandomFloat(-5.0f, 5.0f) * strength;

        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap randomizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        return randomizedBitmap;
    }

    private float nextRandomFloat(float min, float max) {
        return min + sRandom.nextFloat() * (max - min);
    }

    /* ---------- EXIF Handling ---------- */
    private void setGeneralExifAttributes(File file, int width, int height) {
        if (!ADD_EXIF_ATTRIBUTES) return;

        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());

            exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER);
            exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL);
            exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, String.valueOf(width));
            exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, String.valueOf(height));

            String dateTime = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(new Date());
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime);
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime);
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dateTime);

            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));

            // Additional attributes to make it look real
            // WHITE_BALANCE_AUTO is 0
            exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, "0");
            exif.setAttribute(ExifInterface.TAG_FLASH, "0");

            exif.saveAttributes();
            Log.d(TAG, "Successfully added EXIF attributes to: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error adding EXIF attributes", e);
        }
    }

    private void setLocationExifAttributes(File file) {
        // Since we don't have easy access to the internal SpoofLocation class without reflection,
        // we'll try to find it.
        try {
            Class<?> spoofLocationClass = Class.forName("com.applisto.appcloner.classes.secondary.SpoofLocation");
            Object instance = spoofLocationClass.getDeclaredField("INSTANCE").get(null);

            if (instance != null) {
                Method getLat = spoofLocationClass.getMethod("getSpoofLocationLatitude");
                Method getLon = spoofLocationClass.getMethod("getSpoofLocationLongitude");

                double lat = (double) getLat.invoke(null);
                double lon = (double) getLon.invoke(null);

                ExifInterface exif = new ExifInterface(file.getAbsolutePath());

                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDMS(lat));
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, lat > 0 ? "N" : "S");
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDMS(lon));
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lon > 0 ? "E" : "W");

                exif.saveAttributes();
                Log.i(TAG, "Added spoofed location EXIF: " + lat + ", " + lon);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set location EXIF (SpoofLocation not found or error)", e);
        }
    }

    private static String convertToDMS(double coordinate) {
        coordinate = Math.abs(coordinate);
        int degrees = (int) coordinate;
        coordinate = (coordinate - degrees) * 60;
        int minutes = (int) coordinate;
        coordinate = (coordinate - minutes) * 60;
        int seconds = (int) (coordinate * 1000);

        return degrees + "/1," + minutes + "/1," + seconds + "/1000";
    }

    /* ---------- Utility Methods ---------- */
    private Bitmap getCurrentFakeImage() {
        if (USE_RANDOM_IMAGE && !sFakeBitmaps.isEmpty()) {
            return sFakeBitmaps.get(sRandom.nextInt(sFakeBitmaps.size()));
        } else if (!sFakeBitmaps.isEmpty()) {
            Bitmap image = sFakeBitmaps.get(sCurrentImageIndex);
            sCurrentImageIndex = (sCurrentImageIndex + 1) % sFakeBitmaps.size();
            return image;
        } else {
            return sFakeBitmap;
        }
    }

    private void createFakeVideoFile() {
        Log.d(TAG, "Creating fake video file placeholder");
    }
}
