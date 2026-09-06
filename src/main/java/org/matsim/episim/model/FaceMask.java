package org.matsim.episim.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FaceMask {

	public final static FaceMask NONE = new FaceMask("NONE",1d, 1d);
//	CLOTH(0.6, 0.5),
//	SURGICAL(0.3, 0.2),
//	N95(0.15, 0.025);

	// values based on Kriegel
	public final static FaceMask CLOTH=new FaceMask("CLOTH",0.8, 0.7);
	public final static FaceMask SURGICAL=new FaceMask("SURGICAL",0.8, 0.7);
	public final static FaceMask N95=new FaceMask("N95",0.6, 0.2);

	private static final List<FaceMask> STANDARD_OPTIONS = List.of(NONE, CLOTH, SURGICAL,N95);

	public final String name;
	public final double shedding;
	public final double intake;

	private static final Map<String, FaceMask> FACE_MASK = new ConcurrentHashMap<>();

	static {
		for (FaceMask strain : STANDARD_OPTIONS) {
			FACE_MASK.put(strain.name, strain);
		}
	}


	public FaceMask(String name, double shedding, double intake) {
		this.name = name;
		this.shedding = shedding;
		this.intake = intake;
	}

	public List<FaceMask> values(){
		return STANDARD_OPTIONS;
	}

	public static FaceMask of(String name) {
		Objects.requireNonNull(name, "FaceMask name must not be null");
		return FACE_MASK.computeIfAbsent(name,
			(a)-> {throw new RuntimeException("Face mask should be initialized before name based call");
		});
	}

	public static FaceMask of(String name,Double shedding,Double intake ) {
		Objects.requireNonNull(name, "FaceMask name must not be null");
		Objects.requireNonNull(shedding, "FaceMask shedding must not be null");
		Objects.requireNonNull(intake, "FaceMask intake must not be null");
		return FACE_MASK.computeIfAbsent(name, a->new FaceMask(name, shedding, intake));
	}

	public String getName() {
		return name;
	}

	public double getShedding() {
		return shedding;
	}

	public double getIntake() {
		return intake;
	}

	public static Collection<FaceMask> getAllStandardOptions() {
		return STANDARD_OPTIONS;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) return false;
		FaceMask faceMask = (FaceMask) o;
		return Objects.equals(name, faceMask.name);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(name);
	}
}
