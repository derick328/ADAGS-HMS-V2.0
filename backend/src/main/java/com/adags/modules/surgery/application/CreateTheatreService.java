package com.adags.modules.surgery.application;

import com.adags.modules.surgery.domain.Theatre;
import com.adags.modules.surgery.domain.TheatreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// THEATRE_MANAGE (V50) — admin defines the physical resource, same pattern
// as CreateWardService/CreateBedService.
@Service
public class CreateTheatreService {

    private final TheatreRepository theatreRepository;

    public CreateTheatreService(TheatreRepository theatreRepository) {
        this.theatreRepository = theatreRepository;
    }

    @Transactional
    public Theatre create(CreateTheatreCommand command) {
        Theatre toInsert = new Theatre(null, command.facilityId(), command.name(), true, null);
        return theatreRepository.insert(toInsert);
    }
}
