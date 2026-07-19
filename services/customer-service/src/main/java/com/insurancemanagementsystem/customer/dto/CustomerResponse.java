package com.insurancemanagementsystem.customer.dto;

import com.insurancemanagementsystem.customer.entity.Customer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

	private UUID id;

	private String firstName;

	private String lastName;

	private String nationalId;

	private String email;

	private String phone;

	private LocalDate birthDate;

	private String address;

	private Integer cityId;

	private Integer professionId;

	private Instant createdAt;

	private Instant updatedAt;

	public static CustomerResponse fromEntity(Customer customer) {
		return CustomerResponse.builder()
			.id(customer.getId())
			.firstName(customer.getFirstName())
			.lastName(customer.getLastName())
			.nationalId(customer.getNationalId())
			.email(customer.getEmail())
			.phone(customer.getPhone())
			.birthDate(customer.getBirthDate())
			.address(customer.getAddress())
			.cityId(customer.getCityId())
			.professionId(customer.getProfessionId())
			.createdAt(customer.getCreatedAt())
			.updatedAt(customer.getUpdatedAt())
			.build();
	}

}
