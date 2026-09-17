package com.tttn.qlnvl.purchaseorder.repository;

import com.tttn.qlnvl.purchaseorder.domain.PurchaseOrderItem;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from PurchaseOrderItem item where item.id in :ids order by item.id")
    List<PurchaseOrderItem> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from PurchaseOrderItem item where item.id = :id")
    Optional<PurchaseOrderItem> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select coalesce(sum(d.quantity), 0)
            from warehouse_request_detail d
            join warehouse_request r on r.id = d.request_id
            where d.purchase_order_item_id = :itemId
              and r.status in ('SUBMITTED', 'APPROVED', 'PROCESSING', 'COMPLETED')
            """, nativeQuery = true)
    long committedQuantity(@Param("itemId") Long itemId);

    @Query(value = """
            select coalesce(sum(d.quantity), 0)
            from warehouse_request_detail d
            join warehouse_transaction_detail td on td.request_detail_id = d.id
            join warehouse_transaction t on t.id = td.transaction_id
            where d.purchase_order_item_id = :itemId
              and t.warehouse_status = 'COMPLETED'
            """, nativeQuery = true)
    long receivedQuantity(@Param("itemId") Long itemId);

    @Query(value = """
            select exists (
                select 1
                from warehouse_request_detail d
                join warehouse_request r on r.id = d.request_id
                join purchase_order_item i on i.id = d.purchase_order_item_id
                where i.purchase_order_id = :purchaseOrderId
                  and r.status in ('SUBMITTED', 'APPROVED', 'PROCESSING', 'COMPLETED')
                union all
                select 1
                from warehouse_request_detail d
                join warehouse_transaction_detail td on td.request_detail_id = d.id
                join warehouse_transaction t on t.id = td.transaction_id
                join purchase_order_item i on i.id = d.purchase_order_item_id
                where i.purchase_order_id = :purchaseOrderId
                  and t.warehouse_status = 'COMPLETED'
            )
            """, nativeQuery = true)
    boolean hasCommittedOrReceivedQuantity(@Param("purchaseOrderId") Long purchaseOrderId);
}
