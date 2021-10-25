package org.matsim.episim;

import com.google.inject.Module;
import com.google.inject.*;
import com.google.inject.internal.AbstractBindingBuilder;
import com.google.inject.internal.BindingBuilder;
import com.google.inject.internal.BindingImpl;
import com.google.inject.internal.SingletonScope;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.util.Modules;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * By design Guice does not allow child injectors to override bindings with a different scope:
 * "The reason overriding a binding in a child injector isn't supported is
 * because it can lead a developer towards writing code that can work in either a parent & child injector, but have different behavior in each."
 * (https://groups.google.com/g/google-guice/c/naqT-fOrOTw)
 * <p>
 * Unfortunately, that is exactly what is needed.
 * This class provides util method to create a copy of an injector to provide similar functionality.
 */
@SuppressWarnings("unchecked, rawtypes")
public class GuiceUtils {

	private static final Logger log = LogManager.getLogger(GuiceUtils.class);

	/**
	 * Create a injector similar to a "child injector".
	 * This new injector has the same singletons as the parent injectors, unless they have been overwritten.
	 * <p>
	 * Note: Accessing types in the child before they are bound in the parent may lead to unexpected behaviour.
	 *
	 * @param parent     the parent injector to copy
	 * @param modules    modules with overriding binding
	 * @param localScope binding that are not copied into this injector, but instead recreated in the local scope.
	 */
	public static Injector createCopiedInjector(Injector parent, Iterable<Module> modules, Class<?>... localScope) {
		return Guice.createInjector(Modules.override(new CopyModule(parent, localScope)).with(modules));
	}


	private static class CopyModule extends AbstractModule {
		private final Injector parent;
		private final List<String> localScope;

		private CopyModule(Injector parent, Class<?>[] localScope) {
			this.parent = parent;
			this.localScope = Arrays.stream(localScope).map(Class::getCanonicalName).collect(Collectors.toList());
		}

		@Override
		protected void configure() {

			binder().requireExplicitBindings();

			for (Map.Entry<Key<?>, Binding<?>> e : parent.getAllBindings().entrySet()) {

				Key key = e.getKey();

				// internal guice types are not bound
				String type = key.getTypeLiteral().toString();
				if (type.contains("com.google.inject") || type.contains("java.util.logging"))
					continue;

				Binding<?> binding = e.getValue();
				Boolean singleton = binding.acceptScopingVisitor(new IsSingleTonVisitor());

				if (singleton && !localScope.contains(type)) {
					Object instance = parent.getInstance(key);
					bind(key).toInstance(instance);
				} else {

					BindingBuilder binder = (BindingBuilder) bind(key);
					try {
						GuiceUtils.bind(binder, binding);
					} catch (ReflectiveOperationException exc) {
						throw new RuntimeException(exc);
					}
				}

			}
		}
	}

	/**
	 * Internal method to copy a binding.
	 */
	private static void bind(BindingBuilder binder, Binding binding) throws ReflectiveOperationException {
		Method method = AbstractBindingBuilder.class.getDeclaredMethod("setBinding", BindingImpl.class);
		method.setAccessible(true);
		method.invoke(binder, binding);
	}

	/**
	 * Returns true if scope is singleton.
	 */
	private static class IsSingleTonVisitor implements BindingScopingVisitor<Boolean> {
		@Override
		public Boolean visitEagerSingleton() {
			return true;
		}

		@Override
		public Boolean visitScope(Scope scope) {
			return scope instanceof SingletonScope;
		}

		@Override
		public Boolean visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation) {
			return scopeAnnotation == Singleton.class;
		}

		@Override
		public Boolean visitNoScoping() {
			return false;
		}
	}

	;

}
