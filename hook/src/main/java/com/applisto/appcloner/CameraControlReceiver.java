package com.applisto.appcloner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class CameraControlReceiver extends BroadcastReceiver {
    private static final String TAG = "CameraControlReceiver";

    public static final String ACTION_ROTATE_CLOCKWISE = "com.applisto.appcloner.ACTION_ROTATE_CLOCKWISE";
    public static final String ACTION_ROTATE_COUNTERCLOCKWISE = "com.applisto.appcloner.ACTION_ROTATE_COUNTERCLOCKWISE";
    public static final String ACTION_FLIP_HORIZONTALLY = "com.applisto.appcloner.ACTION_FLIP_HORIZONTALLY";
    public static final String ACTION_TOGGLE_SCALE = "com.applisto.appcloner.ACTION_TOGGLE_SCALE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "Received action: " + action);

        if (ACTION_ROTATE_CLOCKWISE.equals(action)) {
            FakeCameraHook.performRotate(true);
        } else if (ACTION_ROTATE_COUNTERCLOCKWISE.equals(action)) {
            FakeCameraHook.performRotate(false);
        } else if (ACTION_FLIP_HORIZONTALLY.equals(action)) {
            FakeCameraHook.performFlip();
        } else if (ACTION_TOGGLE_SCALE.equals(action)) {
            FakeCameraHook.toggleScale();
        }
    }
}
