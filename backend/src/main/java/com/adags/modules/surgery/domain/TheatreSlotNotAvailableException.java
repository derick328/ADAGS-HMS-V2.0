package com.adags.modules.surgery.domain;

import java.time.LocalDate;
import java.util.UUID;

// root CLAUDE.md "Concurrency": surfaces idx_theatre_slot_not_cancelled
// rejecting a double-booking — the loser of the race gets this, not a 500.
// Same treatment as inpatient's BedNotAvailableException.
public class TheatreSlotNotAvailableException extends RuntimeException {

    private final UUID theatreId;
    private final LocalDate scheduledDate;
    private final String timeSlot;

    public TheatreSlotNotAvailableException(UUID theatreId, LocalDate scheduledDate, String timeSlot) {
        super("Theatre slot already booked: theatre " + theatreId + " on " + scheduledDate + " " + timeSlot);
        this.theatreId = theatreId;
        this.scheduledDate = scheduledDate;
        this.timeSlot = timeSlot;
    }

    public UUID theatreId() {
        return theatreId;
    }

    public LocalDate scheduledDate() {
        return scheduledDate;
    }

    public String timeSlot() {
        return timeSlot;
    }
}
