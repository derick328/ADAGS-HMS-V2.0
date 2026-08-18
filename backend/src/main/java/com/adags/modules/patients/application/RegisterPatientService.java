package com.adags.modules.patients.application;

import com.adags.modules.patients.domain.DuplicateCandidate;
import com.adags.modules.patients.domain.DuplicateCheckQuery;
import com.adags.modules.patients.domain.InsurancePolicy;
import com.adags.modules.patients.domain.NextOfKin;
import com.adags.modules.patients.domain.Patient;
import com.adags.modules.patients.domain.PatientAddress;
import com.adags.modules.patients.domain.PatientRepository;
import com.adags.shared.authz.AuditRecorder;
import com.adags.shared.events.DomainEventPublisher;
import com.adags.shared.numbering.GaplessNumberAllocator;
import com.adags.shared.settings.ProtocolSettingReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 05-receptionist.md §4.3: duplicate check, hospital number, Patient +
// NextOfKin + PatientAddress[] + InsurancePolicy, audit entry, event — one
// transaction. Registration does not open an encounter (that module doesn't
// exist yet in this slice, and wouldn't belong here even if it did).
@Service
public class RegisterPatientService {

    private static final String HOSPITAL_NUMBER_SEQUENCE = "HOSPITAL_NO";
    private static final int HOSPITAL_NUMBER_PAD_WIDTH = 6;

    // No fallback constants: V7__protocol_setting.sql seeds both rows, so a
    // missing one means the table is misconfigured or was wiped, not that
    // a default is wanted — protocolSettings.getDouble(key) throws rather
    // than silently substituting a value (see backend/CLAUDE.md for the
    // similarity-score evidence behind these numbers).
    private static final String LOW_SIMILARITY_THRESHOLD_KEY = "patients.duplicate_detection.low_similarity_threshold";
    private static final String HIGH_SIMILARITY_THRESHOLD_KEY = "patients.duplicate_detection.high_similarity_threshold";

    private final PatientRepository patientRepository;
    private final GaplessNumberAllocator numberAllocator;
    private final AuditRecorder auditRecorder;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;
    private final ProtocolSettingReader protocolSettings;

    public RegisterPatientService(
            PatientRepository patientRepository,
            GaplessNumberAllocator numberAllocator,
            AuditRecorder auditRecorder,
            DomainEventPublisher eventPublisher,
            Clock clock,
            ProtocolSettingReader protocolSettings
    ) {
        this.patientRepository = patientRepository;
        this.numberAllocator = numberAllocator;
        this.auditRecorder = auditRecorder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.protocolSettings = protocolSettings;
    }

    @Transactional
    public Patient register(RegisterPatientCommand command) {
        LocalDate dateOfBirth = resolveDateOfBirth(command);
        boolean dobIsEstimated = command.dateOfBirth() == null;

        checkForDuplicates(command, dateOfBirth);

        String hospitalNo = numberAllocator.allocate(
                HOSPITAL_NUMBER_SEQUENCE, command.facilityId(), LocalDate.now(clock).getYear(), HOSPITAL_NUMBER_PAD_WIDTH);

        Patient toInsert = new Patient(null, hospitalNo, command.givenName(), command.middleName(),
                command.familyName(), dateOfBirth, dobIsEstimated, command.gender(), command.maritalStatus(),
                command.nationalId(), command.phone(), command.email(), command.occupation(), false, true, null);
        Patient saved = patientRepository.insert(toInsert, command.actorUserId());

        patientRepository.insertNextOfKin(withPatientId(command.nextOfKin(), saved.id()));
        for (PatientAddress address : command.addresses()) {
            patientRepository.insertAddress(withPatientId(address, saved.id()));
        }
        if (command.insurancePolicy() != null) {
            patientRepository.insertInsurancePolicy(withPatientId(command.insurancePolicy(), saved.id()));
        }

        auditRecorder.record(command.actorUserId(), "PATIENT_REGISTERED", "PATIENT", saved.id(), saved.id());
        eventPublisher.publish("PatientRegistered", Map.of(
                "patientId", saved.id().toString(),
                "hospitalNo", saved.hospitalNo()
        ));

        return saved;
    }

    private void checkForDuplicates(RegisterPatientCommand command, LocalDate dateOfBirth) {
        double lowSimilarityThreshold = protocolSettings.getDouble(LOW_SIMILARITY_THRESHOLD_KEY);
        double highSimilarityThreshold = protocolSettings.getDouble(HIGH_SIMILARITY_THRESHOLD_KEY);
        DuplicateCheckQuery query = new DuplicateCheckQuery(
                command.givenName(), command.familyName(), dateOfBirth,
                command.nationalId(), command.phone(), lowSimilarityThreshold, highSimilarityThreshold);
        List<DuplicateCandidate> candidates = patientRepository.findDuplicateCandidates(query);

        // National-ID exact match is never overridable here — 05-receptionist.md
        // §4.1 directs staff to the existing record, not through this path.
        boolean hardBlock = candidates.stream().anyMatch(DuplicateCandidate::blocksUnconditionally);
        boolean softBlock = candidates.stream().anyMatch(DuplicateCandidate::blocks)
                && (command.notSamePersonConfirmationReason() == null || command.notSamePersonConfirmationReason().isBlank());

        if (hardBlock || softBlock) {
            throw new DuplicatePatientException(candidates);
        }
    }

    private LocalDate resolveDateOfBirth(RegisterPatientCommand command) {
        if (command.dateOfBirth() != null) {
            return command.dateOfBirth();
        }
        if (command.ageYears() == null) {
            throw new IllegalArgumentException("Either dateOfBirth or ageYears must be provided.");
        }
        int months = command.ageMonths() == null ? 0 : command.ageMonths();
        return LocalDate.now(clock).minusYears(command.ageYears()).minusMonths(months);
    }

    private NextOfKin withPatientId(NextOfKin nextOfKin, UUID patientId) {
        return new NextOfKin(null, patientId, nextOfKin.fullName(), nextOfKin.relationship(),
                nextOfKin.phone(), nextOfKin.isGuardian());
    }

    private PatientAddress withPatientId(PatientAddress address, UUID patientId) {
        return new PatientAddress(null, patientId, address.addressType(), address.region(),
                address.district(), address.ward(), address.streetOrVillage(), address.landmark());
    }

    private InsurancePolicy withPatientId(InsurancePolicy policy, UUID patientId) {
        return new InsurancePolicy(null, patientId, policy.schemeId(), policy.policyNumber(),
                policy.memberNumber(), policy.isPrincipal(), policy.principalName(),
                policy.validFrom(), policy.validTo(), policy.verificationStatus(), policy.verifiedAt());
    }
}
