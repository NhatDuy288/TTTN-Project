package com.tttn.qlnvl.shared.audit;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    @EntityGraph(attributePaths = "changedBy")
    List<StatusHistory> findByAggregateTypeAndAggregateIdOrderByChangedAtAscIdAsc(
            AggregateType aggregateType, Long aggregateId);
}
