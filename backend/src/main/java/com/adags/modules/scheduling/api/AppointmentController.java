package com.adags.modules.scheduling.api;

import com.adags.modules.scheduling.application.CreateFollowUpAppointmentCommand;
import com.adags.modules.scheduling.application.CreateFollowUpAppointmentService;
import com.adags.modules.scheduling.domain.Appointment;
import com.adags.modules.scheduling.domain.AppointmentRepository;
import com.adags.shared.authz.Actor;
import com.adags.shared.authz.AuthorizationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

// 05a-receptionist-decisions.md §3: D1 — the only creation path is a
// doctor's follow-up after a consultation (APPOINTMENT_CREATE_FOLLOWUP,
// already DOCTOR/SPECIALIST_DOCTOR's in V5). The receptionist only reads
// (APPOINTMENT_READ, already RECEPTIONIST's) — D10, no create/edit/
// reschedule/cancel from this workspace.
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private static final int EXPECTED_RETURNS_WINDOW_HOURS = 48;

    private final CreateFollowUpAppointmentService createFollowUpAppointmentService;
    private final AppointmentRepository appointmentRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    public AppointmentController(
            CreateFollowUpAppointmentService createFollowUpAppointmentService,
            AppointmentRepository appointmentRepository,
            AuthorizationService authorizationService,
            Clock clock
    ) {
        this.createFollowUpAppointmentService = createFollowUpAppointmentService;
        this.appointmentRepository = appointmentRepository;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(@Valid @RequestBody CreateFollowUpAppointmentRequest request, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "APPOINTMENT_CREATE_FOLLOWUP");
        Appointment appointment = createFollowUpAppointmentService.create(new CreateFollowUpAppointmentCommand(
                request.patientId(), request.sourceEncounterId(), request.scheduledAt(), request.notes(), actor.userId()));
        return AppointmentResponse.from(appointment);
    }

    /** Dashboard's "today" tile and "next 8 upcoming" panel. */
    @GetMapping
    public List<AppointmentResponse> scheduledBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticationPrincipal Actor actor
    ) {
        requirePermission(actor, "APPOINTMENT_READ");
        return appointmentRepository.findScheduledBetween(from, to).stream().map(AppointmentResponse::from).toList();
    }

    /** Dashboard's "8 most recent" panel. */
    @GetMapping("/recent")
    public List<AppointmentResponse> recent(@RequestParam(defaultValue = "8") int limit, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "APPOINTMENT_READ");
        return appointmentRepository.findRecentPast(clock.instant(), limit).stream().map(AppointmentResponse::from).toList();
    }

    /** Expected Returns §3.2 — still SCHEDULED, due within 48h or already overdue, no upper bound. */
    @GetMapping("/expected-returns")
    public List<AppointmentResponse> expectedReturns(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "APPOINTMENT_READ");
        Instant windowEnd = clock.instant().plus(EXPECTED_RETURNS_WINDOW_HOURS, ChronoUnit.HOURS);
        return appointmentRepository.findExpectedReturns(windowEnd).stream().map(AppointmentResponse::from).toList();
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
