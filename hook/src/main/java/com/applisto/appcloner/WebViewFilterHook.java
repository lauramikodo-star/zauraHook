package com.applisto.appcloner;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Intercepts every URL & POST body flowing through any WebView and
 *  applies user-defined regex rules. */
public final class WebViewFilterHook {
    private static final String TAG = "WebViewFilterHook";
    private static final String CFG_KEY = "url_data_filters";

    /* ------------------------------ PUBLIC ENTRY ------------------------- */
    public void init(Context ctx) {
        try {
            List<Rule> rules = loadRules(ctx);
            if (rules.isEmpty()) {
                Log.i(TAG, "No URL/data filter rules configured");
                return;
            }
            hookLoadUrl(rules);
            hookPostUrl(rules);
            hookWebViewClient(rules);
            Log.i(TAG, "WebView filter hook installed (" + rules.size() + " rules)");
        } catch (Exception e) {
            Log.e(TAG, "Filter hook init failed", e);
        }
    }

    /* ------------------------------ LOAD RULES -------------------------- */
    private List<Rule> loadRules(Context c) throws Exception {
        JSONArray arr = ClonerSettings.get(c).raw().optJSONArray(CFG_KEY);
        List<Rule> list = new ArrayList<>();
        if (arr == null) return list;

        for (int i = 0; i < arr.length(); i++)
            list.add(new Rule(arr.getJSONObject(i)));
        return list;
    }

    /* a single filter rule */
    private static final class Rule {
        final Pattern urlPat;
        final boolean urlBlock;
        final String  urlRepl;
        final boolean urlEncode;

        final Pattern dataPat;
        final int     dataFlags;
        final boolean dataBlock;
        final String  dataRepl;
        final boolean dataReplaceAll;

        Rule(JSONObject o) {
            /* URL part */
            urlPat   = regex(o, "url_regex", 0);
            urlBlock = o.optBoolean("url_block");
            urlRepl  = o.optString("url_replacement", null);
            urlEncode= o.optBoolean("url_encode");

            /* Data part */
            int flags = o.optBoolean("data_ignore_case") ? Pattern.CASE_INSENSITIVE : 0;
            dataPat   = regex(o, "data_regex", flags);
            dataFlags = flags;
            dataBlock = o.optBoolean("data_block");
            dataRepl  = o.optString("data_replacement", null);
            dataReplaceAll = o.optBoolean("data_replace_all", true);
        }
        private static Pattern regex(JSONObject o, String key, int flags) {
            String src = o.optString(key, null);
            return src == null || src.isEmpty() ? null : Pattern.compile(src, flags);
        }
    }

    /* ------------------------------ HOOKS ------------------------------- */
    private void hookLoadUrl(List<Rule> rules) throws Exception {
        Method m1 = WebView.class.getDeclaredMethod("loadUrl", String.class);
        Method m2 = WebView.class.getDeclaredMethod("loadUrl", String.class, Map.class);

        XC_MethodHook hook = new XC_MethodHook() {
            @Override public void beforeHookedMethod(MethodHookParam param) {
                String in = (String) param.args[0];
                for (Rule r : rules) {
                    if (r.urlPat != null && r.urlPat.matcher(in).find()) {
                        if (r.urlBlock) {
                            Log.d(TAG, "Blocked URL: " + in);
                            param.setResult(null);   // cancel load
                            return;
                        }
                        if (r.urlRepl != null) {
                            String out = r.urlPat.matcher(in)
                                    .replaceAll(r.urlRepl);
                            if (r.urlEncode) out = Uri.encode(out);
                            Log.d(TAG, "Rewrote URL: " + in + " -> " + out);
                            param.args[0] = out;
                            in = out;  // keep testing next rules on new URL
                        }
                    }
                }
            }
        };
        XposedBridge.hookMethod(m1, hook);
        XposedBridge.hookMethod(m2, hook);
    }

