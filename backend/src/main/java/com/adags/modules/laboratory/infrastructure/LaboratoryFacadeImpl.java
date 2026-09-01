package com.adags.modules.laboratory.infrastructure;

import com.adags.modules.laboratory.LaboratoryFacade;
import com.adags.modules.laboratory.domain.LabTest;
import com.adags.modules.laboratory.domain.LabTestRepository;
import com.adags.modules.laboratory.domain.VerificationLevel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class LaboratoryFacadeImpl implements LaboratoryFacade {

    private final LabTestRepository labTestRepository;

    public LaboratoryFacadeImpl(LabTestRepository labTestRepository) {
        this.labTestRepository = labTestRepository;
    }

    @Override
    @Transactional
    public UUID ensureCatalogueTest(String code, String name) {
        return labTestRepository.findByCode(code)
                .map(LabTest::id)
                .orElseGet(() -> {
                    LabTest draft = LabTest.create(code, name, "—", "—", 24,
                            VerificationLevel.NONE, false);
                    return labTestRepository.insert(draft).id();
                });
    }
}
