package com.example.streamwindow;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/** Pure policy: GET only; two automatic retries; never shorten Retry-After. */
final class RetryPolicy {
    static final long MAX_AUTOMATIC_DELAY = 120000L;
    static boolean eligible(String method, int status) {
        return "GET".equalsIgnoreCase(method) &&
            (status == 503 || status == 502 || status == 504 || status == 429);
    }
    static long retryAfter(String value, long now) {
        if (value == null) return 0;
        value = value.trim();
        if (value.matches("[0-9]+")) {
            try { long seconds = Long.parseLong(value); return seconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : seconds * 1000L; }
            catch (NumberFormatException e) { return Long.MAX_VALUE; }
        }
        try {
            SimpleDateFormat date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            date.setTimeZone(TimeZone.getTimeZone("GMT")); date.setLenient(false);
            return Math.max(0, date.parse(value).getTime() - now);
        } catch (Exception e) { return 0; }
    }
    static long delay(int retriesAlreadyMade, String value, long now) {
        return Math.max(2000L << Math.min(retriesAlreadyMade, 1), retryAfter(value, now));
    }
    static boolean automatic(int retriesAlreadyMade, long delay) {
        return retriesAlreadyMade < 2 && delay <= MAX_AUTOMATIC_DELAY;
    }
}
