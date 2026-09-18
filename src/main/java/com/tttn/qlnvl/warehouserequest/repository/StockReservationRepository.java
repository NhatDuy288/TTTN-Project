package com.tttn.qlnvl.warehouserequest.repository;

import com.tttn.qlnvl.warehouserequest.domain.MaterialCondition;
import com.tttn.qlnvl.warehouserequest.domain.StockReservation;
import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.warehouse.domain.Warehouse;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    @Query("""
            select reservation.warehouse as warehouse,
                   reservation.material as material,
                   reservation.condition as condition,
                   sum(reservation.quantity) as softReservedQuantity
            from StockReservation reservation
            where reservation.status = com.tttn.qlnvl.warehouserequest.domain.StockReservationStatus.ACTIVE_SOFT
              and reservation.warehouse.status = com.tttn.qlnvl.warehouse.domain.WarehouseStatus.ACTIVE
              and reservation.material.status = com.tttn.qlnvl.material.domain.MaterialStatus.ACTIVE
              and reservation.material.materialGroup.active = true
              and (:warehouseId is null or reservation.warehouse.id = :warehouseId)
              and (:materialGroupId is null or reservation.material.materialGroup.id = :materialGroupId)
              and (:materialId is null or reservation.material.id = :materialId)
              and reservation.condition in :conditions
            group by reservation.warehouse, reservation.material, reservation.condition
            order by reservation.material.materialCode,
                     reservation.warehouse.warehouseCode, reservation.condition
            """)
    List<DetailedInventorySoftAggregate> aggregateDetailedInventorySoftReservations(
            @Param("warehouseId") Long warehouseId,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("conditions") List<MaterialCondition> conditions);
    @Query("""
            select reservation.warehouse as warehouse,
                   reservation.material as material,
                   reservation.condition as condition,
                   sum(reservation.quantity) as softReservedQuantity
            from StockReservation reservation
            where reservation.status = com.tttn.qlnvl.warehouserequest.domain.StockReservationStatus.ACTIVE_SOFT
            group by reservation.warehouse, reservation.material, reservation.condition
            """)
    List<DailySoftReservationAggregate> aggregateActiveSoftForDailySnapshot();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation from StockReservation reservation
            where reservation.requestDetail.request.id = :requestId
              and reservation.status = com.tttn.qlnvl.warehouserequest.domain.StockReservationStatus.ACTIVE_SOFT
            order by reservation.id
            """)
    List<StockReservation> findActiveByRequestIdForUpdate(@Param("requestId") Long requestId);

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

    interface DailySoftReservationAggregate {
        Warehouse getWarehouse();
        Material getMaterial();
        MaterialCondition getCondition();
        long getSoftReservedQuantity();
    }

    interface DetailedInventorySoftAggregate {
        Warehouse getWarehouse();
        Material getMaterial();
        MaterialCondition getCondition();
        long getSoftReservedQuantity();
    }
}
