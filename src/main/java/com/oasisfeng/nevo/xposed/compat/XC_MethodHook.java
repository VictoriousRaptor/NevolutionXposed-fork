package com.oasisfeng.nevo.xposed.compat;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {
	protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
	protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

	public static final class MethodHookParam {
		public Member method;
		public Object thisObject;
		public Object[] args;

		private Object result;
		private Throwable throwable;
		private boolean returnEarly;

		public Object getResult() { return result; }

		public void setResult(Object result) {
			this.result = result;
			this.throwable = null;
			this.returnEarly = true;
		}

		public Throwable getThrowable() { return throwable; }

		public boolean hasThrowable() { return throwable != null; }

		public void setThrowable(Throwable throwable) {
			this.throwable = throwable;
			this.result = null;
			this.returnEarly = true;
		}

		boolean shouldReturnEarly() { return returnEarly; }

		void setResultFromOriginal(Object result) {
			this.result = result;
			this.throwable = null;
		}

		void setThrowableFromOriginal(Throwable throwable) {
			this.throwable = throwable;
			this.result = null;
		}
	}
}
