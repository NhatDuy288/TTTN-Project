package com.tttn.qlnvl.material.repository;

import com.tttn.qlnvl.material.domain.Material;
import com.tttn.qlnvl.material.domain.MaterialStatus;
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

public interface MaterialRepository extends JpaRepository<Material, Long> {
    @EntityGraph(attributePaths = "materialGroup")
    List<Material> findByStatusOrderByMaterialCodeAsc(MaterialStatus status);

    @EntityGraph(attributePaths = "materialGroup")
    List<Material> findAllByOrderByMaterialCodeAsc();

    @EntityGraph(attributePaths = "materialGroup")
    @Query("select m from Material m where m.id in :ids")
    List<Material> findDetailedByIdIn(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = "materialGroup")
    @Query("""
            select m from Material m
            where (:materialGroupId is null or m.materialGroup.id = :materialGroupId)
              and (:codeOrName = ''
                   or lower(m.materialCode) like lower(concat('%', :codeOrName, '%'))
                   or lower(m.materialName) like lower(concat('%', :codeOrName, '%')))
              and (:status is null or m.status = :status)
            """)
    Page<Material> search(@Param("materialGroupId") Long materialGroupId,
                          @Param("codeOrName") String codeOrName,
                          @Param("status") MaterialStatus status,
                          Pageable pageable);

    @EntityGraph(attributePaths = "materialGroup")
    @Query("select m from Material m where m.id = :id")
    Optional<Material> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Material m join fetch m.materialGroup where m.id = :id")
    Optional<Material> findByIdForUpdate(@Param("id") Long id);

    boolean existsByMaterialCode(String materialCode);
    Optional<Material> findByMaterialCode(String materialCode);
    boolean existsByGlCodeAndStatus(String glCode, MaterialStatus status);
    boolean existsByGlCodeAndStatusAndIdNot(String glCode, MaterialStatus status, Long id);

    @Query(value = """
            select exists (
                select 1 from inventory_lot
                where material_id = :materialId and on_hand_quantity > 0
            )
            """, nativeQuery = true)
    boolean hasOnHandStock(@Param("materialId") Long materialId);

    @Query(value = """
            select exists (
                select 1
                from warehouse_request_detail d
                join warehouse_request r on r.id = d.request_id
                where d.material_id = :materialId
                  and r.status not in ('COMPLETED', 'REJECTED', 'CANCELLED')
                union all
                select 1
                from warehouse_transaction_detail td
                join warehouse_request_detail d on d.id = td.request_detail_id
                join warehouse_transaction t on t.id = td.transaction_id
                where d.material_id = :materialId
                  and t.warehouse_status not in ('COMPLETED', 'REJECTED')
                union all
                select 1
                from warehouse_transfer_detail td
                join warehouse_request_detail d on d.id = td.request_detail_id
                join warehouse_transfer t on t.id = td.transfer_id
                where d.material_id = :materialId
                  and t.status not in ('COMPLETED', 'REJECTED')
            )
            """, nativeQuery = true)
    boolean hasActiveWorkflow(@Param("materialId") Long materialId);
}
