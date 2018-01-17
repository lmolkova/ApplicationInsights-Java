package com.microsoft.applicationinsights.agent.internal.agent;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.microsoft.applicationinsights.agent.internal.logger.InternalAgentLogger;

/*public class ThreadPoolExecutorMethodVisitor extends MethodVisitor implements Opcodes {

	public final static String CLASS_NAME = "http_test/MyThreadPoolExec";//"java/util/concurrent/ThreadPoolExecutor";// ;
	protected final static String ON_ENTER_METHOD_NAME = "execute";
	protected final static String ON_ENTER_METHOD_SIGNATURE = "(Ljava/lang/Runnable;)V";
	protected final MethodVisitor originalMv;

	public ThreadPoolExecutorMethodVisitor(int access, String desc, String owner, String methodName,
			MethodVisitor methodVisitor, ClassToMethodTransformationData additionalData) {
		super(ASM5);
		this.originalMv = methodVisitor;
	}

	@Override
	public void visitCode() {
		originalMv.visitCode();
		Label lm1 = new Label();
		originalMv.visitLabel(lm1);
		originalMv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
		originalMv.visitLdcInsn("GOTit");
		originalMv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");

		Label l0 = new Label();
		originalMv.visitLabel(l0);
		originalMv.visitTypeInsn(NEW, "com/microsoft/applicationinsights/agent/internal/agent/WrappedRunnable");
		originalMv.visitInsn(DUP);
		originalMv.visitVarInsn(ALOAD, 1);

		originalMv.visitMethodInsn(INVOKESTATIC, "com/microsoft/applicationinsights/agent/internal/agent/ThreadContext1", "getCurrent", "()Lcom/microsoft/applicationinsights/agent/internal/agent/ThreadContext1;");
		
		//com.microsoft.applicationinsights.agent.internal.agent
		originalMv.visitMethodInsn(INVOKESPECIAL, "com/microsoft/applicationinsights/agent/internal/agent/WrappedRunnable", "<init>",
				"(Ljava/lang/Runnable;Lcom/microsoft/applicationinsights/agent/internal/agent/ThreadContext1;)V");
		originalMv.visitVarInsn(ASTORE, 2);

		Label l1 = new Label();
		originalMv.visitLabel(l1);

		originalMv.visitVarInsn(ALOAD, 0);
		originalMv.visitVarInsn(ALOAD, 2);
		super.visitMethodInsn(INVOKEVIRTUAL, CLASS_NAME, "execute", "(Ljava/lang/Runnable;)V", false);

		Label l2 = new Label();
		originalMv.visitLabel(l2);
		originalMv.visitInsn(RETURN);

		Label l3 = new Label();
		originalMv.visitLabel(l3);

		originalMv.visitMaxs(4, 3);
		originalMv.visitEnd();

	}
}*/

public class ThreadPoolExecutorMethodVisitor extends AdvancedAdviceAdapter  {
	public final String className;// = "java/util/concurrent/ThreadPoolExecutor";	//"http_test/MyThreadPoolExec";//
    protected final static String ON_ENTER_METHOD_NAME = "execute";
    protected final static String ON_ENTER_METHOD_SIGNATURE = "(Ljava/lang/Runnable;)V";

    	public ThreadPoolExecutorMethodVisitor(
    			String className,
    			int access,
            String desc,
            String methodName,
            MethodVisitor methodVisitor,
            ClassToMethodTransformationData additionalData) {
			super(false, ASM5, methodVisitor, access, className, methodName, desc);
			this.className = className;
    }

	@Override
	protected void onMethodEnter() {
/*		Label lm1 = new Label();
		mv.visitLabel(lm1);
		mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
		mv.visitLdcInsn("GOTit");
		mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");*/

		Label l0 = new Label();
		mv.visitLabel(l0);
		mv.visitTypeInsn(NEW, "com/microsoft/applicationinsights/agent/internal/agent/WrappedRunnable");
		mv.visitInsn(DUP);
		mv.visitVarInsn(ALOAD, 1);

		mv.visitMethodInsn(INVOKESTATIC, "com/microsoft/applicationinsights/agent/internal/agent/ThreadContext1", "getCurrent", "()Lcom/microsoft/applicationinsights/agent/internal/agent/ThreadContext1;");
		
		mv.visitMethodInsn(INVOKESPECIAL, "com/microsoft/applicationinsights/agent/internal/agent/WrappedRunnable", "<init>",
				"(Ljava/lang/Runnable;Lcom/microsoft/applicationinsights/agent/internal/agent/ThreadContext1;)V");
		mv.visitVarInsn(ASTORE, 1);
	}
	
	@Override
	protected void onMethodExit(int opcode) {
	}
	
    @Override
    protected void byteCodeForMethodExit(int opcode) {
    }
}
