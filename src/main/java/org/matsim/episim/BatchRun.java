/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Interface for defining the setup procedure of a batch run and the corresponding parameter class.
 * The batch runner will create the cross-product of all possible parameters configuration and prepare
 * the config for each.
 *
 * @param <T> Class holding the available parameters.
 */
public interface BatchRun<T> {

	/**
	 * Loads the defined parameters and executes the {@link #prepareConfig(int, Object)} procedure.
	 *
	 * @param clazz      setup class
	 * @param paramClazz class holding the parameters
	 * @param <T>        params type
	 */
	static <T> PreparedRun prepare(Class<? extends BatchRun<T>> clazz, Class<T> paramClazz) {

		Logger log = LogManager.getLogger(BatchRun.class);
		List<Field> fields = new ArrayList<>();
		List<List<Object>> allParams = new ArrayList<>();

		for (Field field : paramClazz.getDeclaredFields()) {
			Parameter param = field.getAnnotation(Parameter.class);
			if (param != null) {
				allParams.add(DoubleStream.of(param.value()).boxed().collect(Collectors.toList()));
				fields.add(field);
			}
			StartDates dateParam = field.getAnnotation(StartDates.class);
			if (dateParam != null) {
				if (!field.getName().equals("startDate"))
					throw new IllegalArgumentException("StartDates field must be called 'startDate'");

				allParams.add(Stream.of(dateParam.value()).map(LocalDate::parse).collect(Collectors.toList()));
				fields.add(field);
			}
			IntParameter intParam = field.getAnnotation(IntParameter.class);
			if (intParam != null) {
				allParams.add(IntStream.of(intParam.value()).boxed().collect(Collectors.toList()));
				fields.add(field);
			}
			StringParameter stringParam = field.getAnnotation(StringParameter.class);
			if (stringParam != null) {
				allParams.add(Arrays.asList(stringParam.value()));
				fields.add(field);
			}
		}

		List<PreparedRun.Run> runs = new ArrayList<>();
		BatchRun<T> setup;
		try {
			setup = clazz.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			log.error("Could not create run class", e);
			throw new IllegalArgumentException(e);
		}

		Config base = setup.baseCase(0);
		if (base != null)
			runs.add(new PreparedRun.Run(0, Lists.newArrayList("base"), base));

		List<List<Object>> combinations = Lists.cartesianProduct(Lists.newArrayList(allParams));

		int id = 0;
		for (List<Object> params : combinations) {

			try {
				T inst = paramClazz.getDeclaredConstructor().newInstance();
				for (int i = 0; i < params.size(); i++) {
					fields.get(i).setAccessible(true);
					fields.get(i).set(inst, params.get(i));
				}

				Config config = setup.prepareConfig(++id, inst);
				runs.add(new PreparedRun.Run(id, params, config));

			} catch (ReflectiveOperationException e) {
				log.error("Could not create param class", e);
				throw new IllegalArgumentException(e);
			}
		}

		log.info("Prepared {} runs for {} with params {}", runs.size(), clazz.getSimpleName(), paramClazz.getName());

		return new PreparedRun(setup, fields.stream().map(Field::getName).collect(Collectors.toList()), allParams, runs);
	}

	/**
	 * The default start of the scenario as day in real world. Only needed if there are multiple start dates in the batch run.
	 */
	default LocalDate getDefaultStartDate() {
		return LocalDate.now();
	}

	/**
	 * Returns name of the region.
	 */
	default Metadata getMetadata() {
		return Metadata.of("region", "default");
	}

	/**
	 * List of options that will be added to the metadata.
	 */
	default List<Option> getOptions() {
		return List.of();
	}

	/**
	 * Provide a base case without any parametrization.
	 */
	@Nullable
	default Config baseCase(int id) {
		return null;
	}

	/**
	 * Prepare a config using the given parameters.
	 *
	 * @param id     task id
	 * @param params parameters to use
	 * @return initialized config
	 */
	Config prepareConfig(int id, T params);

	/**
	 * Write additionally needed files to {@code directory}, if any are needed.
	 * Don't write the config!
	 */
	default void writeAuxiliaryFiles(Path directory, Config config) throws IOException {
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		if (episimConfig.getPolicyConfig() != null)
			Files.writeString(directory.resolve(episimConfig.getPolicyConfig()), episimConfig.getPolicy().root().render());

		if (!episimConfig.getProgressionConfig().isEmpty())
			Files.writeString(directory.resolve(episimConfig.getProgressionConfigName()), episimConfig.getProgressionConfig().root().render());

	}

	/**
	 * This declares a field as parameter for a batch run.
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface Parameter {
		/**
		 * All values this parameter should attain.
		 */
		double[] value();
	}

	/**
	 * Declares parameter as dates. Receiver must be {@link LocalDate} and named {@code startDate}.
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface StartDates {
		/**
		 * Desired start dates in the form yyyy-mm-dd.
		 */
		String[] value();
	}

	/**
	 * See {@link Parameter}.
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface IntParameter {
		int[] value();
	}

	/**
	 * See {@link Parameter}.
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface StringParameter {
		String[] value();
	}


	/**
	 * Describes one option group of parameters with multiple measures.
	 */
	final class Option {

		public final String heading;
		public final String subheading;
		public final int day;

		/**
		 * Tuples of (title, paramName).
		 */
		public final List<Pair<String, String>> measures = new ArrayList<>();

		private Option(String heading, String subheading, int day) {
			this.heading = heading;
			this.subheading = subheading;
			this.day = day;
		}

		/**
		 * Creates a new option group.
		 *
		 * @param heading    header shown in ui
		 * @param subheading description shown ui
		 * @param day        day when it will be in effect
		 */
		public static Option of(String heading, String subheading, int day) {
			return new Option(heading, subheading, day);
		}

		/**
		 * See {@link #of(String, String, int)}.
		 */
		public static Option of(String heading, int day) {
			return new Option(heading, "", day);
		}

		/**
		 * See {@link #of(String, String, int)}.
		 */
		public static Option of(String heading) {
			return new Option(heading, "", -1);
		}

		/**
		 * Adds an measure to this option.
		 *
		 * @param title title shown in ui
		 * @param param name of the parameter in code
		 */
		public Option measure(String title, String param) {
			measures.add(Pair.of(title, param));
			return this;
		}
	}

	/**
	 * Contains the metadata of a batch run.
	 */
	final class Metadata {

		public final String region;
		public final String name;

		/**
		 * End date for the ui.
		 */
		String endDate = null;

		public Metadata(String region, String name) {
			this.region = region;
			this.name = name;
		}

		/**
		 * Creates new metadata instance.
		 */
		public static Metadata of(String region, String name) {
			return new Metadata(region, name);
		}

		/**
		 * Sets the end date and returns the same instance.
		 */
		public Metadata withEndDate(String date) {
			this.endDate = endDate;
			return this;
		}

	}

}
