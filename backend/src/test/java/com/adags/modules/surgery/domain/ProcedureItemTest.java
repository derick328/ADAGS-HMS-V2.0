package com.adags.modules.surgery.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 04-procedure-bedday-rules.md §2.1: "The system infers DEFERRED from
// phase = INTRA_OP; it is not a permission anyone holds." Plain JUnit —
// no Spring context, no I/O.
class ProcedureItemTest {

    private final UUID surgeryOrderId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();
    private final UUID preparedBy = UUID.randomUUID();

    @Test
    void freshlyPreparedItemStartsAtZeroUsedReturnedWasted() {
        ProcedureItem item = ProcedureItem.prepare(surgeryOrderId, itemId, ProcedurePhase.PRE_OP, 5, batchId, preparedBy);

        assertThat(item.id()).isNull();
        assertThat(item.quantityPrepared()).isEqualTo(5);
        assertThat(item.quantityUsed()).isZero();
        assertThat(item.quantityReturned()).isZero();
        assertThat(item.quantityWasted()).isZero();
        assertThat(item.reconciledBy()).isNull();
        assertThat(item.attestedBy()).isNull();
    }

    @Test
    void onlyIntraOpItemsAreDeferred() {
        assertThat(ProcedureItem.prepare(surgeryOrderId, itemId, ProcedurePhase.PRE_OP, 1, batchId, preparedBy)
                .isDeferred()).isFalse();
        assertThat(ProcedureItem.prepare(surgeryOrderId, itemId, ProcedurePhase.POST_OP, 1, batchId, preparedBy)
                .isDeferred()).isFalse();
        assertThat(ProcedureItem.prepare(surgeryOrderId, itemId, ProcedurePhase.INTRA_OP, 1, batchId, preparedBy)
                .isDeferred()).isTrue();
    }

    @Test
    void withIdSetsOnlyTheId() {
        ProcedureItem prepared = ProcedureItem.prepare(surgeryOrderId, itemId, ProcedurePhase.PRE_OP, 3, batchId, preparedBy);
        UUID id = UUID.randomUUID();

        ProcedureItem withId = prepared.withId(id);

        assertThat(withId.id()).isEqualTo(id);
        assertThat(withId.quantityPrepared()).isEqualTo(3);
        assertThat(withId.surgeryOrderId()).isEqualTo(surgeryOrderId);
    }
}
