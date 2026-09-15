package com.tttn.qlnvl.warehouserequest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.Reason;
import com.tttn.qlnvl.warehouserequest.repository.OperationTypeRepository;
import com.tttn.qlnvl.warehouserequest.repository.ReasonRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestConfigurationServiceTest {
    private OperationTypeRepository operationTypeRepository;
    private ReasonRepository reasonRepository;
    private RequestConfigurationService service;

    @BeforeEach
    void setUp() {
        operationTypeRepository = mock(OperationTypeRepository.class);
        reasonRepository = mock(ReasonRepository.class);
        service = new RequestConfigurationService(operationTypeRepository, reasonRepository);
    }

    @Test
    void loadsOnlyActiveOperationTypesUsingConfiguredOrder() {
        List<OperationType> expected = List.of(mock(OperationType.class));
        when(operationTypeRepository.findDistinctByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(expected);

        assertThat(service.activeOperationTypes()).isSameAs(expected);
        verify(operationTypeRepository).findDistinctByActiveTrueOrderByDisplayOrderAsc();
    }

    @Test
    void filtersReasonsByOperationDirection() {
        List<Reason> expected = List.of(mock(Reason.class));
        when(reasonRepository.findByDirectionAndActiveTrueOrderByDisplayOrderAsc(
                OperationDirection.IMPORT)).thenReturn(expected);

        assertThat(service.activeReasons(OperationDirection.IMPORT)).isSameAs(expected);
        verify(reasonRepository).findByDirectionAndActiveTrueOrderByDisplayOrderAsc(
                OperationDirection.IMPORT);
    }

    @Test
    void nullDirectionReturnsNoReasonsWithoutQueryingRepository() {
        assertThat(service.activeReasons(null)).isEmpty();
        verifyNoInteractions(reasonRepository);
    }
}
