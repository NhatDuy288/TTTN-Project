package com.tttn.qlnvl.warehouse.repository;

import com.tttn.qlnvl.warehouse.domain.Warehouse;
import com.tttn.qlnvl.warehouse.domain.WarehouseStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    List<Warehouse> findAllByOrderByWarehouseCodeAsc();
    List<Warehouse> findByStatusOrderByWarehouseCodeAsc(WarehouseStatus status);

    @EntityGraph(attributePaths = "updatedBy")
    @Query("""
            select w from Warehouse w
            where (:keyword = ''
                   or lower(w.warehouseCode) like lower(concat('%', :keyword, '%'))
                   or lower(w.warehouseName) like lower(concat('%', :keyword, '%')))
              and (:status is null or w.status = :status)
            """)
    Page<Warehouse> search(@Param("keyword") String keyword,
                           @Param("status") WarehouseStatus status,
                           Pageable pageable);

    @EntityGraph(attributePaths = "updatedBy")
    @Query("select w from Warehouse w where w.id = :id")
    Optional<Warehouse> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Warehouse w join fetch w.updatedBy where w.id = :id")
    Optional<Warehouse> findByIdForUpdate(@Param("id") Long id);

    boolean existsByWarehouseCodeIgnoreCase(String warehouseCode);
    boolean existsByWarehouseName(String warehouseName);
    boolean existsByWarehouseNameAndIdNot(String warehouseName, Long id);

    @Query(value = """
            select exists (
                select 1 from inventory_lot
                where warehouse_id = :warehouseId and on_hand_quantity > 0
            )
            """, nativeQuery = true)
    boolean hasOnHandStock(@Param("warehouseId") Long warehouseId);

    @Query(value = """
            select exists (
                select 1 from warehouse_request r
                where (r.source_warehouse_id = :warehouseId or r.destination_warehouse_id = :warehouseId)
                  and r.status not in ('COMPLETED', 'REJECTED', 'CANCELLED')
                union all
                select 1
                from warehouse_transaction t
                join warehouse_request r on r.id = t.request_id
                where (r.source_warehouse_id = :warehouseId or r.destination_warehouse_id = :warehouseId)
                  and t.warehouse_status not in ('COMPLETED', 'REJECTED')
                union all
                select 1 from warehouse_transfer t
                where (t.source_warehouse_id = :warehouseId or t.destination_warehouse_id = :warehouseId)
                  and t.status not in ('COMPLETED', 'REJECTED')
            )
            """, nativeQuery = true)
    boolean hasActiveWorkflow(@Param("warehouseId") Long warehouseId);
}
