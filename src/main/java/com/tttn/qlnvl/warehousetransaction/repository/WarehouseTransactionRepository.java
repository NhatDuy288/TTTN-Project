package com.tttn.qlnvl.warehousetransaction.repository;

import com.tttn.qlnvl.warehousetransaction.domain.WarehouseTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseTransactionRepository extends JpaRepository<WarehouseTransaction, Long> {
}
