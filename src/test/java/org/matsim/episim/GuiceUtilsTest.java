package org.matsim.episim;

import com.google.inject.Module;
import com.google.inject.*;
import org.junit.Test;

import java.util.List;

import org.matsim.episim.util.EpisimSplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.matsim.episim.GuiceUtils.createCopiedInjector;

public class GuiceUtilsTest {

	@Test
	public void copiedInjector() {


		Module parent = new AbstractModule() {
			@Override
			protected void configure() {
				bind(Integer.class).toProvider(() -> 1).in(Singleton.class);
				bind(EpisimSplittableRandom.class).toProvider(() -> new EpisimSplittableRandom(0)).in(Singleton.class);
			}

			@Singleton
			@Provides
			String test(Integer i) {
				return String.valueOf(i);
			}
		};

		EpisimSplittableRandom local = new EpisimSplittableRandom(1);
		Module child = new AbstractModule() {
			@Override
			protected void configure() {
				bind(EpisimSplittableRandom.class).toInstance(local);
				bind(Integer.class).toInstance(2);
			}
		};

		Injector inj = Guice.createInjector(parent);
		assertThat(inj.getInstance(String.class)).isEqualTo("1");
		Injector childInj = createCopiedInjector(inj, List.of(child), String.class);

		EpisimSplittableRandom instance = inj.getInstance(EpisimSplittableRandom.class);
		assertThat(instance).isNotSameAs(local);
		assertThat(inj.getInstance(String.class)).isEqualTo("1");

		assertThat(childInj.getInstance(EpisimSplittableRandom.class)).isSameAs(local);
		assertThat(childInj.getInstance(Integer.class)).isEqualTo(2);
		assertThat(childInj.getInstance(String.class)).isEqualTo("2");

	}
}
