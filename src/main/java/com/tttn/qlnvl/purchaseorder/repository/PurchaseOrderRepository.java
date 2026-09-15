package com.tttn.qlnvl.purchaseorder.repository;

import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrder;
import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    boolean existsByPoCodeIgnoreCase(String poCode);

    @EntityGraph(attributePaths = {"items", "items.material", "items.material.materialGroup"})
    @Query("select distinct p from PurchaseOrder p order by p.poCode")
    List<PurchaseOrder> findAllDetailedOrderByPoCode();

    @Query(value = """
            select p from PurchaseOrder p join fetch p.createdBy creator
            where p.createdAt >= :createdFrom and p.createdAt < :createdToExclusive
              and (:status is null or p.status = :status)
              and (:poCode = '' or lower(p.poCode) like lower(concat('%', :poCode, '%')))
              and (:createdBy = '' or lower(creator.username) like lower(concat('%', :createdBy, '%'))
                   or lower(creator.fullName) like lower(concat('%', :createdBy, '%')))
              and (:supplierName = ''
                   or lower(p.supplierName) like lower(concat('%', :supplierName, '%')))
              and (:materialGroupId is null or exists (
                   select item.id from PurchaseOrderItem item
                   where item.purchaseOrder = p and item.material.materialGroup.id = :materialGroupId))
              and (:materialId is null or exists (
                   select item.id from PurchaseOrderItem item
                   where item.purchaseOrder = p and item.material.id = :materialId))
            """,
            countQuery = """
            select count(p) from PurchaseOrder p join p.createdBy creator
            where p.createdAt >= :createdFrom and p.createdAt < :createdToExclusive
              and (:status is null or p.status = :status)
              and (:poCode = '' or lower(p.poCode) like lower(concat('%', :poCode, '%')))
              and (:createdBy = '' or lower(creator.username) like lower(concat('%', :createdBy, '%'))
                   or lower(creator.fullName) like lower(concat('%', :createdBy, '%')))
              and (:supplierName = ''
                   or lower(p.supplierName) like lower(concat('%', :supplierName, '%')))
              and (:materialGroupId is null or exists (
                   select item.id from PurchaseOrderItem item
                   where item.purchaseOrder = p and item.material.materialGroup.id = :materialGroupId))
              and (:materialId is null or exists (
                   select item.id from PurchaseOrderItem item
                   where item.purchaseOrder = p and item.material.id = :materialId))
            """)
    Page<PurchaseOrder> search(
            @Param("createdFrom") Instant createdFrom,
            @Param("createdToExclusive") Instant createdToExclusive,
            @Param("status") PurchaseOrderStatus status,
            @Param("poCode") String poCode,
            @Param("createdBy") String createdBy,
            @Param("materialGroupId") Long materialGroupId,
            @Param("materialId") Long materialId,
            @Param("supplierName") String supplierName,
            Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "items", "items.material", "items.material.materialGroup"})
    @Query("select distinct p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);
}
