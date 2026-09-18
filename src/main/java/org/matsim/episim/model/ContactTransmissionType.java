package org.matsim.episim.model;

/**
 * Defines the route by which a pathogen can be transmitted between hosts.
 *
 * <p>A transmission route describes the mechanism of exposure and therefore
 * determines how potentially infectious interactions should be modeled.
 * Different routes may require fundamentally different exposure models.
 * For example, respiratory transmission may depend on co-presence, distance,
 * duration, and ventilation, while direct-contact transmission requires
 * physical interaction between hosts, and fomite transmission involves an
 * intermediate contaminated object or surface.
 *
 * <p>The transmission route is a property of the pathogen and defines which
 * types of exposure can result in transmission.
 */
public enum ContactTransmissionType {
	/** Transmission through respiratory droplets or aerosols. */
	RESPIRATORY,

	/** Transmission through direct physical contact between hosts. */
	DIRECT_CONTACT,

	/** Transmission through contaminated objects or surfaces. */
	//FOMITE //We do not have necessary to do it now, and we do not have this logic in facilities, it is future improvement
}
