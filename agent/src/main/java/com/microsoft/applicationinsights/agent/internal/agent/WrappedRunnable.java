/*package com.microsoft.applicationinsights.agent.internal.agent;

public class WrappedRunnable implements Runnable
{
   private final Runnable task;

   private final ThreadContext1 caller;

   public WrappedRunnable(Runnable task, ThreadContext1 caller)
   {
      this.task = task;
      this.caller = caller;
   }

   @Override
   public void run()
   {
	   ThreadContext1.setCurrent(caller);
	   task.run();
   }
}*/