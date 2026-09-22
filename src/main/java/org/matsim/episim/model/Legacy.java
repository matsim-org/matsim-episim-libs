package org.matsim.episim.model;

import com.google.inject.BindingAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the legacy implementation a module would use, as opposed to the implementation the run actually uses.
 *
 * <p>Scenarios differ in which legacy immunity implementation belongs to them: the curve based one, or the split
 * that takes severity from antibodies. They bind that choice as {@code @Legacy ImmunityModel}, and the provider of
 * {@code ImmunityModel} in {@code EpisimModule} decides from the configuration whether the run uses it at all. A
 * scenario that bound {@code ImmunityModel} directly would silently overrule that setting.</p>
 */
@BindingAnnotation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Legacy {
}
