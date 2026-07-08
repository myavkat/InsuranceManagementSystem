package com.insurancemanagementsystem.insurance.repository;

import com.insurancemanagementsystem.insurance.entity.Insurance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsuranceRepository extends JpaRepository<Insurance, UUID> {

    // All insurances (including soft-deleted)
    Page<Insurance> findAll(Pageable pageable);

    // Filter by type (including inactive)
    Page<Insurance> findByTypeId(Integer typeId, Pageable pageable);

    // Search by name (including inactive)
    @Query("SELECT i FROM Insurance i WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Insurance> searchByName(@Param("search") String search, Pageable pageable);

    // Find by name for uniqueness checks
    Optional<Insurance> findByNameIgnoreCase(String name);
}
