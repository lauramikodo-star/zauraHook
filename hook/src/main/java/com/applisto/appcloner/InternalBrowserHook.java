package com.applisto.appcloner;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class InternalBrowserHook extends ExecStartActivityHook {
    private static final String TAG = "InternalBrowserHook";
    private final Context mContext;
    private boolean mEnabled;

    public InternalBrowserHook(Context context) {
        mContext = context;
    }

    public void init() {
        ClonerSettings settings = ClonerSettings.get(mContext);
        mEnabled = settings.internalBrowserEnabled();
        if (mEnabled) {
            install(mContext);
            Log.i(TAG, "InternalBrowserHook installed");
        }
    }

    @Override
    protected boolean onExecStartActivity(ExecStartActivityArgs args) throws ActivityNotFoundException {
        if (!mEnabled) {
            return true;
        }

        Intent intent = args.intent;
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                String scheme = data.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    Log.i(TAG, "Intercepting URL: " + data);

                    Intent browserIntent = new Intent(mContext, InternalBrowserActivity.class);
                    browserIntent.setData(data);
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    // Copy extras?
                    if (intent.getExtras() != null) {
                        browserIntent.putExtras(intent.getExtras());
                    }

                    try {
                        mContext.startActivity(browserIntent);
                        return false; // Suppress original call
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start InternalBrowserActivity", e);
                    }
                }
            }
        }

        return true;
    }
}
