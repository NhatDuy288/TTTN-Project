package com.tttn.qlnvl.warehouserequest.application;

import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import com.tttn.qlnvl.warehouserequest.domain.Reason;
import com.tttn.qlnvl.warehouserequest.repository.OperationTypeRepository;
import com.tttn.qlnvl.warehouserequest.repository.ReasonRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestConfigurationService {
    private final OperationTypeRepository operationTypeRepository;
    private final ReasonRepository reasonRepository;

    public RequestConfigurationService(
            OperationTypeRepository operationTypeRepository,
            ReasonRepository reasonRepository) {
        this.operationTypeRepository = operationTypeRepository;
        this.reasonRepository = reasonRepository;
    }

    @Transactional(readOnly = true)
    public List<OperationType> activeOperationTypes() {
        return operationTypeRepository.findDistinctByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Reason> activeReasons(OperationDirection direction) {
        if (direction == null) {
            return List.of();
        }
        return reasonRepository.findByDirectionAndActiveTrueOrderByDisplayOrderAsc(direction);
    }
}
