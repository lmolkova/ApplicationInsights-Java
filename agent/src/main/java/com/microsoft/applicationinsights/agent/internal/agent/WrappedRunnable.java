package com.microsoft.applicationinsights.agent.internal.agent;


public class WrappedRunnable implements Runnable
{
    private final Runnable task;

    private final Object caller;

    public WrappedRunnable(Runnable task/*, RequestTelemetryContext caller*/)
    {
        this.task = task;
        this.caller = ThreadContext.getCurrent();
    }

    @Override
    public void run()
    {
        ThreadContext.setCurrent(caller);
        task.run();
    }
}
