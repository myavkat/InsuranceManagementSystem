package com.insurancemanagementsystem.customer.repository;

import com.insurancemanagementsystem.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

	Page<Customer> findByDeletedAtIsNull(Pageable pageable);

	@Query("SELECT c FROM Customer c WHERE c.deletedAt IS NULL AND "
			+ "LOWER(CONCAT(c.firstName, ' ', c.lastName)) LIKE LOWER(CONCAT('%', :search, '%'))")
	Page<Customer> findByNameSearch(@Param("search") String search, Pageable pageable);

	Page<Customer> findByNationalIdContaining(String nationalId, Pageable pageable);

	@Query("SELECT c FROM Customer c WHERE c.deletedAt IS NULL AND ("
			+ "LOWER(CONCAT(c.firstName, ' ', c.lastName)) LIKE LOWER(CONCAT('%', :search, '%')) "
			+ "OR c.nationalId LIKE CONCAT('%', :search, '%'))")
	Page<Customer> findBySearchAll(@Param("search") String search, Pageable pageable);

	Optional<Customer> findByNationalId(String nationalId);

}
