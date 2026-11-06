package com.adags.modules.scheduling.api;

import com.adags.modules.scheduling.application.CreateFollowUpAppointmentCommand;
import com.adags.modules.scheduling.application.CreateFollowUpAppointmentService;
import com.adags.modules.scheduling.application.ClinicFollowUpService;
import com.adags.shared.authz.ScopeRef;
import com.adags.shared.authz.ScopeType;
import com.adags.modules.scheduling.domain.AppointmentStatus;
import java.util.UUID;
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
    private final ClinicFollowUpService clinics;

    public AppointmentController(
            CreateFollowUpAppointmentService createFollowUpAppointmentService,
            AppointmentRepository appointmentRepository,
            AuthorizationService authorizationService,
            Clock clock,
            ClinicFollowUpService clinics
    ) {
        this.createFollowUpAppointmentService = createFollowUpAppointmentService;
        this.appointmentRepository = appointmentRepository;
        this.authorizationService = authorizationService;
        this.clock = clock;
        this.clinics = clinics;
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
        return appointmentRepository.findScheduledBetween(from, to).stream().filter(a -> visible(a, actor)).map(a -> response(a, actor)).toList();
    }

    /** Dashboard's "8 most recent" panel. */
    @GetMapping("/recent")
    public List<AppointmentResponse> recent(@RequestParam(defaultValue = "8") int limit, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "APPOINTMENT_READ");
        return appointmentRepository.findRecentPast(clock.instant(), limit).stream().filter(a -> visible(a, actor)).map(a -> response(a, actor)).toList();
    }

    /** Expected Returns §3.2 — still SCHEDULED, due within 48h or already overdue, no upper bound. */
    @GetMapping("/expected-returns")
    public List<AppointmentResponse> expectedReturns(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "APPOINTMENT_READ");
        Instant windowEnd = clock.instant().plus(EXPECTED_RETURNS_WINDOW_HOURS, ChronoUnit.HOURS);
        return appointmentRepository.findExpectedReturns(windowEnd).stream().filter(a -> visible(a, actor)).map(a -> response(a, actor)).toList();
    }

    public record ClinicBookingRequest(UUID patientId, UUID sourceEncounterId, UUID departmentId, String clinicName,
                                       Instant scheduledAt, String instructions) {}
    public record RescheduleRequest(Instant scheduledAt, String instructions) {}

    @PostMapping("/clinic")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse scheduleClinic(@RequestBody ClinicBookingRequest request, @AuthenticationPrincipal Actor actor) {
        if (request.patientId() == null || request.sourceEncounterId() == null || request.departmentId() == null) {
            throw new IllegalArgumentException("Patient, encounter and clinic are required.");
        }
        requireScoped(actor, "APPOINTMENT_CREATE_FOLLOWUP", request.departmentId());
        return response(clinics.schedule(request.patientId(), request.sourceEncounterId(), request.departmentId(), request.clinicName(),
                request.scheduledAt(), request.instructions(), actor.userId()), actor);
    }

    @GetMapping("/patient/{patientId}")
    public List<AppointmentResponse> patientHistory(@PathVariable UUID patientId, @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "APPOINTMENT_READ");
        return appointmentRepository.findByPatientId(patientId).stream().filter(a -> visible(a, actor)).map(a -> response(a, actor)).toList();
    }

    @GetMapping("/clinic-plans")
    public List<ClinicFollowUpService.Plan> clinicPlans(@RequestParam(required = false) UUID patientId,
                                                        @RequestParam(required = false) UUID facilityId,
                                                        @RequestParam(defaultValue = "false") boolean mine,
                                                        @AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "APPOINTMENT_READ");
        if (patientId == null && facilityId == null && !mine) throw new IllegalArgumentException("Specify patient, facility or mine.");
        var plans = mine ? clinics.plansForProvider(actor.userId()) : clinics.plans(patientId, facilityId);
        return plans.stream().filter(p ->
                authorizationService.hasPermission(actor, "APPOINTMENT_READ",
                        ScopeRef.of(ScopeType.DEPARTMENT, p.departmentId()))).toList();
    }

    @GetMapping("/clinic-active")
    public List<AppointmentResponse> activeClinicAppointments(@AuthenticationPrincipal Actor actor) {
        requirePermission(actor, "APPOINTMENT_READ");
        return appointmentRepository.findActiveClinicAppointments().stream().filter(a -> visible(a, actor)).map(a -> response(a, actor)).toList();
    }

    @PostMapping("/{id}/no-show")
    public AppointmentResponse markNoShow(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        Appointment appointment = appointmentRepository.findById(id).orElseThrow();
        if (appointment.clinicFollowUpId() == null || appointment.status() != AppointmentStatus.SCHEDULED
                || appointment.scheduledAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("Only a missed scheduled clinic appointment can be marked no-show.");
        }
        requireScoped(actor, "APPOINTMENT_CANCEL", clinics.plan(appointment.clinicFollowUpId()).departmentId());
        appointmentRepository.setStatus(id, AppointmentStatus.NO_SHOW);
        return response(appointmentRepository.findById(id).orElseThrow(), actor);
    }

    @PatchMapping("/{id}/reschedule")
    public AppointmentResponse reschedule(@PathVariable UUID id, @RequestBody RescheduleRequest request,
                                          @AuthenticationPrincipal Actor actor) {
        Appointment appointment = appointmentRepository.findById(id).orElseThrow();
        if (appointment.clinicFollowUpId() == null) throw new IllegalArgumentException("Not a clinic appointment.");
        var plan = clinics.plan(appointment.clinicFollowUpId());
        requireScoped(actor, "APPOINTMENT_RESCHEDULE", plan.departmentId());
        if (!plan.providerId().equals(actor.userId()) && !authorizationService.hasPermission(actor, "APPOINTMENT_CANCEL")) {
            throw new AccessDeniedException("Only the assigned doctor may reschedule this clinic visit.");
        }
        return response(clinics.reschedule(id, request.scheduledAt(), request.instructions()), actor);
    }

    @PostMapping("/clinic-plans/{id}/close")
    public void closeClinic(@PathVariable UUID id, @AuthenticationPrincipal Actor actor) {
        var plan = clinics.plan(id);
        requireScoped(actor, "APPOINTMENT_CREATE_FOLLOWUP", plan.departmentId());
        if (!plan.providerId().equals(actor.userId())) throw new AccessDeniedException("Only the assigned doctor may close this clinic plan.");
        clinics.close(id);
    }

    private boolean visible(Appointment appointment, Actor actor) {
        UUID departmentId = appointment.clinicFollowUpId() == null
                ? clinics.sourceDepartment(appointment.sourceEncounterId())
                : clinics.plan(appointment.clinicFollowUpId()).departmentId();
        return authorizationService.hasPermission(actor, "APPOINTMENT_READ",
                ScopeRef.of(ScopeType.DEPARTMENT, departmentId));
    }

    private AppointmentResponse response(Appointment appointment, Actor actor) {
        if (appointment.clinicFollowUpId() == null) return AppointmentResponse.from(appointment);
        var plan = clinics.plan(appointment.clinicFollowUpId());
        requireScoped(actor, "APPOINTMENT_READ", plan.departmentId());
        return AppointmentResponse.from(appointment).withClinic(plan.departmentId(), plan.clinic(), clinics.providerName(appointment.providerId()));
    }

    private void requireScoped(Actor actor, String permission, UUID departmentId) {
        if (!authorizationService.hasPermission(actor, permission, ScopeRef.of(ScopeType.DEPARTMENT, departmentId))) {
            throw new AccessDeniedException("Missing permission: " + permission);
        }
    }

    private void requirePermission(Actor actor, String permissionCode) {
        if (!authorizationService.hasPermission(actor, permissionCode)) {
            throw new AccessDeniedException("Missing permission: " + permissionCode);
        }
    }
}
