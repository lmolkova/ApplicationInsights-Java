package com.microsoft.applicationinsights.agent.internal.agent;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import com.microsoft.applicationinsights.agent.internal.logger.InternalAgentLogger;

public class ThreadPoolExecutorClassVisitor extends DefaultClassVisitor 
{

	public ThreadPoolExecutorClassVisitor(ClassInstrumentationData instrumentationData, ClassWriter classWriter) {
		super(instrumentationData, classWriter);
		// TODO Auto-generated constructor stub
	}
	
	
	 @Override
     public MethodVisitor visitMethod(int access,
             String name,
             String desc,
             String signature,
             String[] exceptions) {

         if (name.equals("execute")) {
             InternalAgentLogger.INSTANCE.logAlways(InternalAgentLogger.LoggingLevel.INFO, " visit exec " );
             return super.visitMethod(access, name, desc, signature, exceptions);
         }
         return super.visitMethod(access, name, desc, signature, exceptions);

     }
}
