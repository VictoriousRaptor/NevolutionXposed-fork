package com.oasisfeng.nevo.xposed.compat;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;

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

	private static class Parent {
		@SuppressWarnings("unused") private void inherited(String value) {}
	}

	private static final class Child extends Parent {
		@SuppressWarnings("unused") private void number(int value) {}
	}
}
