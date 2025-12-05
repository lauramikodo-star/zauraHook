package com.applisto.appcloner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FakeCameraActivity extends Activity {
    private static final String TAG = "FakeCameraActivity";
    private static final int REQUEST_CODE_SELECT_PICTURE_FROM_STORAGE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Directly open picker, no permissions check for SAF
        onSelectPicture();
    }

    private void onSelectPicture() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_SELECT_PICTURE_FROM_STORAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult: " + requestCode + ", " + resultCode);

        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            handleSelectedImage(data.getData());
        }

        boolean isFakeCameraApp = getIntent().getBooleanExtra("fake_camera_app", false);
        if (isFakeCameraApp && resultCode == RESULT_OK) {
             FakeCameraHook.showNotification();
        } else if (!isFakeCameraApp && resultCode == RESULT_OK) {
             FakeCameraHook.showNotification();
        }

        finish();
    }

    private void handleSelectedImage(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();

            if (bitmap != null) {
                FakeCameraHook.setFakeBitmap(bitmap);
                Log.i(TAG, "Selected image set successfully: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                // Notify AppSupport that an image is selected, in case it's waiting
                FakeCameraAppSupport.notifyImageSelected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load image", e);
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }
}
