package com.tttn.qlnvl.material.repository;

import com.tttn.qlnvl.material.domain.MaterialGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialGroupRepository extends JpaRepository<MaterialGroup, Long> {
    List<MaterialGroup> findAllByOrderByCodeAsc();
    List<MaterialGroup> findByActiveTrueOrderByCodeAsc();
    Optional<MaterialGroup> findByCode(String code);
}
