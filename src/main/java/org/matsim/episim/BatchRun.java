package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.Config;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

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
	 * @param clazz setup class
	 * @param paramClazz class holding the parameters
	 * @param <T> params type
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

		return new PreparedRun(setup, fields.stream().map(Field::getName).collect(Collectors.toList()), runs);
	}

	/**
	 * Prepare a config using the given parameters.
	 * @param id task id
	 * @param params parameters to use
	 * @return initialized config
	 */
	Config prepareConfig(int id, T params);

	/**
	 * Write additionally needed files to {@code directory}, if any are needed.
	 * Don't write the config!
	 */
	default void writeAuxiliaryFiles(Path directory, Config config) throws IOException {}

	/**
	 * Desired output name.
	 */
	default String getOutputName(PreparedRun.Run run) {
		return Joiner.on("-").join(run.params);
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
	 * See {@link Parameter}
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface IntParameter {
		int[] value();
	}

	/**
	 * See {@link Parameter}
	 */
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface StringParameter {
		String[] value();
	}

}