    private void hookPostUrl(List<Rule> rules) throws Exception {
        Method post = WebView.class.getDeclaredMethod("postUrl", String.class, byte[].class);
        XposedBridge.hookMethod(post, new XC_MethodHook() {
            @Override public void beforeHookedMethod(MethodHookParam param) {
                /* 1. Treat URL part exactly like loadUrl */
                String url = (String) param.args[0];
                for (Rule r : rules) {
                    if (r.urlPat != null && r.urlPat.matcher(url).find()) {
                        if (r.urlBlock) { param.setResult(null); return; }
                        if (r.urlRepl != null) {
                            String newUrl = r.urlPat.matcher(url).replaceAll(r.urlRepl);
                            if (r.urlEncode) newUrl = Uri.encode(newUrl);
                            param.args[0] = url = newUrl;
                        }
                    }
                }
                /* 2. Work on POST body */
                byte[] bodyBytes = (byte[]) param.args[1];
                String body = new String(bodyBytes, StandardCharsets.UTF_8);

                for (Rule r : rules) {
                    if (r.dataPat != null && r.dataPat.matcher(body).find()) {
                        if (r.dataBlock) { param.setResult(null); return; }
                        if (r.dataRepl != null) {
                            body = r.dataReplaceAll
                                   ? r.dataPat.matcher(body).replaceAll(r.dataRepl)
                                   : r.dataPat.matcher(body).replaceFirst(r.dataRepl);
                        }
                    }
                }
                param.args[1] = body.getBytes(StandardCharsets.UTF_8);
            }
        });
    }

    /* shouldInterceptRequest gives us every sub-resource, response can be
       replaced with empty stream to BLOCK. */
    private void hookWebViewClient(List<Rule> rules) throws Exception {
        /* We need to wrap whatever WebViewClient the app installs */
        Method setClient = WebView.class.getDeclaredMethod(
                "setWebViewClient", WebViewClient.class);

        XposedBridge.hookMethod(setClient, new XC_MethodHook() {
            @Override public void beforeHookedMethod(MethodHookParam param) {
                WebViewClient orig = (WebViewClient) param.args[0];
                param.args[0] = new FilteringClient(orig, rules);
            }
        });

        /* Also patch already-created WebViews (constructor) */
        Constructor<WebView> ctor = WebView.class.getDeclaredConstructor(Context.class);
        XposedBridge.hookMethod(ctor, new XC_MethodHook() {
            @Override public void afterHookedMethod(MethodHookParam param) {
                WebView vw = (WebView) param.thisObject;
                vw.setWebViewClient(new FilteringClient(null, rules));
            }
        });
    }

    /* -------------------------------------------------------------------- */
    private static final class FilteringClient extends WebViewClient {
        private final WebViewClient orig;
        private final List<Rule> rules;
        FilteringClient(WebViewClient o, List<Rule> r) { orig = o; rules = r; }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
            String url = req.getUrl().toString();

            /* ---- URL part ------------------------------------------------ */
            for (Rule r : rules) {
                if (r.urlPat != null && r.urlPat.matcher(url).find()) {
                    if (r.urlBlock) {
                        Log.d(TAG, "Blocked sub-resource: " + url);
                        return empty();
                    }
                    if (r.urlRepl != null) {
                        String newUrl = r.urlPat.matcher(url).replaceAll(r.urlRepl);
                        if (r.urlEncode) newUrl = Uri.encode(newUrl);
                        try {
                            return super.shouldInterceptRequest(v,
                                    new WrappedRequest(req, Uri.parse(newUrl)));
                        } catch (Throwable t) { /* fall through */ }
                    }
                }
            }
            return (orig != null)
                    ? orig.shouldInterceptRequest(v, req)
                    : super.shouldInterceptRequest(v, req);
        }

        /* empty 204 */
        private static WebResourceResponse empty() {
            return new WebResourceResponse("text/plain", "utf-8",
                    204, "No Content", null,
                    new ByteArrayInputStream(new byte[0]));
        }
    }

    /* Small wrapper to present a modified URI to shouldInterceptRequest */
    private static final class WrappedRequest implements WebResourceRequest {
        private final WebResourceRequest base;
        private final Uri newUri;
        WrappedRequest(WebResourceRequest b, Uri u) { base = b; newUri = u; }
        @Override public Uri getUrl() { return newUri; }
        @Override public boolean isForMainFrame() { return base.isForMainFrame(); }
        @Override public boolean isRedirect() { return Build.VERSION.SDK_INT >= 24 && base.isRedirect(); }
        @Override public boolean hasGesture() { return base.hasGesture(); }
        @Override public String getMethod() { return base.getMethod(); }
        @Override public Map<String, String> getRequestHeaders() { return base.getRequestHeaders(); }
    }
}
