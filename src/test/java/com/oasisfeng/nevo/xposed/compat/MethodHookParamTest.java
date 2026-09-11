package com.oasisfeng.nevo.xposed.compat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MethodHookParamTest {
	@Test public void callbackResultSkipsOriginalInvocation() {
		XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();

		param.setResult("replacement");

		assertTrue(param.shouldReturnEarly());
		assertFalse(param.hasThrowable());
		assertEquals("replacement", param.getResult());
	}

	@Test public void callbackThrowableSkipsOriginalInvocation() {
		XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
		IllegalStateException failure = new IllegalStateException("failed");

		param.setThrowable(failure);

		assertTrue(param.shouldReturnEarly());
		assertTrue(param.hasThrowable());
		assertSame(failure, param.getThrowable());
	}

	@Test public void originalOutcomeDoesNotMarkEarlyReturn() {
		XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();

		param.setResultFromOriginal("original");

		assertFalse(param.shouldReturnEarly());
		assertEquals("original", param.getResult());
	}
}
