package com.insurancemanagementsystem.customer.service;

import com.insurancemanagementsystem.customer.config.CustomerEventPublisher;
import com.insurancemanagementsystem.customer.dto.CustomerRequest;
import com.insurancemanagementsystem.customer.dto.CustomerResponse;
import com.insurancemanagementsystem.customer.entity.Customer;
import com.insurancemanagementsystem.customer.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

	private final CustomerRepository customerRepository;

	private final CustomerEventPublisher customerEventPublisher;

	@Transactional(readOnly = true)
	public Page<CustomerResponse> findAll(Pageable pageable) {
		return customerRepository.findByDeletedAtIsNull(pageable).map(CustomerResponse::fromEntity);
	}

	@Transactional(readOnly = true)
	public Page<CustomerResponse> search(String name, String nationalId, Pageable pageable) {
		boolean hasName = name != null && !name.isBlank();
		boolean hasNationalId = nationalId != null && !nationalId.isBlank();

		if (hasName && hasNationalId) {
			return customerRepository.findBySearchAll(name, pageable).map(CustomerResponse::fromEntity);
		}
		else if (hasName) {
			return customerRepository.findBySearchAll(name, pageable).map(CustomerResponse::fromEntity);
		}
		else if (hasNationalId) {
			return customerRepository.findByNationalIdContaining(nationalId, pageable)
				.map(CustomerResponse::fromEntity);
		}
		else {
			return findAll(pageable);
		}
	}

	@Transactional(readOnly = true)
	public CustomerResponse findById(UUID id) {
		Customer customer = customerRepository.findById(id)
			.filter(c -> c.getDeletedAt() == null)
			.orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + id));
		return CustomerResponse.fromEntity(customer);
	}

	@Transactional
	public CustomerResponse create(CustomerRequest request) {
		customerRepository.findByNationalId(request.getNationalId().trim()).ifPresent(_ -> {
			throw new IllegalArgumentException(
					"Customer with national ID " + request.getNationalId() + " already exists");
		});

		Customer customer = Customer.builder()
			.firstName(request.getFirstName().trim())
			.lastName(request.getLastName().trim())
			.nationalId(request.getNationalId().trim())
			.email(request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null)
			.phone(request.getPhone())
			.birthDate(request.getBirthDate())
			.address(request.getAddress())
			.cityId(request.getCityId())
			.professionId(request.getProfessionId())
			.build();

		Customer savedCustomer = customerRepository.save(customer);
		log.info("Customer created with id: {} and nationalId: {}", savedCustomer.getId(),
				savedCustomer.getNationalId());
		customerEventPublisher.publishCustomerCreated(savedCustomer);
		return CustomerResponse.fromEntity(savedCustomer);
	}

	@Transactional
	public CustomerResponse update(UUID id, CustomerRequest request) {
		Customer customer = customerRepository.findById(id)
			.filter(c -> c.getDeletedAt() == null)
			.orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + id));

		customer.setFirstName(request.getFirstName().trim());
		customer.setLastName(request.getLastName().trim());

		if (!customer.getNationalId().equals(request.getNationalId().trim())) {
			customerRepository.findByNationalId(request.getNationalId().trim()).ifPresent(_ -> {
				throw new IllegalArgumentException(
						"Customer with national ID " + request.getNationalId() + " already exists");
			});
		}
		customer.setNationalId(request.getNationalId().trim());
		customer.setEmail(request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null);
		customer.setPhone(request.getPhone());
		customer.setBirthDate(request.getBirthDate());
		customer.setAddress(request.getAddress());
		customer.setCityId(request.getCityId());
		customer.setProfessionId(request.getProfessionId());

		Customer savedCustomer = customerRepository.save(customer);
		log.info("Customer updated with id: {}", savedCustomer.getId());
		customerEventPublisher.publishCustomerUpdated(savedCustomer);
		return CustomerResponse.fromEntity(savedCustomer);
	}

	@Transactional
	public CustomerResponse softDelete(UUID id) {
		Customer customer = customerRepository.findById(id)
			.filter(c -> c.getDeletedAt() == null)
			.orElseThrow(() -> new EntityNotFoundException("Customer not found with id: " + id));

		// TODO: Check for active estimations before soft-delete when Estimation Service
		// exists
		log.warn("Active estimation check skipped — Estimation Service not yet available");

		customer.setDeletedAt(Instant.now());
		Customer savedCustomer = customerRepository.save(customer);
		log.info("Customer soft-deleted with id: {}", savedCustomer.getId());
		customerEventPublisher.publishCustomerDeleted(savedCustomer);
		return CustomerResponse.fromEntity(savedCustomer);
	}

}
