package com.tttn.qlnvl.warehouserequest.repository;

import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequest;
import com.tttn.qlnvl.warehouserequest.domain.WarehouseRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseRequestRepository extends JpaRepository<WarehouseRequest, Long> {
    @EntityGraph(attributePaths = {"createdBy", "operationType", "sourceWarehouse", "destinationWarehouse"})
    Page<WarehouseRequest> findByCreatedById(Long actorId, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "operationType", "sourceWarehouse", "destinationWarehouse"})
    Page<WarehouseRequest> findByCreatedByIdAndStatus(Long actorId,
            WarehouseRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "operationType", "sourceWarehouse", "destinationWarehouse"})
    Page<WarehouseRequest> findByCreatedByIdAndRequestCodeContainingIgnoreCase(Long actorId,
            String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "operationType", "sourceWarehouse", "destinationWarehouse"})
    Page<WarehouseRequest> findByCreatedByIdAndRequestCodeContainingIgnoreCaseAndStatus(
            Long actorId, String keyword, WarehouseRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "operationType", "sourceWarehouse", "destinationWarehouse"})
    @Query("select r from WarehouseRequest r where r.status = :status")
    Page<WarehouseRequest> findApprovalQueue(@Param("status") WarehouseRequestStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "operationType", "operationType.materialGroups",
            "operationType.allowedConditions", "reason", "sourceWarehouse", "destinationWarehouse",
            "purchaseOrder", "details", "details.material", "details.material.materialGroup",
            "details.purchaseOrderItem"})
    @Query("select distinct r from WarehouseRequest r where r.id = :id")
    Optional<WarehouseRequest> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WarehouseRequest r where r.id = :id")
    Optional<WarehouseRequest> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query(value = "LOCK TABLE warehouse_request IN SHARE ROW EXCLUSIVE MODE", nativeQuery = true)
    void lockForCodeGeneration();

    @Query(value = """
            select coalesce(max(cast(right(request_code, 3) as integer)), 0)
            from warehouse_request where request_code like concat(:prefixDate, '_%')
            """, nativeQuery = true)
    int maxSequenceFor(@Param("prefixDate") String prefixDate);
}
