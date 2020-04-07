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

public interface BatchRun<T> {

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


		return new PreparedRun(setup, fields.stream().map(Field::getName).collect(Collectors.toList()), runs);
	}

	Config prepareConfig(int id, T params);

	void write(Path directory, Config config) throws IOException;


	default String getOutputName(PreparedRun.Run run) {
		return Joiner.on("-").join(run.params);
	}

	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface Parameter {
		double[] value();
	}

	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	@interface StringParameter {
		String[] value();
	}

}
