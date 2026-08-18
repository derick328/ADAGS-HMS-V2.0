package com.adags.modules.referencedata.infrastructure;

import com.adags.modules.referencedata.ReferenceDataFacade;
import com.adags.modules.referencedata.domain.Icd10Code;
import com.adags.modules.referencedata.domain.Icd10CodeRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ReferenceDataFacadeImpl implements ReferenceDataFacade {

    private final Icd10CodeRepository repository;

    public ReferenceDataFacadeImpl(Icd10CodeRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> resolveBillableIcd10Title(String code) {
        return repository.findByCode(code)
                .filter(Icd10Code::isActive)
                .filter(Icd10Code::isBillable)
                .map(Icd10Code::title);
    }
}
