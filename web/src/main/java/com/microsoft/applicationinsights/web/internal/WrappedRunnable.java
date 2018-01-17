package com.microsoft.applicationinsights.web.internal;

public class WrappedRunnable implements Runnable
{
   private final Runnable task;

   private final RequestTelemetryContext caller;

   public WrappedRunnable(Runnable task/*, RequestTelemetryContext caller*/)
   {
      this.task = task;
      this.caller = ThreadContext.getRequestTelemetryContext();
   }

   @Override
   public void run()
   {
	   ThreadContext.setRequestTelemetryContext(caller);
	   task.run();
   }
}