package com.applisto.appcloner;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.media.Image;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utility class for image format conversions.
 * Provides methods to convert between Bitmap, NV21, YUV_420_888, RGBA_8888, and other formats.
 * 
 * Supported formats:
 * - ImageFormat.JPEG (256)
 * - ImageFormat.YUV_420_888 (35)
 * - ImageFormat.NV21 (17)
 * - PixelFormat.RGBA_8888 (1)
 * - PixelFormat.RGB_565 (4)
 * - ImageFormat.FLEX_RGB_888 (41)
 * - ImageFormat.FLEX_RGBA_8888 (42)
 */
public final class ImageUtils {
    private static final String TAG = "ImageUtils";
    
    // Common image format constants for reference
    public static final int FORMAT_RGBA_8888 = 1;      // PixelFormat.RGBA_8888
    public static final int FORMAT_RGBX_8888 = 2;      // PixelFormat.RGBX_8888
    public static final int FORMAT_RGB_888 = 3;        // PixelFormat.RGB_888
    public static final int FORMAT_RGB_565 = 4;        // PixelFormat.RGB_565
    public static final int FORMAT_NV21 = 17;          // ImageFormat.NV21
    public static final int FORMAT_NV16 = 16;          // ImageFormat.NV16
    public static final int FORMAT_YUY2 = 20;          // ImageFormat.YUY2
    public static final int FORMAT_YUV_420_888 = 35;   // ImageFormat.YUV_420_888
    public static final int FORMAT_YUV_422_888 = 39;   // ImageFormat.YUV_422_888
    public static final int FORMAT_YUV_444_888 = 40;   // ImageFormat.YUV_444_888
    public static final int FORMAT_FLEX_RGB_888 = 41;  // ImageFormat.FLEX_RGB_888
    public static final int FORMAT_FLEX_RGBA_8888 = 42; // ImageFormat.FLEX_RGBA_8888
    public static final int FORMAT_JPEG = 256;         // ImageFormat.JPEG

    private ImageUtils() {
        // Utility class, no instantiation
    }

    /**
     * Converts a Bitmap to NV21 format byte array.
     * NV21 is the standard Android camera preview format (YCrCb).
     *
     * @param bitmap The source bitmap (ARGB_8888 or RGB_565)
     * @return NV21 formatted byte array
     */
    public static byte[] bitmapToNV21(Bitmap bitmap) {
        if (bitmap == null) {
            Log.w(TAG, "bitmapToNV21: bitmap is null");
            return new byte[0];
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Get pixels from bitmap
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        return rgbToNV21(pixels, width, height);
    }

    /**
     * Converts RGB pixel array to NV21 format.
     *
     * @param argb   Array of ARGB pixels
     * @param width  Image width
     * @param height Image height
     * @return NV21 formatted byte array
     */
    public static byte[] rgbToNV21(int[] argb, int width, int height) {
        // NV21 size: Y plane (width * height) + UV plane (width * height / 2)
        int frameSize = width * height;
        int chromaSize = frameSize / 2;
        byte[] nv21 = new byte[frameSize + chromaSize];

        int yIndex = 0;
        int uvIndex = frameSize;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int pixel = argb[j * width + i];

                // Extract RGB components
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // Convert RGB to YUV (BT.601)
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;

                // Clamp values
                y = clamp(y, 16, 235);
                u = clamp(u, 16, 240);
                v = clamp(v, 16, 240);

                // Store Y value
                nv21[yIndex++] = (byte) y;

                // Store UV values (subsampled 2x2)
                // NV21 format: VU VU VU... (V first, then U)
                if (j % 2 == 0 && i % 2 == 0 && uvIndex < nv21.length - 1) {
                    nv21[uvIndex++] = (byte) v;
                    nv21[uvIndex++] = (byte) u;
                }
            }
        }

