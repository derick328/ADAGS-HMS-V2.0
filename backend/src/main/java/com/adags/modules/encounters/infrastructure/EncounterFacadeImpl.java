package com.adags.modules.encounters.infrastructure;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.encounters.application.InvalidEncounterTransitionException;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterEvent;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.modules.encounters.domain.EncounterState;
import com.adags.shared.kernel.EncounterContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Component
public class EncounterFacadeImpl implements EncounterFacade {

    private final EncounterRepository encounterRepository;

    public EncounterFacadeImpl(EncounterRepository encounterRepository) {
        this.encounterRepository = encounterRepository;
    }

    @Override
    public Optional<EncounterContext> findContext(UUID encounterId) {
        return encounterRepository.findById(encounterId).map(this::toContext);
    }

    @Override
    @Transactional
    public void admit(UUID encounterId, UUID actorUserId) {
        transition(encounterId, EncounterState.ADMITTED, actorUserId);
    }

    @Override
    @Transactional
    public void moveToWard(UUID encounterId, UUID actorUserId) {
        transition(encounterId, EncounterState.IN_WARD, actorUserId);
    }

    private void transition(UUID encounterId, EncounterState targetState, UUID actorUserId) {
        Encounter encounter = encounterRepository.findById(encounterId)
                .orElseThrow(() -> new NoSuchElementException("Encounter not found: " + encounterId));
        if (!encounter.state().canTransitionTo(targetState)) {
            throw new InvalidEncounterTransitionException(encounter.state(), targetState);
        }
        encounterRepository.updateState(encounterId, targetState);
        encounterRepository.insertEvent(
                new EncounterEvent(null, encounterId, encounter.state(), targetState, actorUserId, null, null));
    }

    private EncounterContext toContext(Encounter encounter) {
        return new EncounterContext(
                encounter.patientId(), encounter.facilityId(), encounter.departmentId(), encounter.payerType());
    }
}
