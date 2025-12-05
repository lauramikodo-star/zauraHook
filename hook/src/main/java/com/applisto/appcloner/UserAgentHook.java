package com.applisto.appcloner;

import android.content.Context;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URLConnection;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class UserAgentHook {

    private static final String TAG = "UserAgentHook";
    
    public void init(Context context) {
        try {
            String customUserAgent = ClonerSettings.get(context).userAgent();
            if (customUserAgent == null || customUserAgent.isEmpty()) {
                Log.w(TAG, "No custom user agent configured");
                return;
            }
            
            // Hook WebView user agent
            hookWebViewUserAgent(customUserAgent);
            
            // Hook HttpURLConnection user agent
            hookHttpURLConnection(customUserAgent);
            
            // Hook OkHttp if present
            hookOkHttp(customUserAgent);
            
            Log.i(TAG, "User-Agent hooks installed. Custom UA: " + customUserAgent);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install User-Agent hooks", t);
        }
    }
    
    private void hookWebViewUserAgent(String customUserAgent) {
        try {
            // Hook WebView constructor - use Constructor type instead of Method
            Constructor<WebView> webViewConstructor = WebView.class.getDeclaredConstructor(Context.class);
            XposedBridge.hookMethod(webViewConstructor, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) throws Throwable {
                    WebView webView = (WebView) param.thisObject;
                    WebSettings settings = webView.getSettings();
                    settings.setUserAgentString(customUserAgent);
                    Log.d(TAG, "Set WebView user agent");
                }
            });
            
            // Hook setUserAgentString to prevent apps from changing it
            Method setUserAgent = WebSettings.class.getDeclaredMethod("setUserAgentString", String.class);
            XposedBridge.hookMethod(setUserAgent, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = customUserAgent;
                    Log.d(TAG, "Intercepted setUserAgentString call");
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook WebView user agent", t);
        }
    }
    
    private void hookHttpURLConnection(String customUserAgent) {
        try {
            // Hook setRequestProperty for HttpURLConnection
            Method setRequestProperty = URLConnection.class.getDeclaredMethod(
                    "setRequestProperty", String.class, String.class);
            
            XposedBridge.hookMethod(setRequestProperty, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if ("User-Agent".equalsIgnoreCase((String) param.args[0])) {
                        param.args[1] = customUserAgent;
                        Log.d(TAG, "Intercepted HttpURLConnection User-Agent");
                    }
                }
            });
            
            // Hook addRequestProperty as well
            Method addRequestProperty = URLConnection.class.getDeclaredMethod(
                    "addRequestProperty", String.class, String.class);
            
            XposedBridge.hookMethod(addRequestProperty, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if ("User-Agent".equalsIgnoreCase((String) param.args[0])) {
                        param.args[1] = customUserAgent;
                        Log.d(TAG, "Intercepted HttpURLConnection addRequestProperty User-Agent");
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook HttpURLConnection", t);
        }
    }
    
    private void hookOkHttp(String customUserAgent) {
        try {
            // Try to hook OkHttp3 Request.Builder
            Class<?> requestBuilderClass = Class.forName("okhttp3.Request$Builder");
            Method addHeader = requestBuilderClass.getDeclaredMethod("addHeader", String.class, String.class);
            
            XposedBridge.hookMethod(addHeader, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if ("User-Agent".equalsIgnoreCase((String) param.args[0])) {
                        param.args[1] = customUserAgent;
                        Log.d(TAG, "Intercepted OkHttp User-Agent");
                    }
                }
            });
            
            // Also hook header() method
            Method header = requestBuilderClass.getDeclaredMethod("header", String.class, String.class);
            XposedBridge.hookMethod(header, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if ("User-Agent".equalsIgnoreCase((String) param.args[0])) {
                        param.args[1] = customUserAgent;
                        Log.d(TAG, "Intercepted OkHttp header() User-Agent");
                    }
                }
            });
            
            Log.i(TAG, "OkHttp hooks installed");
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "OkHttp not found in app, skipping OkHttp hooks");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook OkHttp", t);
        }
    }
}
