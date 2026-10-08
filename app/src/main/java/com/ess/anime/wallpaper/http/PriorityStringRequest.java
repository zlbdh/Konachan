package com.ess.anime.wallpaper.http;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import androidx.annotation.Nullable;

public class PriorityStringRequest extends StringRequest {

    private Priority mPriority;
    private Map<String, String> mHeaderMap = new LinkedHashMap<>();

    public PriorityStringRequest(int method, String url, Response.Listener<String> listener, @Nullable Response.ErrorListener errorListener) {
        super(method, url, listener, errorListener);
        // VPN latency makes Volley's default 2.5-second timeout too short; increase it to 15 seconds
        setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 1, 1.0f));
    }

    public PriorityStringRequest(String url, Response.Listener<String> listener, @Nullable Response.ErrorListener errorListener) {
        super(url, listener, errorListener);
        setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 1, 1.0f));
    }

    @Override
    public Priority getPriority() {
        return mPriority;
    }

    public void setPriority(Priority priority) {
        mPriority = priority;
    }

    protected Response<String> parseNetworkResponse(NetworkResponse response) {
        // Prevent garbled Chinese text
        String parsed = new String(response.data, StandardCharsets.UTF_8);
        return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
    }

    public void addHeaders(Map<String, String> headerMap) {
        if (headerMap != null) {
            mHeaderMap.putAll(headerMap);
        }
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", OkHttp.USER_AGENT);
        headers.putAll(mHeaderMap);
        return headers;
    }

}
