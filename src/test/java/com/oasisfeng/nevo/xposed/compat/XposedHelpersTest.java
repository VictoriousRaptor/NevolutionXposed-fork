package com.oasisfeng.nevo.xposed.compat;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class XposedHelpersTest {
	@Test public void exactLookupFindsInheritedMethod() {
		Method method = XposedHelpers.findMethodExact(Child.class, "inherited", String.class);

		assertEquals(Parent.class, method.getDeclaringClass());
	}

	@Test public void bestMatchHandlesBoxingAndInheritance() {
		Method primitive = XposedHelpers.findMethodBestMatch(Child.class, "number", Integer.class);
		Method inherited = XposedHelpers.findMethodBestMatch(Child.class, "inherited", String.class);

		assertEquals(int.class, primitive.getParameterTypes()[0]);
		assertEquals(Parent.class, inherited.getDeclaringClass());
	}

	@Test public void additionalFieldsAreIsolatedAndRemovable() {
		Object first = new Object();
		Object second = new Object();

		XposedHelpers.setAdditionalInstanceField(first, "key", "value");
		assertEquals("value", XposedHelpers.getAdditionalInstanceField(first, "key"));
		assertEquals(null, XposedHelpers.getAdditionalInstanceField(second, "key"));
		assertEquals("value", XposedHelpers.setAdditionalInstanceField(first, "key", null));
		assertEquals(null, XposedHelpers.getAdditionalInstanceField(first, "key"));
	}

	@Test public void additionalFieldsRemainIsolatedAcrossNotificationThreads() throws Exception {
		ExecutorService threads = Executors.newFixedThreadPool(8);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<?>> tasks = new ArrayList<>();
		try {
			for (int thread = 0; thread < 8; thread++) {
				final int value = thread;
				tasks.add(threads.submit(() -> {
					start.await();
					List<Object> receivers = new ArrayList<>();
					for (int i = 0; i < 200; i++) {
						Object receiver = new Object();
						receivers.add(receiver);
						XposedHelpers.setAdditionalInstanceField(receiver, "thread", value);
						assertEquals(value, XposedHelpers.getAdditionalInstanceField(receiver, "thread"));
					}
					for (Object receiver : receivers)
						assertEquals(value, XposedHelpers.getAdditionalInstanceField(receiver, "thread"));
					return null;
				}));
			}
			start.countDown();
			for (Future<?> task : tasks) task.get(30, TimeUnit.SECONDS);
		} finally {
			threads.shutdownNow();
		}
	}

	private static class Parent {
		@SuppressWarnings("unused") private void inherited(String value) {}
	}

	private static final class Child extends Parent {
		@SuppressWarnings("unused") private void number(int value) {}
	}
}
