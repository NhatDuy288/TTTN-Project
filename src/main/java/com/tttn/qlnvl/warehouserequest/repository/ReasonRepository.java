package com.tttn.qlnvl.warehouserequest.repository;

import com.tttn.qlnvl.warehouserequest.domain.OperationDirection;
import com.tttn.qlnvl.warehouserequest.domain.Reason;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReasonRepository extends JpaRepository<Reason, Long> {
    List<Reason> findByDirectionAndActiveTrueOrderByDisplayOrderAsc(OperationDirection direction);
}
