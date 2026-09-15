package com.tttn.qlnvl.warehouserequest.repository;

import com.tttn.qlnvl.warehouserequest.domain.OperationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationTypeRepository extends JpaRepository<OperationType, Long> {
    @EntityGraph(attributePaths = {"materialGroups", "allowedConditions"})
    List<OperationType> findDistinctByActiveTrueOrderByDisplayOrderAsc();

    @EntityGraph(attributePaths = {"materialGroups", "allowedConditions"})
    Optional<OperationType> findDetailedByIdAndActiveTrue(Long id);
}
