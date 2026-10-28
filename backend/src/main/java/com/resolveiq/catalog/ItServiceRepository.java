package com.resolveiq.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ItServiceRepository extends JpaRepository<ItService, Long> {

    Optional<ItService> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("select distinct s from ItService s left join fetch s.dependencies order by s.name")
    List<ItService> findAllWithDependencies();
}
