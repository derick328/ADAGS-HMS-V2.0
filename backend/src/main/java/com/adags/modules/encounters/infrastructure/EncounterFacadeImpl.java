package com.adags.modules.encounters.infrastructure;

import com.adags.modules.encounters.EncounterFacade;
import com.adags.modules.encounters.domain.Encounter;
import com.adags.modules.encounters.domain.EncounterRepository;
import com.adags.shared.kernel.EncounterContext;
import org.springframework.stereotype.Component;

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

    private EncounterContext toContext(Encounter encounter) {
        return new EncounterContext(
                encounter.patientId(), encounter.facilityId(), encounter.departmentId(), encounter.payerType());
    }
}
