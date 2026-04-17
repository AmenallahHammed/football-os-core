package com.fos.sdk.events;

public final class RequestContext {
    private static final ThreadLocal<String> correlationId = new InheritableThreadLocal<>();

    public static void set(String id)  { correlationId.set(id); }
    public static String get()         { return correlationId.get() != null ? correlationId.get() : "no-correlation-id"; }
    public static void clear()         { correlationId.remove(); }

    private RequestContext() {}
}
