package com.adags.modules.referencedata.application;

import com.adags.modules.referencedata.domain.CatalogueType;
import com.adags.modules.referencedata.domain.Icd10SearchRepository;
import com.adags.modules.referencedata.domain.Icd10SearchResult;
import com.adags.modules.referencedata.domain.ReferenceSearchLog;
import com.adags.modules.referencedata.domain.ReferenceSearchLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 10-clinical-reference-data.md §5.3 (minimum query length) + §5.4 (every
// search logged). Ranking itself is Icd10SearchRepositoryTest's job
// (real Postgres, real trigram indexes); this is the orchestration around it.
@ExtendWith(MockitoExtension.class)
class Icd10SearchServiceTest {

    @Mock
    private Icd10SearchRepository searchRepository;
    @Mock
    private ReferenceSearchLogRepository logRepository;

    private Icd10SearchService service;

    @Test
    void aQueryShorterThanTwoCharactersIsRejectedWithoutTouchingTheRepository() {
        service = new Icd10SearchService(searchRepository, logRepository);

        assertThatThrownBy(() -> service.search("m", null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(searchRepository, logRepository);
    }

    @Test
    void searchDelegatesToTheRepositoryAndLogsTheResultCount() {
        service = new Icd10SearchService(searchRepository, logRepository);
        UUID actorUserId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        List<Icd10SearchResult> results = List.of(new Icd10SearchResult("B50.9", "Malaria", true, 1000.0));
        when(searchRepository.search("malaria", departmentId, actorUserId, 20)).thenReturn(results);
        UUID logId = UUID.randomUUID();
        when(logRepository.insert(any())).thenReturn(new ReferenceSearchLog(
                logId, actorUserId, departmentId, CatalogueType.ICD10, "malaria", 1, null, Instant.now()));

        Icd10SearchOutcome outcome = service.search("malaria", departmentId, actorUserId);

        assertThat(outcome.logId()).isEqualTo(logId);
        assertThat(outcome.results()).isEqualTo(results);

        ArgumentCaptor<ReferenceSearchLog> logCaptor = ArgumentCaptor.forClass(ReferenceSearchLog.class);
        verify(logRepository).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().resultCount()).isEqualTo(1);
        assertThat(logCaptor.getValue().queryText()).isEqualTo("malaria");
        assertThat(logCaptor.getValue().selectedId()).isNull();
    }

    @Test
    void recordSelectionDelegatesToTheLogRepository() {
        service = new Icd10SearchService(searchRepository, logRepository);
        UUID logId = UUID.randomUUID();

        service.recordSelection(logId, "B50.9");

        verify(logRepository).recordSelection(logId, "B50.9");
    }
}