        return nv21;
    }

    /**
     * Writes YUV data (NV21 format) to Image.Plane array for YUV_420_888 format.
     *
     * @param yuvData NV21 formatted data
     * @param width   Image width
     * @param height  Image height
     * @param planes  Image.Plane array from the target Image
     */
    public static void writeYuvToPlanes(byte[] yuvData, int width, int height, Image.Plane[] planes) {
        if (yuvData == null || planes == null || planes.length < 3) {
            Log.w(TAG, "writeYuvToPlanes: invalid parameters");
            return;
        }

        int frameSize = width * height;

        try {
            // Y plane
            ByteBuffer yBuffer = planes[0].getBuffer();
            int yRowStride = planes[0].getRowStride();
            int yPixelStride = planes[0].getPixelStride();

            // U plane (Cb)
            ByteBuffer uBuffer = planes[1].getBuffer();
            int uRowStride = planes[1].getRowStride();
            int uPixelStride = planes[1].getPixelStride();

            // V plane (Cr)
            ByteBuffer vBuffer = planes[2].getBuffer();
            int vRowStride = planes[2].getRowStride();
            int vPixelStride = planes[2].getPixelStride();

            // Write Y plane
            writeYPlane(yuvData, yBuffer, width, height, yRowStride, yPixelStride);

            // Write UV planes from NV21 data
            // NV21 format: YYYYYYYY VUVUVU (V and U interleaved after Y)
            writeUVPlanesFromNV21(yuvData, frameSize, uBuffer, vBuffer,
                    width, height, uRowStride, uPixelStride, vRowStride, vPixelStride);

            Log.d(TAG, "Successfully wrote YUV data to planes");

        } catch (Exception e) {
            Log.e(TAG, "Error writing YUV data to planes", e);
        }
    }

    private static void writeYPlane(byte[] nv21, ByteBuffer yBuffer,
                                    int width, int height, int rowStride, int pixelStride) {
        yBuffer.rewind();

        if (rowStride == width && pixelStride == 1) {
            // Simple case: no padding, contiguous data
            int ySize = Math.min(width * height, yBuffer.remaining());
            yBuffer.put(nv21, 0, ySize);
        } else {
            // Handle row stride and pixel stride
            for (int row = 0; row < height; row++) {
                int srcOffset = row * width;
                for (int col = 0; col < width; col++) {
                    if (yBuffer.hasRemaining()) {
                        yBuffer.put(nv21[srcOffset + col]);
                    }
                }
                // Skip padding bytes at end of row
                int paddingBytes = rowStride - (width * pixelStride);
                for (int p = 0; p < paddingBytes && yBuffer.hasRemaining(); p++) {
                    yBuffer.put((byte) 0);
                }
            }
        }

        yBuffer.rewind();
    }

    private static void writeUVPlanesFromNV21(byte[] nv21, int ySize,
                                              ByteBuffer uBuffer, ByteBuffer vBuffer,
                                              int width, int height,
                                              int uRowStride, int uPixelStride,
                                              int vRowStride, int vPixelStride) {
        uBuffer.rewind();
        vBuffer.rewind();

        int uvHeight = height / 2;
        int uvWidth = width / 2;

        // NV21: V comes first, then U (interleaved)
        for (int row = 0; row < uvHeight; row++) {
            for (int col = 0; col < uvWidth; col++) {
                int nv21Index = ySize + (row * width) + (col * 2);

                if (nv21Index + 1 < nv21.length) {
                    byte v = nv21[nv21Index];     // V
                    byte u = nv21[nv21Index + 1]; // U

                    if (vBuffer.hasRemaining()) {
                        vBuffer.put(v);
                    }
                    if (uBuffer.hasRemaining()) {
                        uBuffer.put(u);
                    }

                    // Handle pixel stride > 1 (skip bytes between pixels)
                    for (int s = 1; s < uPixelStride && uBuffer.hasRemaining(); s++) {
                        uBuffer.put((byte) 0);
                    }
                    for (int s = 1; s < vPixelStride && vBuffer.hasRemaining(); s++) {
                        vBuffer.put((byte) 0);
                    }
                }
            }

            // Handle row stride (skip padding at end of row)
            int uPadding = uRowStride - (uvWidth * uPixelStride);
            int vPadding = vRowStride - (uvWidth * vPixelStride);

            for (int p = 0; p < uPadding && uBuffer.hasRemaining(); p++) {
                uBuffer.put((byte) 0);
            }
            for (int p = 0; p < vPadding && vBuffer.hasRemaining(); p++) {
                vBuffer.put((byte) 0);
            }
        }

        uBuffer.rewind();
        vBuffer.rewind();
    }

    /**
     * Converts a Bitmap to YUV_420_888 format byte array.
     * Similar to NV21 but with different UV plane arrangement.
     *
     * @param bitmap Source bitmap
     * @return YUV_420_888 formatted byte array (Y plane, then U plane, then V plane)
     */
    public static byte[] bitmapToYUV420(Bitmap bitmap) {
        if (bitmap == null) {
            return new byte[0];
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int frameSize = width * height;
        int chromaSize = frameSize / 4;

        byte[] yuv = new byte[frameSize + chromaSize * 2];

        int yIndex = 0;
        int uIndex = frameSize;
        int vIndex = frameSize + chromaSize;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int pixel = pixels[j * width + i];

                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // Convert RGB to YUV
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;

                yuv[yIndex++] = (byte) clamp(y, 16, 235);

                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uIndex++] = (byte) clamp(u, 16, 240);
                    yuv[vIndex++] = (byte) clamp(v, 16, 240);
                }
            }
        }

        return yuv;
    }

    /**
     * Clamps a value between min and max.
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Checks if the given image format is supported for overwriting.
     * 
     * @param format The image format code
     * @return true if the format is supported
     */
    public static boolean isFormatSupported(int format) {
        switch (format) {
            case FORMAT_JPEG:
            case FORMAT_YUV_420_888:
            case FORMAT_NV21:
            case FORMAT_RGBA_8888:
            case FORMAT_RGBX_8888:
            case FORMAT_RGB_888:
            case FORMAT_RGB_565:
            case FORMAT_FLEX_RGB_888:
            case FORMAT_FLEX_RGBA_8888:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Gets a human-readable name for an image format.
     * 
     * @param format The image format code
     * @return Human-readable format name
     */
    public static String getFormatName(int format) {
        switch (format) {
            case FORMAT_RGBA_8888: return "RGBA_8888";
            case FORMAT_RGBX_8888: return "RGBX_8888";
            case FORMAT_RGB_888: return "RGB_888";
            case FORMAT_RGB_565: return "RGB_565";
            case FORMAT_NV21: return "NV21";
            case FORMAT_NV16: return "NV16";
            case FORMAT_YUY2: return "YUY2";
            case FORMAT_YUV_420_888: return "YUV_420_888";
            case FORMAT_YUV_422_888: return "YUV_422_888";
            case FORMAT_YUV_444_888: return "YUV_444_888";
            case FORMAT_FLEX_RGB_888: return "FLEX_RGB_888";
            case FORMAT_FLEX_RGBA_8888: return "FLEX_RGBA_8888";
            case FORMAT_JPEG: return "JPEG";
            default: return "UNKNOWN(" + format + ")";
        }
    }
    
    /**
     * Converts a Bitmap to RGBA_8888 byte array.
     * 
     * @param bitmap The source bitmap
     * @return RGBA_8888 formatted byte array
     */
    public static byte[] bitmapToRGBA8888(Bitmap bitmap) {
        if (bitmap == null) {
            Log.w(TAG, "bitmapToRGBA8888: bitmap is null");
            return new byte[0];
        }
        
        // Ensure bitmap is in ARGB_8888 format
        Bitmap argbBitmap = bitmap;
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            argbBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        
        int width = argbBitmap.getWidth();
        int height = argbBitmap.getHeight();
        int[] pixels = new int[width * height];
        argbBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        
        // RGBA_8888: 4 bytes per pixel (R, G, B, A)
        byte[] rgba = new byte[width * height * 4];
        
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int offset = i * 4;
            
            // ARGB -> RGBA conversion
            rgba[offset] = (byte) ((pixel >> 16) & 0xFF);     // R
            rgba[offset + 1] = (byte) ((pixel >> 8) & 0xFF);  // G
            rgba[offset + 2] = (byte) (pixel & 0xFF);         // B
            rgba[offset + 3] = (byte) ((pixel >> 24) & 0xFF); // A
        }
        
        if (argbBitmap != bitmap) {
            argbBitmap.recycle();
        }
        
        return rgba;
    }
    
    /**
     * Converts a Bitmap to RGB_565 byte array.
     * 
     * @param bitmap The source bitmap
     * @return RGB_565 formatted byte array
     */
    public static byte[] bitmapToRGB565(Bitmap bitmap) {
        if (bitmap == null) {
            Log.w(TAG, "bitmapToRGB565: bitmap is null");
            return new byte[0];
        }
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // Convert to RGB_565 if needed
        Bitmap rgb565Bitmap = bitmap;
        if (bitmap.getConfig() != Bitmap.Config.RGB_565) {
            rgb565Bitmap = bitmap.copy(Bitmap.Config.RGB_565, false);
        }
        
        // RGB_565: 2 bytes per pixel
        ByteBuffer buffer = ByteBuffer.allocate(width * height * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        rgb565Bitmap.copyPixelsToBuffer(buffer);
        
        if (rgb565Bitmap != bitmap) {
            rgb565Bitmap.recycle();
        }
        
        return buffer.array();
    }
    
    /**
     * Writes RGBA data to an Image plane for RGBA_8888 format.
     * 
     * @param bitmap Source bitmap
     * @param planes Image.Plane array from the target Image
     * @param width  Image width
     * @param height Image height
     * @return true if successful
     */
    public static boolean writeRGBAToPlanes(Bitmap bitmap, Image.Plane[] planes, int width, int height) {
        if (bitmap == null || planes == null || planes.length == 0) {
            Log.w(TAG, "writeRGBAToPlanes: invalid parameters");
            return false;
        }
        
        try {
            // Resize bitmap if dimensions don't match
            Bitmap resizedBitmap = bitmap;
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            
            // Ensure ARGB_8888 format
            Bitmap argbBitmap = resizedBitmap;
            if (resizedBitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                argbBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, false);
            }
            
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int pixelStride = planes[0].getPixelStride();
            
            buffer.rewind();
            
            // Get pixels from bitmap
            int[] pixels = new int[width * height];
            argbBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            
            // Write pixels to buffer
            if (rowStride == width * 4 && pixelStride == 4) {
                // Simple case: contiguous buffer
                for (int pixel : pixels) {
                    if (buffer.remaining() >= 4) {
                        // ARGB -> RGBA
                        buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                        buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                        buffer.put((byte) (pixel & 0xFF));         // B
                        buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
                    }
                }
            } else {
                // Handle row stride and pixel stride
                for (int row = 0; row < height; row++) {
                    for (int col = 0; col < width; col++) {
                        if (buffer.remaining() >= pixelStride) {
                            int pixel = pixels[row * width + col];
                            buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                            buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                            buffer.put((byte) (pixel & 0xFF));         // B
                            buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
                            
                            // Skip extra bytes for pixel stride > 4
                            for (int s = 4; s < pixelStride && buffer.hasRemaining(); s++) {
                                buffer.put((byte) 0);
                            }
                        }
                    }
                    // Skip row padding
                    int rowPadding = rowStride - (width * pixelStride);
                    for (int p = 0; p < rowPadding && buffer.hasRemaining(); p++) {
                        buffer.put((byte) 0);
                    }
                }
            }
            
            buffer.rewind();
            
            // Cleanup
            if (argbBitmap != resizedBitmap) {
                argbBitmap.recycle();
            }
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
            
            Log.d(TAG, "Successfully wrote RGBA data to plane");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error writing RGBA data to planes", e);
            return false;
        }
    }
    
    /**
     * Writes RGB_565 data to an Image plane.
     * 
     * @param bitmap Source bitmap
     * @param planes Image.Plane array from the target Image
     * @param width  Image width
     * @param height Image height
     * @return true if successful
     */
    public static boolean writeRGB565ToPlanes(Bitmap bitmap, Image.Plane[] planes, int width, int height) {
        if (bitmap == null || planes == null || planes.length == 0) {
            Log.w(TAG, "writeRGB565ToPlanes: invalid parameters");
            return false;
        }
        
        try {
            // Resize bitmap if dimensions don't match
            Bitmap resizedBitmap = bitmap;
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            
            // Convert to RGB_565
            Bitmap rgb565Bitmap = resizedBitmap;
            if (resizedBitmap.getConfig() != Bitmap.Config.RGB_565) {
                rgb565Bitmap = resizedBitmap.copy(Bitmap.Config.RGB_565, false);
            }
            
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            
            buffer.rewind();
            
            // RGB_565: 2 bytes per pixel
            if (rowStride == width * 2) {
                // Simple case: contiguous buffer
                rgb565Bitmap.copyPixelsToBuffer(buffer);
            } else {
                // Handle row stride
                ByteBuffer rowBuffer = ByteBuffer.allocate(width * 2);
                for (int row = 0; row < height; row++) {
                    rowBuffer.rewind();
                    // Copy one row from bitmap
                    Bitmap rowBitmap = Bitmap.createBitmap(rgb565Bitmap, 0, row, width, 1);
                    rowBitmap.copyPixelsToBuffer(rowBuffer);
                    rowBitmap.recycle();
                    
                    // Write to main buffer
                    rowBuffer.rewind();
                    byte[] rowBytes = new byte[width * 2];
                    rowBuffer.get(rowBytes);
                    buffer.put(rowBytes);
                    
                    // Skip row padding
                    int rowPadding = rowStride - (width * 2);
                    for (int p = 0; p < rowPadding && buffer.hasRemaining(); p++) {
                        buffer.put((byte) 0);
                    }
                }
            }
            
            buffer.rewind();
            
            // Cleanup
            if (rgb565Bitmap != resizedBitmap) {
                rgb565Bitmap.recycle();
            }
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
            
            Log.d(TAG, "Successfully wrote RGB565 data to plane");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error writing RGB565 data to planes", e);
            return false;
        }
    }
    
    /**
     * Writes fake image data to an Image object based on its format.
     * This is the main entry point for overwriting camera images.
     * 
     * @param image  The target Image to overwrite
     * @param bitmap The source bitmap with fake image data
     * @return true if successful, false if format is unsupported or an error occurred
     */
    public static boolean writeFakeDataToImage(Image image, Bitmap bitmap) {
        if (image == null || bitmap == null) {
            Log.w(TAG, "writeFakeDataToImage: null parameters");
            return false;
        }
        
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        
        if (planes == null || planes.length == 0) {
            Log.e(TAG, "Image has no planes to overwrite");
            return false;
        }
        
        Log.d(TAG, "Attempting to write fake data to format: " + getFormatName(format) + 
                   " (" + width + "x" + height + ")");
        
        try {
            switch (format) {
                case FORMAT_JPEG:
                    return writeJpegToPlanes(bitmap, planes, width, height);
                    
                case FORMAT_YUV_420_888:
                case FORMAT_NV21:
                    return writeYuvToImage(bitmap, planes, width, height);
                    
                case FORMAT_RGBA_8888:
                case FORMAT_RGBX_8888:
                case FORMAT_FLEX_RGBA_8888:
                    return writeRGBAToPlanes(bitmap, planes, width, height);
                    
                case FORMAT_RGB_565:
                    return writeRGB565ToPlanes(bitmap, planes, width, height);
                    
                case FORMAT_RGB_888:
                case FORMAT_FLEX_RGB_888:
                    return writeRGB888ToPlanes(bitmap, planes, width, height);
                    
                default:
                    Log.w(TAG, "Unsupported image format: " + getFormatName(format));
                    // Try RGBA as fallback for unknown formats with single plane
                    if (planes.length == 1) {
                        Log.d(TAG, "Attempting RGBA fallback for unknown format");
                        return writeRGBAToPlanes(bitmap, planes, width, height);
                    }
                    return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing fake data to image", e);
            return false;
        }
    }
    
    /**
     * Writes JPEG data to an Image plane.
     * 
     * @param bitmap Source bitmap
     * @param planes Image.Plane array
     * @param width  Image width
     * @param height Image height
     * @return true if successful
     */
    private static boolean writeJpegToPlanes(Bitmap bitmap, Image.Plane[] planes, int width, int height) {
        try {
            // Resize bitmap if needed
            Bitmap resizedBitmap = bitmap;
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            
            // Compress to JPEG
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, baos);
            byte[] jpegData = baos.toByteArray();
            
            ByteBuffer buffer = planes[0].getBuffer();
            buffer.rewind();
            
            int bufferSize = buffer.remaining();
            if (jpegData.length > bufferSize) {
                buffer.put(jpegData, 0, bufferSize);
                Log.w(TAG, "JPEG data truncated to fit buffer");
            } else {
                buffer.put(jpegData);
            }
            
            buffer.rewind();
            
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
            
            Log.d(TAG, "Successfully wrote JPEG data to plane");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error writing JPEG data", e);
            return false;
        }
    }
    
    /**
     * Writes YUV data to an Image (wrapper for existing writeYuvToPlanes).
     */
    private static boolean writeYuvToImage(Bitmap bitmap, Image.Plane[] planes, int width, int height) {
        try {
            // Resize bitmap if needed
            Bitmap resizedBitmap = bitmap;
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            
            byte[] yuvData = bitmapToNV21(resizedBitmap);
            writeYuvToPlanes(yuvData, width, height, planes);
            
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error writing YUV data", e);
            return false;
        }
    }
    
    /**
     * Writes RGB_888 data to an Image plane.
     * 
     * @param bitmap Source bitmap
     * @param planes Image.Plane array
     * @param width  Image width
     * @param height Image height
     * @return true if successful
     */
    public static boolean writeRGB888ToPlanes(Bitmap bitmap, Image.Plane[] planes, int width, int height) {
        if (bitmap == null || planes == null || planes.length == 0) {
            Log.w(TAG, "writeRGB888ToPlanes: invalid parameters");
            return false;
        }
        
        try {
            // Resize bitmap if dimensions don't match
            Bitmap resizedBitmap = bitmap;
            if (bitmap.getWidth() != width || bitmap.getHeight() != height) {
                resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
            }
            
            // Ensure ARGB_8888 format for pixel access
            Bitmap argbBitmap = resizedBitmap;
            if (resizedBitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                argbBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, false);
            }
            
            ByteBuffer buffer = planes[0].getBuffer();
            int rowStride = planes[0].getRowStride();
            int pixelStride = planes[0].getPixelStride();
            
            buffer.rewind();
            
            // Get pixels from bitmap
            int[] pixels = new int[width * height];
            argbBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            
            // Write pixels to buffer (RGB_888: 3 bytes per pixel)
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    if (buffer.remaining() >= 3) {
                        int pixel = pixels[row * width + col];
                        buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                        buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                        buffer.put((byte) (pixel & 0xFF));         // B
                        
                        // Skip extra bytes for pixel stride > 3
                        for (int s = 3; s < pixelStride && buffer.hasRemaining(); s++) {
                            buffer.put((byte) 0);
                        }
                    }
                }
                // Skip row padding
                int rowPadding = rowStride - (width * pixelStride);
                for (int p = 0; p < rowPadding && buffer.hasRemaining(); p++) {
                    buffer.put((byte) 0);
                }
            }
            
            buffer.rewind();
            
            // Cleanup
            if (argbBitmap != resizedBitmap) {
                argbBitmap.recycle();
            }
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle();
            }
            
            Log.d(TAG, "Successfully wrote RGB888 data to plane");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error writing RGB888 data to planes", e);
            return false;
        }
    }
}
