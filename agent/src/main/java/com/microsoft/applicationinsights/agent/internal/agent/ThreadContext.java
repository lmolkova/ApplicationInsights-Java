package com.microsoft.applicationinsights.agent.internal.agent;

public class ThreadContext {
//    public final Object internal;
    private static final ThreadLocal<Object> ctx = new ThreadLocal<>();
    public ThreadContext(Object internal)
    {
    	//this.internal = internal;
        ctx.set(internal);
    }
    
    public static Object getCurrent() {
    	return ctx.get();
    }
    
    public static void setCurrent(Object context) {
        ctx.set(context);
    }

    public static void remove() {
        ctx.remove();
    }

}
