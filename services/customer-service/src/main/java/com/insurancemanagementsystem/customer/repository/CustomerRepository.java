package com.insurancemanagementsystem.customer.repository;

import com.insurancemanagementsystem.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Page<Customer> findByDeletedAtIsNull(Pageable pageable);

    Page<Customer> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName, String lastName, Pageable pageable);

    Page<Customer> findByNationalIdContaining(String nationalId, Pageable pageable);

    Optional<Customer> findByNationalId(String nationalId);
}
