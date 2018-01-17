/*
 * AppInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.agent.internal.agent;

import com.microsoft.applicationinsights.agent.internal.coresync.InstrumentedClassType;
import com.microsoft.applicationinsights.agent.internal.logger.InternalAgentLogger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import java.util.Map;

public class ThreadPoolExecutorProvider {

	private final static String THREAD_POOL_CLASS_NAME = "java/util/concurrent/ThreadPoolExecutor";
	private final static String FORK_JOIN_POOL_CLASS_NAME = "java/util/concurrent/ForkJoinPool";
    private final Map<String, ClassInstrumentationData> classesToInstrument;

    public ThreadPoolExecutorProvider(Map<String, ClassInstrumentationData> classesToInstrument) {
        this.classesToInstrument = classesToInstrument;
    }

    public void add() {
        try {
          
            ClassInstrumentationData threadPoolData =
            		// CLASS VISITOR FACTORY
                    new ClassInstrumentationData(THREAD_POOL_CLASS_NAME, InstrumentedClassType.OTHER)
                    	.setReportCaughtExceptions(false)
                        .setReportExecutionTime(true);
                            
            MethodVisitorFactory threadPoolMethodVisitorFactory = new MethodVisitorFactory() {
                @Override
                public MethodVisitor create(MethodInstrumentationDecision decision, int access, String desc, String owner, String methodName, MethodVisitor methodVisitor, ClassToMethodTransformationData additionalData) {
                	ThreadPoolExecutorMethodVisitor visitor = new ThreadPoolExecutorMethodVisitor( THREAD_POOL_CLASS_NAME, access, desc, methodName, methodVisitor, additionalData);
                    return visitor;
                }
            };

            ClassInstrumentationData forkJoinData =
            		// CLASS VISITOR FACTORY
                    new ClassInstrumentationData(FORK_JOIN_POOL_CLASS_NAME, InstrumentedClassType.OTHER)
                    	.setReportCaughtExceptions(false)
                        .setReportExecutionTime(true);
                            
            MethodVisitorFactory forkJoinMethodVisitorFactory = new MethodVisitorFactory() {
                @Override
                public MethodVisitor create(MethodInstrumentationDecision decision, int access, String desc, String owner, String methodName, MethodVisitor methodVisitor, ClassToMethodTransformationData additionalData) {
                	ThreadPoolExecutorMethodVisitor visitor = new ThreadPoolExecutorMethodVisitor( FORK_JOIN_POOL_CLASS_NAME, access, desc, methodName, methodVisitor, additionalData);
                    return visitor;
                }
            };
            
            threadPoolData.addMethod(ThreadPoolExecutorMethodVisitor.ON_ENTER_METHOD_NAME, ThreadPoolExecutorMethodVisitor.ON_ENTER_METHOD_SIGNATURE, false, true, 0, threadPoolMethodVisitorFactory);
            forkJoinData.addMethod(ThreadPoolExecutorMethodVisitor.ON_ENTER_METHOD_NAME, ThreadPoolExecutorMethodVisitor.ON_ENTER_METHOD_SIGNATURE, false, true, 0, forkJoinMethodVisitorFactory);
            classesToInstrument.put(THREAD_POOL_CLASS_NAME, threadPoolData);
            classesToInstrument.put(FORK_JOIN_POOL_CLASS_NAME, forkJoinData);
        } catch (Throwable t) {
            InternalAgentLogger.INSTANCE.logAlways(InternalAgentLogger.LoggingLevel.ERROR, "Failed to load instrumentation for thread pool: '%s':'%s'", t.getClass().getName(), t.getMessage());
        }
    }

}