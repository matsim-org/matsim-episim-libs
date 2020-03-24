package org.matsim.episim;

import com.google.inject.Inject;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.episim.EpisimConfigGroup.PutTracablePersonsInQuarantine;
import org.matsim.facilities.Facility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 *
 */
public final class InfectionEventHandler implements ActivityEndEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, ActivityStartEventHandler {
        // Some notes:

        // * Especially if we repeat the same events file, then we do not have complete mixing.  So it may happen that only some subpopulations gets infected.

        // * However, if with infection proba=1 almost everybody gets infected, then in our current setup (where infected people remain in the iterations),
        // this will also happen with lower probabilities, albeit slower.  This is presumably the case that we want to investigate.

        // * We seem to be getting two different exponential spreading rates.  With infection proba=1, the crossover is (currently) around 15h.

        // TODO

        // * yyyyyy There are now some things that depend on ID conventions.  We should try to replace them.  This presumably would mean to interpret
        //  additional events.  Those would need to be prepared for the "reduced" files.  kai, mar'20


        private static final Logger log = Logger.getLogger( InfectionEventHandler.class );

        @Inject private Scenario scenario;

        private Map<Id<Person>, EpisimPerson> personMap = new LinkedHashMap<>();
        private Map<Id<Vehicle>, EpisimVehicle> vehicleMap = new LinkedHashMap<>();
        private Map<Id<Facility>, EpisimFacility> pseudoFacilityMap = new LinkedHashMap<>();

        private int cnt = 10 ;

        private EpisimConfigGroup episimConfig;

        private int iteration=0;

        private Random rnd = new Random(1);

        private EpisimReporting reporting ;

