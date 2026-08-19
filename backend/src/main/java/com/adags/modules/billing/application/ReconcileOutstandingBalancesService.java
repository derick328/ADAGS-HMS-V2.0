package com.adags.modules.billing.application;

import com.adags.modules.billing.domain.BalanceDrift;
import com.adags.modules.billing.domain.OutstandingBalanceReconciliationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 02-billing-architecture.md §9.3: "Materialised on Invoice ... with a
// nightly job recomputing from source rows and alerting on drift. Trust
// the derived value for display; trust the ledger for truth." There is no
// notification/alerting module yet, so "alert" here is a WARN-level log
// per drifted invoice — the same treatment JdbcProtocolSettingReader's
// defaulting overload already gives a missing-but-tolerable condition, so
// drift stays visible in the logs rather than silently blending in.
@Service
public class ReconcileOutstandingBalancesService {

    private static final Logger log = LoggerFactory.getLogger(ReconcileOutstandingBalancesService.class);

    private final OutstandingBalanceReconciliationRepository reconciliationRepository;

    public ReconcileOutstandingBalancesService(OutstandingBalanceReconciliationRepository reconciliationRepository) {
        this.reconciliationRepository = reconciliationRepository;
    }

    @Transactional
    public List<BalanceDrift> reconcile() {
        List<BalanceDrift> drifted = reconciliationRepository.findDrifted();
        for (BalanceDrift drift : drifted) {
            if (drift.recomputedOutstandingMinor() < 0) {
                // Never write a negative outstandingTotal (the CHECK
                // would reject it anyway) — a negative recompute means
                // the ledger itself is inconsistent, which needs a human,
                // not a silent write.
                log.error("Invoice {} recomputes to a NEGATIVE outstanding balance ({}) — ledger inconsistency, "
                                + "not corrected. Stored value left at {}.",
                        drift.invoiceId(), drift.recomputedOutstandingMinor(), drift.storedOutstandingMinor());
                continue;
            }
            reconciliationRepository.correct(drift.invoiceId(), drift.recomputedOutstandingMinor());
            log.warn("Invoice {} outstandingTotal drifted: stored={} recomputed={} — corrected.",
                    drift.invoiceId(), drift.storedOutstandingMinor(), drift.recomputedOutstandingMinor());
        }
        return drifted;
    }
}
