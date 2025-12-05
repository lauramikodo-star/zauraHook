package com.applisto.appcloner;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public abstract class ExecStartActivityHook {
    private static final String TAG = "ExecStartActivityHook";
    private static final List<ExecStartActivityHook> sHooks = new ArrayList<>();
    private static boolean sInstalled;

    public static class ExecStartActivityArgs {
        public Context who;
        public IBinder contextThread;
        public IBinder token;
        public Activity target;
        public Intent intent;
        public int requestCode;
        public Bundle options;
    }

    public void install(Context context) {
        Log.i(TAG, "install; ");
        if (!sInstalled) {
            try {
                // Hook Instrumentation.execStartActivity
                // Note: There are multiple signatures for execStartActivity. We should hook the one used by Activity.startActivityForResult.

                // public ActivityResult execStartActivity(
                //    Context who, IBinder contextThread, IBinder token, Activity target,
                //    Intent intent, int requestCode, Bundle options)

                Class<?> instrumentationClass = Instrumentation.class;
                Method execStartActivityMethod = instrumentationClass.getMethod("execStartActivity",
                    Context.class, IBinder.class, IBinder.class, Activity.class,
                    Intent.class, int.class, Bundle.class);

                XposedBridge.hookMethod(execStartActivityMethod, new XC_MethodHook() {
                    @Override
                    public void beforeHookedMethod(MethodHookParam param) {
                        try {
                            ExecStartActivityArgs args = new ExecStartActivityArgs();
                            args.who = (Context) param.args[0];
                            args.contextThread = (IBinder) param.args[1];
                            args.token = (IBinder) param.args[2];
                            args.target = (Activity) param.args[3];
                            args.intent = (Intent) param.args[4];
                            args.requestCode = (Integer) param.args[5];
                            args.options = (Bundle) param.args[6];

                            Log.i(TAG, "execStartActivity; intent: " + args.intent);

                            for (ExecStartActivityHook hook : sHooks) {
                                if (!hook.onExecStartActivity(args)) {
                                    // If hook returns false, suppress the call?
                                    // Based on the Smali, if onExecStartActivity returns false, it returns null (suppressing the original call).
                                    // In Pine, we use setResult(null) to suppress the call if we want, or just return.
                                    // But here we might want to return a dummy ActivityResult or just null.
                                    param.setResult(null);
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error in ExecStartActivityHook", e);
                        }
                    }
                });

                sInstalled = true;
            } catch (Exception e) {
                Log.w(TAG, "Failed to hook execStartActivity", e);
            }
        }
        sHooks.add(this);
        Log.i(TAG, "install; installed ExecStartActivityHook: " + this.getClass());
    }

    protected abstract boolean onExecStartActivity(ExecStartActivityArgs args) throws ActivityNotFoundException;
}
