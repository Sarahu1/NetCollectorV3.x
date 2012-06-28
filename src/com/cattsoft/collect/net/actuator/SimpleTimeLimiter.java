/**
 * 
 */
package com.cattsoft.collect.net.actuator;

/*
 * Copyright (C) 2006 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A TimeLimiter that runs method calls in the background using an
 * {@link ExecutorService}. If the time limit expires for a given method call,
 * the thread running the call will be interrupted.
 * 
 * @author Kevin Bourrillion
 * @since 1.0
 */
public final class SimpleTimeLimiter {

	private final ExecutorService executor;

	/**
	 * Constructs a TimeLimiter instance using the given executor service to
	 * execute proxied method calls.
	 * <p>
	 * <b>Warning:</b> using a bounded executor may be counterproductive! If the
	 * thread pool fills up, any time callers spend waiting for a thread may
	 * count toward their time limit, and in this case the call may even time
	 * out before the target method is ever invoked.
	 * 
	 * @param executor
	 *            the ExecutorService that will execute the method calls on the
	 *            target objects; for example, a
	 *            {@link Executors#newCachedThreadPool()}.
	 */
	public SimpleTimeLimiter(ExecutorService executor) {
		this.executor = executor;
	}

	/**
	 * Constructs a TimeLimiter instance using a
	 * {@link Executors#newCachedThreadPool()} to execute proxied method calls.
	 * 
	 * <p>
	 * <b>Warning:</b> using a bounded executor may be counterproductive! If the
	 * thread pool fills up, any time callers spend waiting for a thread may
	 * count toward their time limit, and in this case the call may even time
	 * out before the target method is ever invoked.
	 */
	public SimpleTimeLimiter() {
		this(Executors.newCachedThreadPool());
	}

	public <T> T newProxy(final T target, Class<T> interfaceType,
			final long timeoutDuration, final TimeUnit timeoutUnit) {
		// checkNotNull(target);
		// checkNotNull(interfaceType);
		// checkNotNull(timeoutUnit);
		// checkArgument(timeoutDuration > 0, "bad timeout: " +
		// timeoutDuration);
		// checkArgument(interfaceType.isInterface(),
		// "interfaceType must be an interface type");

		final Set<Method> interruptibleMethods = findInterruptibleMethods(interfaceType);

		InvocationHandler handler = new InvocationHandler() {
			@Override
			public Object invoke(Object obj, final Method method,
					final Object[] args) throws Throwable {
				Callable<Object> callable = new Callable<Object>() {
					@Override
					public Object call() throws Exception {
						try {
							return method.invoke(target, args);
						} catch (InvocationTargetException e) {
							throw new AssertionError("can't get here");
						}
					}
				};
				return callWithTimeout(callable, timeoutDuration, timeoutUnit,
						interruptibleMethods.contains(method));
			}
		};
		return newProxy(interfaceType, handler);
	}

	public <T> T callWithTimeout(Future<T> future, long timeoutDuration,
			TimeUnit timeoutUnit) throws TimeoutException {
		try {
			return future.get(timeoutDuration, timeoutUnit);
		} catch (TimeoutException e) {
			future.cancel(true);
			throw e;
		} catch (Exception e) {
			future.cancel(true);
			throw new TimeoutException(e.getMessage());
		}
	}
	
	// TODO: should this actually throw only ExecutionException?
	public <T> T callWithTimeout(Callable<T> callable, long timeoutDuration,
			TimeUnit timeoutUnit, boolean amInterruptible) throws Exception {
		// checkNotNull(callable);
		// checkNotNull(timeoutUnit);
		// checkArgument(timeoutDuration > 0, "timeout must be positive: %s",
		// timeoutDuration);
		Future<T> future = executor.submit(callable);
		try {
			if (amInterruptible) {
				try {
					return future.get(timeoutDuration, timeoutUnit);
				} catch (InterruptedException e) {
					future.cancel(true);
					throw e;
				}
			} else {
				return getUninterruptibly(future, timeoutDuration, timeoutUnit);
			}
		} catch (ExecutionException e) {
			future.cancel(true);
			throw e;
			// throw throwCause(e, true);
		} catch (TimeoutException e) {
			future.cancel(true);
			throw e;
			// throw new UncheckedTimeoutException(e);
		}
	}

	private static Set<Method> findInterruptibleMethods(Class<?> interfaceType) {
		Set<Method> set = new HashSet<Method>();
		for (Method m : interfaceType.getMethods()) {
			if (declaresInterruptedEx(m)) {
				set.add(m);
			}
		}
		return set;
	}

	private static boolean declaresInterruptedEx(Method method) {
		for (Class<?> exType : method.getExceptionTypes()) {
			// debate: == or isAssignableFrom?
			if (exType == InterruptedException.class) {
				return true;
			}
		}
		return false;
	}

	// TODO: replace with version in common.reflect if and when it's
	// open-sourced
	private static <T> T newProxy(Class<T> interfaceType,
			InvocationHandler handler) {
		Object object = Proxy.newProxyInstance(interfaceType.getClassLoader(),
				new Class<?>[] { interfaceType }, handler);
		return interfaceType.cast(object);
	}

	public static <V> V getUninterruptibly(Future<V> future, long timeout,
			TimeUnit unit) throws ExecutionException, TimeoutException {
		boolean interrupted = false;
		try {
			long remainingNanos = unit.toNanos(timeout);
			long end = System.nanoTime() + remainingNanos;

			while (true) {
				try {
					// Future treats negative timeouts just like zero.
					return future.get(remainingNanos, NANOSECONDS);
				} catch (InterruptedException e) {
					interrupted = true;
					remainingNanos = end - System.nanoTime();
				}
			}
		} finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

}
