package com.adags.modules.laboratory.application;

import com.adags.modules.laboratory.domain.LabReferenceRange;
import com.adags.modules.laboratory.domain.LabReferenceRangeNotConfiguredException;
import com.adags.modules.laboratory.domain.LabReferenceRangeRepository;
import com.adags.modules.laboratory.domain.LabReferenceRangeResolver;
import com.adags.modules.laboratory.domain.LabResultFlagEvaluator;
import com.adags.modules.laboratory.domain.LabTestParameter;
import com.adags.modules.laboratory.domain.MissingOverrideReasonException;
import com.adags.modules.laboratory.domain.ResultFlag;
import com.adags.modules.laboratory.domain.Sex;
import com.adags.shared.kernel.PatientDemographics;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Shared by RecordLabResultService and AmendLabResultService — both are
// "enter a set of parameter values for a test" (16-lab-verification-model.md
// v0.2 §5: an amendment is a fresh entry, not a partial edit), so the
// evaluation pipeline (resolve range -> compute flag -> apply/validate
// override) is one collaborator rather than duplicated per call site.
@Component
public class LabResultAssembler {

    private final LabReferenceRangeRepository labReferenceRangeRepository;
    private final Clock clock;

    public LabResultAssembler(LabReferenceRangeRepository labReferenceRangeRepository, Clock clock) {
        this.labReferenceRangeRepository = labReferenceRangeRepository;
        this.clock = clock;
    }

    public record Evaluated(UUID parameterId, double resultValue, ResultFlag flag, boolean overridden,
                             String overrideReason, UUID appliedRangeId) {
    }

    public List<Evaluated> evaluate(List<LabTestParameter> catalogueParameters, List<ParameterEntry> submitted,
                                     PatientDemographics demographics) {
        Map<UUID, LabTestParameter> byId = catalogueParameters.stream()
                .collect(Collectors.toMap(LabTestParameter::id, Function.identity()));
        Sex patientSex = Sex.fromPatientGender(demographics.gender());
        int ageMonths = LabReferenceRangeResolver.ageInMonths(demographics.dateOfBirth(), LocalDate.now(clock));

        return submitted.stream().map(entry -> evaluateOne(byId, entry, ageMonths, patientSex)).toList();
    }

    private Evaluated evaluateOne(Map<UUID, LabTestParameter> byId, ParameterEntry entry, int ageMonths, Sex sex) {
        LabTestParameter parameter = byId.get(entry.parameterId());
        if (parameter == null) {
            throw new IllegalArgumentException(
                    "Parameter " + entry.parameterId() + " does not belong to this test's catalogue.");
        }

        List<LabReferenceRange> candidates = labReferenceRangeRepository.findCurrentByParameterId(parameter.id());
        LabReferenceRange range = LabReferenceRangeResolver.resolve(candidates, ageMonths, sex)
                .orElseThrow(() -> new LabReferenceRangeNotConfiguredException(parameter.id(), ageMonths, sex));

        ResultFlag computedFlag = LabResultFlagEvaluator.evaluate(parameter.code(), entry.resultValue(), range);

        ResultFlag finalFlag = entry.overrideFlag() != null ? entry.overrideFlag() : computedFlag;
        boolean overridden = entry.overrideFlag() != null && entry.overrideFlag() != computedFlag;
        if (overridden && (entry.overrideReason() == null || entry.overrideReason().isBlank())) {
            throw new MissingOverrideReasonException(parameter.code());
        }

        return new Evaluated(parameter.id(), entry.resultValue(), finalFlag, overridden, entry.overrideReason(), range.id());
    }
}
