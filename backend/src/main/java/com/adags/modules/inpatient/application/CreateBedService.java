package com.adags.modules.inpatient.application;

import com.adags.modules.inpatient.domain.Bed;
import com.adags.modules.inpatient.domain.BedRepository;
import com.adags.modules.inpatient.domain.BedStatus;
import com.adags.modules.inpatient.domain.WardRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class CreateBedService {

    private final BedRepository bedRepository;
    private final WardRepository wardRepository;

    public CreateBedService(BedRepository bedRepository, WardRepository wardRepository) {
        this.bedRepository = bedRepository;
        this.wardRepository = wardRepository;
    }

    public Bed create(CreateBedCommand command) {
        wardRepository.findById(command.wardId())
                .orElseThrow(() -> new NoSuchElementException("Ward not found: " + command.wardId()));

        Bed toInsert = new Bed(null, command.wardId(), command.label(), command.bedType(), BedStatus.AVAILABLE, null);
        return bedRepository.insert(toInsert);
    }
}