        @Inject
        public InfectionEventHandler( Config config ) {
                this.reporting = new EpisimReporting( config );
                this.episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );
        }

        @Override public void handleEvent( ActivityEndEvent activityEndEvent ) {
                double now = EpisimUtils.getCorrectedTime( activityEndEvent.getTime(), iteration );

                if (!shouldHandleActivityEvent(activityEndEvent, activityEndEvent.getActType())) {
                        return;
                }

                EpisimPerson episimPerson = this.personMap.computeIfAbsent( activityEndEvent.getPersonId(), EpisimPerson::new );
                Id<Facility> episimFacilityId = createEpisimFacilityId( activityEndEvent );

                if (iteration == 0) {
                	EpisimFacility episimFacility = this.pseudoFacilityMap.computeIfAbsent(episimFacilityId, EpisimFacility::new );
                	if (episimPerson.getFirstFacilityId() == null) {
                		episimFacility.addPerson(episimPerson, 0 );
                	}
                	infectionDynamicsFacility( episimPerson, episimFacility, now, activityEndEvent.getActType() );
                	episimFacility.removePerson(episimPerson.getPersonId());
                	handleInitialInfections(episimPerson);
                }
                else {
                	EpisimFacility episimFacility = ((EpisimFacility) episimPerson.getCurrentContainer());
                	if (!episimFacility.equals(pseudoFacilityMap.get(episimFacilityId))) {
                		throw new IllegalStateException("Something went wrong ...");
                	}
                	infectionDynamicsFacility( episimPerson, episimFacility, now, activityEndEvent.getActType() );
                	episimFacility.removePerson(episimPerson.getPersonId());
                }
                if (episimPerson.getCurrentPositionInTrajectory() == 0) {
                	episimPerson.setFirstFacilityId(episimFacilityId.toString());
                }
                handlePersonTrajectory(episimPerson.getPersonId(), activityEndEvent.getActType() );

        }

        @Override public void handleEvent( PersonEntersVehicleEvent entersVehicleEvent ) {
                double now = EpisimUtils.getCorrectedTime( entersVehicleEvent.getTime(), iteration );

                if ( !shouldHandlePersonEvent(entersVehicleEvent) ) {
                        return;
                }

                // if pt is shut down nothing happens here
                if (episimConfig.getUsePt() == EpisimConfigGroup.UsePt.no && episimConfig.getUsePtDate() <= iteration) {
                		return;
                }

                // find the person:
                EpisimPerson episimPerson = this.personMap.computeIfAbsent( entersVehicleEvent.getPersonId(), EpisimPerson::new );

                // find the vehicle:
                EpisimVehicle episimVehicle = this.vehicleMap.computeIfAbsent( entersVehicleEvent.getVehicleId(), EpisimVehicle::new );

                // add person to vehicle and memorize entering time:
                episimVehicle.addPerson( episimPerson, now );

        }

        @Override public void handleEvent( PersonLeavesVehicleEvent leavesVehicleEvent ) {
                double now = EpisimUtils.getCorrectedTime( leavesVehicleEvent.getTime(), iteration );

                if (!shouldHandlePersonEvent(leavesVehicleEvent) ){
                        return;
                }

                // if pt is shut down nothing happens here
                if (episimConfig.getUsePt() == EpisimConfigGroup.UsePt.no && episimConfig.getUsePtDate() <= iteration) {
                		return;
                }
                // find vehicle:
                EpisimVehicle episimVehicle = this.vehicleMap.get( leavesVehicleEvent.getVehicleId() );


                EpisimPerson episimPerson = episimVehicle.getPerson( leavesVehicleEvent.getPersonId() );

                infectionDynamicsVehicle( episimPerson, episimVehicle, now );


                // remove person from vehicle:
                episimVehicle.removePerson( episimPerson.getPersonId() );
        }

        @Override public void handleEvent( ActivityStartEvent activityStartEvent ) {
                double now = EpisimUtils.getCorrectedTime( activityStartEvent.getTime(), iteration );

                if (!shouldHandleActivityEvent(activityStartEvent, activityStartEvent.getActType())) {
                        return;
                }

                // find the person:
                EpisimPerson episimPerson = this.personMap.computeIfAbsent( activityStartEvent.getPersonId(), EpisimPerson::new );

                // create pseudo facility id that includes the activity type:
                Id<Facility> episimFacilityId = createEpisimFacilityId( activityStartEvent );

                // find the facility
                EpisimFacility episimFacility = this.pseudoFacilityMap.computeIfAbsent(episimFacilityId, EpisimFacility::new );

                // add person to facility
                episimFacility.addPerson(episimPerson, now );

                episimPerson.setLastFacilityId(episimFacilityId.toString());

                handlePersonTrajectory(episimPerson.getPersonId(), activityStartEvent.getActType().toString());

        }

        /**
         * Whether {@code event} should be handled.
         * @param actType activity type
         */
        private boolean shouldHandleActivityEvent(HasPersonId event, String actType) {
                // ignore drt and stage activities
                return !event.getPersonId().toString().startsWith("drt") && !event.getPersonId().toString().startsWith("rt")
                        && !TripStructureUtils.isStageActivityType(actType);
        }

        /**
         * Whether a Person event (e.g. {@link PersonEntersVehicleEvent} should be handled.
         */
        private boolean shouldHandlePersonEvent(HasPersonId event) {
                // ignore pt drivers and drt
                String id = event.getPersonId().toString();
                return !id.startsWith("pt_pt") && !id.startsWith("pt_tr") && !id.startsWith("drt") && !id.startsWith("rt");
        }

        private Id<Facility> createEpisimFacilityId( HasFacilityId event ) {
                if (episimConfig.getFacilitiesHandling()== EpisimConfigGroup.FacilitiesHandling.snz ) {
                        return Id.create( event.getFacilityId(), Facility.class );
                } else if ( episimConfig.getFacilitiesHandling() == EpisimConfigGroup.FacilitiesHandling.bln ){
                        if ( event instanceof ActivityStartEvent ){
                                ActivityStartEvent theEvent = (ActivityStartEvent) event;
                                return Id.create( theEvent.getActType().split( "_" )[0] + "_" + theEvent.getLinkId().toString(), Facility.class );
                        } else if ( event instanceof ActivityEndEvent ) {
                                ActivityEndEvent theEvent = (ActivityEndEvent) event;
                                return Id.create( theEvent.getActType().split( "_" )[0] + "_" + theEvent.getLinkId().toString(), Facility.class );
                        } else {
                                throw new IllegalStateException( "unexpected event type=" + ((Event)event).getEventType() ) ;
                        }
                } else {
                        throw new NotImplementedException( Gbl.NOT_IMPLEMENTED );
                }

        }
        private void handlePersonTrajectory(Id<Person> personId, String trajectoryElement) {
                EpisimPerson person = personMap.get(personId);
                if (person.getCurrentPositionInTrajectory() + 1 == person.getTrajectory().size()) {
                        return;
                }
                person.setCurrentPositionInTrajectory(person.getCurrentPositionInTrajectory() + 1);
                if (iteration > 0) {
                        return;
                }
                person.addToTrajectory(trajectoryElement);
        }
        private void handleInitialInfections( EpisimPerson personWrapper ){
                // initial infections:
                if( cnt > 0 ){
                        personWrapper.setDiseaseStatus( DiseaseStatus.infectedButNotContagious );
                        personWrapper.setInfectionDate(iteration);
                        log.warn(" person " + personWrapper.getPersonId() +" has initial infection");
                        cnt--;
                        if ( scenario!=null ){
                                final Person person = PopulationUtils.findPerson( personWrapper.getPersonId(), scenario );
                                if( person != null ){
                                        person.getAttributes().putAttribute( AgentSnapshotInfo.marker, true );
                                }
                        }
                }
        }
        private void infectionDynamicsVehicle( EpisimPerson personLeavingVehicle, EpisimVehicle vehicle, double now ){
                infectionDynamicsGeneralized( personLeavingVehicle, vehicle, now, vehicle.getContainerId().toString() );
        }
        private void infectionDynamicsFacility( EpisimPerson personLeavingFacility, EpisimFacility facility, double now, String actType ) {
                infectionDynamicsGeneralized( personLeavingFacility, facility, now, actType );
        }
        private void infectionDynamicsGeneralized( EpisimPerson personLeavingContainer, EpisimContainer<?> container, double now, String infectionType ) {
        		
                if (iteration == 0) {
                        return;
                }

                if( !EpisimUtils.isRelevantForInfectionDynamics( personLeavingContainer, container, episimConfig, iteration, rnd ) ) {
                        return;
                }

                int contactPersons = 0 ;

                ArrayList<EpisimPerson> personsToInteractWith = new ArrayList<>( container.getPersons() );
                personsToInteractWith.remove( personLeavingContainer );

                for ( int ii = 0 ; ii<personsToInteractWith.size(); ii++ ) {
                        // yy Shouldn't we be able to just count up to min( ...size(), 3 ) and get rid of the separate contactPersons counter?  kai, mar'20

                        // (this is "-1" because we can't interact with "self")

                        // we are essentially looking at the situation when the person leaves the container.  Interactions with other persons who have
                        // already left the container were treated then.  In consequence, we have some "circle of persons around us" (yyyy which should
                        //  depend on the density), and then a probability of infection in either direction.

                        // if we have seen enough, then break, no matter what:
                        if (contactPersons >= 3) {
                                break;
                        }
                        // For the time being, will just assume that the first 10 persons are the ones we interact with.  Note that because of
                        // shuffle, those are 10 different persons every day.

                        int idx = rnd.nextInt( container.getPersons().size() );
                        EpisimPerson otherPerson = container.getPersons().get( idx );

                        contactPersons++;

                        // (we count "quarantine" as well since they essentially represent "holes", i.e. persons who are no longer there and thus the
                        // density in the transit container goes down.  kai, mar'20)

                        if ( personLeavingContainer.getDiseaseStatus()==otherPerson.getDiseaseStatus() ) {
                                // (if they have the same status, then nothing can happen between them)
                                continue;
                        }

                        if( !EpisimUtils.isRelevantForInfectionDynamics( otherPerson, container, episimConfig, iteration, rnd ) ) {
                                continue;
                        }

                        // keep track of contacts:
                        if(infectionType.contains("home") || infectionType.contains("work") || (infectionType.contains("leisure") && rnd.nextDouble() < 0.8)) {
                                if (!personLeavingContainer.getTraceableContactPersons().contains(otherPerson)) {
                                        personLeavingContainer.addTracableContactPerson(otherPerson);
                                }
                                if (!otherPerson.getTraceableContactPersons().contains(personLeavingContainer)) {
                                        otherPerson.addTracableContactPerson(personLeavingContainer);
                                }
                        }

                        Double containerEnterTimeOfPersonLeaving = container.getContainerEnteringTime( personLeavingContainer.getPersonId() );
                        Double containerEnterTimeOfOtherPerson = container.getContainerEnteringTime( otherPerson.getPersonId() );

                        // persons leaving their first-ever activity have no starting time for that activity.  Need to hedge against that.  Since all persons
                        // start healthy (the first seeds are set at enterVehicle), we can make some assumptions.
                        if ( containerEnterTimeOfPersonLeaving==null && containerEnterTimeOfOtherPerson==null ) {
                                throw new IllegalStateException( "should not happen" );
                                // null should only happen at first activity.  However, at first activity all persons are susceptible.  So the only way we
                                // can get here is if an infected person entered the container and is now leaving again, while the other person has been in the
                                // container from the beginning.  ????  kai, mar'20
                        }
                        if ( containerEnterTimeOfPersonLeaving==null ) {
                                containerEnterTimeOfPersonLeaving = Double.NEGATIVE_INFINITY;
                        }
                        if ( containerEnterTimeOfOtherPerson==null ) {
                                containerEnterTimeOfOtherPerson = Double.NEGATIVE_INFINITY;
                        }

                        double jointTimeInContainer = now - Math.max( containerEnterTimeOfPersonLeaving, containerEnterTimeOfOtherPerson );
                        if ( jointTimeInContainer  < 0 || jointTimeInContainer > 86400) {
                            log.warn(containerEnterTimeOfPersonLeaving);
                            log.warn(containerEnterTimeOfOtherPerson);
                            log.warn(now);
                        	throw new IllegalStateException("joint time in container is not plausible for personLeavingContainer=" + personLeavingContainer.getPersonId() + " and otherPerson=" + otherPerson.getPersonId() + ". Joint time is=" + jointTimeInContainer);
                        }

                        double contactIntensity = -1 ;
                        for( EpisimConfigGroup.InfectionParams infectionParams : episimConfig.getContainerParams().values() ){
                                if ( infectionType.contains( infectionParams.getContainerName() ) ) {
                                        contactIntensity = infectionParams.getContactIntensity();
                                }
                        }
                        if ( contactIntensity < 0. ) {
                                throw new RuntimeException( "contactIntensity for infectionType=" + infectionType + " is not defined.  There needs to be a " +
                                                                            "config entry for each infection type." );
                        }

                        double infectionProba = 1 - Math.exp( -episimConfig.getCalibrationParameter() * contactIntensity * jointTimeInContainer );
                        // note that for 1pct runs, calibParam is of the order of one, which means that for typical times of 100sec or more,
                        // exp( - 1 * 1 * 100 ) \approx 0, and thus the infection proba becomes 1.  Which also means that changes in contactIntensity has
                        // no effect.  kai, mar'20

                        if ( rnd.nextDouble() < infectionProba ) {
                                if ( personLeavingContainer.getDiseaseStatus()== DiseaseStatus.susceptible ) {
                                        infectPerson( personLeavingContainer, otherPerson, now, infectionType );
                                        return;
                                } else {
                                        infectPerson( otherPerson, personLeavingContainer, now, infectionType );
                                }
                        }

                }

        }

        private void infectPerson( EpisimPerson personWrapper, EpisimPerson infector, double now, String infectionType ){

                if (personWrapper.getDiseaseStatus() != DiseaseStatus.susceptible) {
                        throw new IllegalStateException("Person to be infected is not susceptible. Status is=" + personWrapper.getDiseaseStatus());
                }
                if (infector.getDiseaseStatus() != DiseaseStatus.contagious) {
                        throw new IllegalStateException("Infector is not contagious. Status is=" + infector.getDiseaseStatus());
                }
                if (personWrapper.getQuarantineStatus() != QuarantineStatus.no ) {
                        throw new IllegalStateException("Person to be infected is in quarantine.");
                }
                if (infector.getQuarantineStatus() != QuarantineStatus.no ) {
                        throw new IllegalStateException("Infector is in quarantine.");
                }
                if (!personWrapper.getCurrentContainer().equals(infector.getCurrentContainer())) {
            			throw new IllegalStateException("Person and infector are not in same container!");
                }

                personWrapper.setDiseaseStatus( DiseaseStatus.infectedButNotContagious );
                if ( scenario!=null ){
                        final Person person = PopulationUtils.findPerson( personWrapper.getPersonId(), scenario );
                        if( person != null ){
                                person.getAttributes().putAttribute( AgentSnapshotInfo.marker, true );
                        }
                }

                personWrapper.setInfectionDate(iteration);

                reporting.reportInfection( personWrapper, infector, now, infectionType );
        }
        @Override public void reset( int iteration ){

                for ( EpisimPerson person : personMap.values()) {
                	handleNoCircle(person);
                	person.setCurrentPositionInTrajectory(0);
                        switch ( person.getDiseaseStatus() ) {
                                case susceptible:
                                        break;
                                case infectedButNotContagious:
                                        if ( person.daysSinceInfection(iteration) >= 4 ) {
                                                person.setDiseaseStatus( DiseaseStatus.contagious );
                                        }
                                        break;
                                case contagious:
                                        if ( person.daysSinceInfection(iteration) == 6 ){
                                                final double nextDouble = rnd.nextDouble();
                                                if( nextDouble < 0.2 ){
                                                        // 20% recognize that they are sick and go into quarantine:

                                                        person.setQuarantineDate( iteration );
                                                        // yyyy date needs to be qualified by status (or better, add iteration into quarantine status setter)

                                                        person.setQuarantineStatus( QuarantineStatus.full );
                                                        // yyyy this should become "home"!  kai, mar'20

                                                        if( episimConfig.getPutTracablePersonsInQuarantine() == PutTracablePersonsInQuarantine.yes ){
                                                                for( EpisimPerson pw : person.getTraceableContactPersons() ){
                                                                        if( pw.getQuarantineStatus() == QuarantineStatus.no ){

                                                                                pw.setQuarantineStatus( QuarantineStatus.full );
                                                                                // yyyy this should become "home"!  kai, mar'20

                                                                                pw.setQuarantineDate( iteration );
                                                                                // yyyy date needs to be qualified by status (or better, add iteration into
                                                                                // quarantine status setter)

                                                                        }
                                                                }
                                                        }

                                                }
                                        } else if ( person.daysSinceInfection(iteration) == 10 ) {
                                                if ( rnd.nextDouble() < 0.045 ){
                                                        // (4.5% get seriously sick.  This is taken from all infected persons, not just those the have shown
                                                        // symptoms before)
                                                        person.setDiseaseStatus( DiseaseStatus.seriouslySick );
                                                }
                                        } else if ( iteration - person.getInfectionDate() >= 16 ) {
                                                person.setDiseaseStatus( DiseaseStatus.recovered );
                                        }
                                        break;
                                case seriouslySick:
                                        if ( person.daysSinceInfection(iteration) == 11 ) {
                                                if ( rnd.nextDouble() < 0.25 ){
                                                        // (25% of persons who are seriously sick transition to critical)
                                                        person.setDiseaseStatus( DiseaseStatus.critical );
                                                }
                                        } else if ( person.daysSinceInfection(iteration) >= 23 ) {
                                                person.setDiseaseStatus( DiseaseStatus.recovered );
                                        }
                                        break;
                                case critical:
                                        if (person.daysSinceInfection(iteration) == 20 ) {
                                                // (transition back to seriouslySick.  Note that this needs to be earlier than sSick->recovered, otherwise
                                                // they stay in sSick.  Problem is that we need differentiation between intensive care beds and normal
                                                // hospital beds.)
                                                person.setDiseaseStatus( DiseaseStatus.seriouslySick );
                                        }
                                        break;
                                case recovered:
                                        break;
                                default:
                                        throw new IllegalStateException( "Unexpected value: " + person.getDiseaseStatus() );
                        }
                        if (person.getQuarantineStatus() == QuarantineStatus.full && person.daysSinceQuarantine(iteration) >= 14 ) {
                                person.setQuarantineStatus( QuarantineStatus.no );
                        }
                        person.getTraceableContactPersons().clear();
                }

                this.iteration = iteration;

                reporting.reporting( personMap, iteration );

        }
        private void handleNoCircle(EpisimPerson person) {
        	Id<Facility> firstFacilityId = Id.create(person.getFirstFacilityId(), Facility.class);
			if (person.isInContainer()) {
				Id<?> lastFacilityId = person.getCurrentContainer().getContainerId();
				if (this.pseudoFacilityMap.containsKey(lastFacilityId) && !firstFacilityId.equals(lastFacilityId)) {
					EpisimFacility lastFacility = this.pseudoFacilityMap.get(lastFacilityId);
					infectionDynamicsFacility(person, lastFacility, (iteration + 1) * 86400d, person.getTrajectory().get(person.getTrajectory().size() - 1));
					lastFacility.removePerson(person.getPersonId());
					EpisimFacility firstFacility = this.pseudoFacilityMap.get(firstFacilityId);
					firstFacility.addPerson(person, (iteration + 1) * 86400d);
				}
				if (this.vehicleMap.containsKey(lastFacilityId)) {
					EpisimVehicle lastVehicle = this.vehicleMap.get(lastFacilityId);
					infectionDynamicsVehicle(person, lastVehicle, (iteration + 1) * 86400d);
					lastVehicle.removePerson(person.getPersonId());
					EpisimFacility firstFacility = this.pseudoFacilityMap.get(firstFacilityId);
					firstFacility.addPerson(person, (iteration + 1) * 86400d);
				}
			}
			else {
				EpisimFacility firstFacility = this.pseudoFacilityMap.get(firstFacilityId);
				firstFacility.addPerson(person, (iteration + 1) * 86400d);
			}

		}

        static final class EpisimVehicle extends EpisimContainer<Vehicle>{
                EpisimVehicle( Id<Vehicle> vehicleId ){
                        super( vehicleId );
                }
        }
        static final class EpisimFacility extends EpisimContainer<Facility>{
                EpisimFacility( Id<Facility> facilityId ){
                        super( facilityId );
                }
        }
        enum DiseaseStatus{susceptible, infectedButNotContagious, contagious, seriouslySick, critical, recovered};
        enum QuarantineStatus {full, atHome, no}
}

