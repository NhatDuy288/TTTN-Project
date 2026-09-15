package com.tttn.qlnvl.warehouserequest.application;

import com.tttn.qlnvl.material.domain.MaterialStatus;
import com.tttn.qlnvl.material.repository.MaterialGroupRepository;
import com.tttn.qlnvl.material.repository.MaterialRepository;
import com.tttn.qlnvl.purchaseorder.repository.PurchaseOrderRepository;
import com.tttn.qlnvl.warehouse.domain.WarehouseStatus;
import com.tttn.qlnvl.warehouse.repository.WarehouseRepository;
import com.tttn.qlnvl.warehouserequest.repository.OperationTypeRepository;
import com.tttn.qlnvl.warehouserequest.repository.ReasonRepository;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseRequestOptionService {
    private final OperationTypeRepository operationTypeRepository;
    private final ReasonRepository reasonRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialGroupRepository materialGroupRepository;
    private final MaterialRepository materialRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public WarehouseRequestOptionService(OperationTypeRepository operationTypeRepository,
            ReasonRepository reasonRepository, WarehouseRepository warehouseRepository,
            MaterialGroupRepository materialGroupRepository, MaterialRepository materialRepository,
            PurchaseOrderRepository purchaseOrderRepository) {
        this.operationTypeRepository = operationTypeRepository;
        this.reasonRepository = reasonRepository;
        this.warehouseRepository = warehouseRepository;
        this.materialGroupRepository = materialGroupRepository;
        this.materialRepository = materialRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Transactional(readOnly = true)
    public WarehouseRequestFormOptions load() {
        return new WarehouseRequestFormOptions(
                operationTypeRepository.findDistinctByActiveTrueOrderByDisplayOrderAsc().stream()
                        .map(operation -> new WarehouseRequestFormOptions.OperationOption(
                                operation.getId(), operation.getCode(), operation.getName(),
                                operation.getDirection(), operation.isRequiresPo(),
                                operation.getMaterialGroups().stream().map(group -> group.getId().toString())
                                        .sorted().collect(Collectors.joining(",")),
                                operation.getAllowedConditions().stream().map(Enum::name)
                                        .sorted().collect(Collectors.joining(","))))
                        .toList(),
                reasonRepository.findByActiveTrueOrderByDirectionAscDisplayOrderAsc(),
                warehouseRepository.findByStatusOrderByWarehouseCodeAsc(WarehouseStatus.ACTIVE),
                materialGroupRepository.findByActiveTrueOrderByCodeAsc(),
                materialRepository.findByStatusOrderByMaterialCodeAsc(MaterialStatus.ACTIVE),
                purchaseOrderRepository.findAllDetailedOrderByPoCode());
    }
}
