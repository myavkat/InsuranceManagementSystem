package com.insurancemanagementsystem.customer.service;

import com.insurancemanagementsystem.customer.config.CustomerEventPublisher;
import com.insurancemanagementsystem.customer.dto.CustomerRequest;
import com.insurancemanagementsystem.customer.dto.CustomerResponse;
import com.insurancemanagementsystem.customer.entity.Customer;
import com.insurancemanagementsystem.customer.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerEventPublisher customerEventPublisher;

    @InjectMocks
    private CustomerService customerService;

    @Captor
    private ArgumentCaptor<Customer> customerCaptor;

    private static final UUID TEST_ID = UUID.randomUUID();
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final String NATIONAL_ID = "12345678901";
    private static final String EMAIL = "john.doe@example.com";
    private static final String PHONE = "+905551234567";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String ADDRESS = "123 Main St";
    private static final Integer CITY_ID = 34;
    private static final Integer PROFESSION_ID = 1;

    private CustomerRequest createValidRequest() {
        CustomerRequest request = new CustomerRequest();
        request.setFirstName(FIRST_NAME);
        request.setLastName(LAST_NAME);
        request.setNationalId(NATIONAL_ID);
        request.setEmail(EMAIL);
        request.setPhone(PHONE);
        request.setBirthDate(BIRTH_DATE);
        request.setAddress(ADDRESS);
        request.setCityId(CITY_ID);
        request.setProfessionId(PROFESSION_ID);
        return request;
    }

    private Customer createCustomer(UUID id, String nationalIdValue, Instant deletedAt) {
        return Customer.builder()
                .id(id)
                .firstName(FIRST_NAME)
                .lastName(LAST_NAME)
                .nationalId(nationalIdValue)
                .email(EMAIL)
                .phone(PHONE)
                .birthDate(BIRTH_DATE)
                .address(ADDRESS)
                .cityId(CITY_ID)
                .professionId(PROFESSION_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .deletedAt(deletedAt)
                .build();
    }

    // ---------------------------------------------------------------
    // 1. create – valid data
    // ---------------------------------------------------------------
    @Test
    void createCustomerWithValidData_ReturnsSavedEntity() {
        // Arrange
        CustomerRequest request = createValidRequest();
        Customer savedCustomer = createCustomer(TEST_ID, NATIONAL_ID, null);

        when(customerRepository.findByNationalId(NATIONAL_ID.trim())).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenReturn(savedCustomer);

        // Act
        CustomerResponse response = customerService.create(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getFirstName()).isEqualTo(FIRST_NAME);
        assertThat(response.getLastName()).isEqualTo(LAST_NAME);
        assertThat(response.getNationalId()).isEqualTo(NATIONAL_ID);
        assertThat(response.getEmail()).isEqualTo(EMAIL);
        assertThat(response.getPhone()).isEqualTo(PHONE);
        assertThat(response.getBirthDate()).isEqualTo(BIRTH_DATE);
        assertThat(response.getAddress()).isEqualTo(ADDRESS);
        assertThat(response.getCityId()).isEqualTo(CITY_ID);
        assertThat(response.getProfessionId()).isEqualTo(PROFESSION_ID);

        verify(customerRepository).findByNationalId(NATIONAL_ID.trim());
        verify(customerRepository).save(any(Customer.class));
        verify(customerEventPublisher).publishCustomerCreated(savedCustomer);
    }

    // ---------------------------------------------------------------
    // 2. create – duplicate national ID
    // ---------------------------------------------------------------
    @Test
    void createCustomerWithDuplicateNationalId_ThrowsIllegalArgumentException() {
        // Arrange
        CustomerRequest request = createValidRequest();
        Customer existingCustomer = createCustomer(UUID.randomUUID(), NATIONAL_ID, null);

        when(customerRepository.findByNationalId(NATIONAL_ID.trim())).thenReturn(Optional.of(existingCustomer));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> customerService.create(request));
        assertThat(exception.getMessage()).contains("already exists");

        verify(customerRepository).findByNationalId(NATIONAL_ID.trim());
        verify(customerRepository, never()).save(any(Customer.class));
        verify(customerEventPublisher, never()).publishCustomerCreated(any(Customer.class));
    }

    // ---------------------------------------------------------------
    // 3. findById – not found
    // ---------------------------------------------------------------
    @Test
    void findById_NotFound_ThrowsEntityNotFoundException() {
        // Arrange
        when(customerRepository.findById(TEST_ID)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> customerService.findById(TEST_ID));
        assertThat(exception.getMessage()).contains("Customer not found");

        verify(customerRepository).findById(TEST_ID);
    }

    // ---------------------------------------------------------------
    // 4. findById – soft-deleted
    // ---------------------------------------------------------------
    @Test
    void findById_SoftDeleted_ThrowsEntityNotFoundException() {
        // Arrange
        Customer deletedCustomer = createCustomer(TEST_ID, NATIONAL_ID, Instant.now());

        when(customerRepository.findById(TEST_ID)).thenReturn(Optional.of(deletedCustomer));

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> customerService.findById(TEST_ID));
        assertThat(exception.getMessage()).contains("Customer not found");

        verify(customerRepository).findById(TEST_ID);
    }

    // ---------------------------------------------------------------
    // 5. softDelete – sets deletedAt
    // ---------------------------------------------------------------
    @Test
    void softDelete_SetsDeletedAt() {
        // Arrange
        Customer customer = createCustomer(TEST_ID, NATIONAL_ID, null);

        when(customerRepository.findById(TEST_ID)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CustomerResponse response = customerService.softDelete(TEST_ID);

        // Assert
        verify(customerRepository).findById(TEST_ID);
        verify(customerRepository).save(customerCaptor.capture());

        Customer savedCustomer = customerCaptor.getValue();
        assertThat(savedCustomer.getDeletedAt()).isNotNull();

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(TEST_ID);
        assertThat(response.getFirstName()).isEqualTo(FIRST_NAME);

        verify(customerEventPublisher, never()).publishCustomerCreated(any(Customer.class));
        verify(customerEventPublisher, never()).publishCustomerUpdated(any(Customer.class));
    }

    // ---------------------------------------------------------------
    // 6. update – fields updated
    // ---------------------------------------------------------------
    @Test
    void updateCustomer_FieldsUpdated() {
        // Arrange
        Customer existingCustomer = createCustomer(TEST_ID, "98765432109", null);
        CustomerRequest request = createValidRequest();

        when(customerRepository.findById(TEST_ID)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.findByNationalId(NATIONAL_ID.trim())).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CustomerResponse response = customerService.update(TEST_ID, request);

        // Assert
        verify(customerRepository).findById(TEST_ID);
        verify(customerRepository).findByNationalId(NATIONAL_ID.trim());
        verify(customerRepository).save(customerCaptor.capture());

        Customer updatedCustomer = customerCaptor.getValue();
        assertThat(updatedCustomer.getFirstName()).isEqualTo(FIRST_NAME);
        assertThat(updatedCustomer.getLastName()).isEqualTo(LAST_NAME);
        assertThat(updatedCustomer.getNationalId()).isEqualTo(NATIONAL_ID);
        assertThat(updatedCustomer.getEmail()).isEqualTo(EMAIL);
        assertThat(updatedCustomer.getPhone()).isEqualTo(PHONE);
        assertThat(updatedCustomer.getBirthDate()).isEqualTo(BIRTH_DATE);
        assertThat(updatedCustomer.getAddress()).isEqualTo(ADDRESS);
        assertThat(updatedCustomer.getCityId()).isEqualTo(CITY_ID);
        assertThat(updatedCustomer.getProfessionId()).isEqualTo(PROFESSION_ID);

        verify(customerEventPublisher).publishCustomerUpdated(updatedCustomer);

        assertThat(response).isNotNull();
        assertThat(response.getFirstName()).isEqualTo(FIRST_NAME);
        assertThat(response.getLastName()).isEqualTo(LAST_NAME);
        assertThat(response.getNationalId()).isEqualTo(NATIONAL_ID);
    }

    // ---------------------------------------------------------------
    // 7. search – by name
    // ---------------------------------------------------------------
    @Test
    void searchByName_ReturnsMatchingResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Customer customer = createCustomer(TEST_ID, NATIONAL_ID, null);
        Page<Customer> customerPage = new PageImpl<>(List.of(customer));

        when(customerRepository.findByNameSearch("Doe", pageable)).thenReturn(customerPage);

        // Act
        Page<CustomerResponse> result = customerService.search("Doe", null, pageable);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFirstName()).isEqualTo(FIRST_NAME);
        assertThat(result.getContent().get(0).getLastName()).isEqualTo(LAST_NAME);

        verify(customerRepository).findByNameSearch("Doe", pageable);
        verify(customerRepository, never()).findByNationalIdContaining(anyString(), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // 8. search – by national ID
    // ---------------------------------------------------------------
    @Test
    void searchByNationalId_ReturnsMatchingResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Customer customer = createCustomer(TEST_ID, NATIONAL_ID, null);
        Page<Customer> customerPage = new PageImpl<>(List.of(customer));

        when(customerRepository.findByNationalIdContaining(NATIONAL_ID, pageable)).thenReturn(customerPage);

        // Act
        Page<CustomerResponse> result = customerService.search(null, NATIONAL_ID, pageable);

        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getNationalId()).isEqualTo(NATIONAL_ID);

        verify(customerRepository).findByNationalIdContaining(NATIONAL_ID, pageable);
        verify(customerRepository, never()).findByNameSearch(anyString(), any(Pageable.class));
    }
}
