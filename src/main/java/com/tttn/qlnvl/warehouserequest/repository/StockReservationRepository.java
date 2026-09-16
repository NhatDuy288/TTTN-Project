package com.tttn.qlnvl.warehouserequest.repository;

import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.domain.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    @Query("""
            select coalesce(sum(reservation.quantity), 0)
            from StockReservation reservation
            where reservation.warehouse.id = :warehouseId
              and reservation.material.id = :materialId
              and reservation.condition = :condition
              and reservation.status = com.tttn.qlnvl.warehouserequest.domain.StockReservationStatus.ACTIVE_SOFT
            """)
    long activeSoftQuantity(@Param("warehouseId") Long warehouseId,
            @Param("materialId") Long materialId,
            @Param("condition") MaterialCondition condition);
}
