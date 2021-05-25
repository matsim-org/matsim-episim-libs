package org.matsim.episim;

import com.google.inject.Module;
import com.google.inject.*;
import org.junit.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.matsim.episim.GuiceUtils.createCopiedInjector;

public class GuiceUtilsTest {

	@Test
	public void copiedInjector() {


		Module parent = new AbstractModule() {
			@Override
			protected void configure() {
				bind(Integer.class).toProvider(() -> 1).in(Singleton.class);
				bind(SplittableRandom.class).toProvider(() -> new SplittableRandom(0)).in(Singleton.class);
			}

			@Singleton
			@Provides
			String test(Integer i) {
				return String.valueOf(i);
			}
		};

		SplittableRandom local = new SplittableRandom(1);
		Module child = new AbstractModule() {
			@Override
			protected void configure() {
				bind(SplittableRandom.class).toInstance(local);
				bind(Integer.class).toInstance(2);
			}
		};

		Injector inj = Guice.createInjector(parent);
		assertThat(inj.getInstance(String.class)).isEqualTo("1");
		Injector childInj = createCopiedInjector(inj, List.of(child), String.class);

		SplittableRandom instance = inj.getInstance(SplittableRandom.class);
		assertThat(instance).isNotSameAs(local);
		assertThat(inj.getInstance(String.class)).isEqualTo("1");

		assertThat(childInj.getInstance(SplittableRandom.class)).isSameAs(local);
		assertThat(childInj.getInstance(Integer.class)).isEqualTo(2);
		assertThat(childInj.getInstance(String.class)).isEqualTo("2");

	}
}
