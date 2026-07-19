package com.insurancemanagementsystem.realestate.repository;

import com.insurancemanagementsystem.realestate.entity.RealEstate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RealEstateRepository extends JpaRepository<RealEstate, UUID> {

	Page<RealEstate> findByCustomerId(UUID customerId, Pageable pageable);

	@Query("""
			    SELECT r FROM RealEstate r
			    WHERE LOWER(r.address) LIKE CONCAT('%', LOWER(:search), '%')
			       OR LOWER(r.district) LIKE CONCAT('%', LOWER(:search), '%')
			""")
	Page<RealEstate> search(@Param("search") String search, Pageable pageable);

	@Query("""
			    SELECT r FROM RealEstate r
			    WHERE r.customerId = :customerId
			      AND (LOWER(r.address) LIKE CONCAT('%', LOWER(:search), '%')
			           OR LOWER(r.district) LIKE CONCAT('%', LOWER(:search), '%'))
			""")
	Page<RealEstate> searchByCustomerIdAndSearch(@Param("customerId") UUID customerId, @Param("search") String search,
			Pageable pageable);

}
