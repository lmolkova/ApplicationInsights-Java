package com.microsoft.applicationinsights.agent.internal.agent;

public class ThreadContext1 {
    public final String id;
    private static final ThreadLocal<ThreadContext1> ctx = new ThreadLocal<ThreadContext1>();
    public ThreadContext1(String id)
    {
    	this.id = id;
    	ThreadContext1.ctx.set(this);
    }
    
    public static ThreadContext1 getCurrent() {
    	return ctx.get();
    }
    
    public static void setCurrent(ThreadContext1 context) {
    	ThreadContext1.ctx.set(context);
    }
}
