package com.insurancemanagementsystem.referencedata.exception;

import com.insurancemanagementsystem.common.web.dto.ApiResponse;
import com.insurancemanagementsystem.common.web.exception.GlobalExceptionHandler;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void handleEntityNotFound() {
		// Given
		EntityNotFoundException ex = new EntityNotFoundException("City not found");

		// When
		ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(ex);

		// Then
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().isSuccess()).isFalse();
		assertThat(response.getBody().getMessage()).isEqualTo("City not found");
	}

	@Test
	void handleValidation() {
		// Given
		MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
		BindingResult bindingResult = mock(BindingResult.class);
		when(ex.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("obj", "name", "must not be null"),
				new FieldError("obj", "code", "must not be empty")));

		// When
		ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

		// Then
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().isSuccess()).isFalse();
		assertThat(response.getBody().getMessage()).contains("name: must not be null", "code: must not be empty");
	}

	@Test
	void handleIllegalArgument() {
		// Given
		IllegalArgumentException ex = new IllegalArgumentException("Invalid plate code");

		// When
		ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(ex);

		// Then
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().isSuccess()).isFalse();
		assertThat(response.getBody().getMessage()).isEqualTo("Invalid plate code");
	}

	@Test
	void handleGeneral() {
		// Given
		Exception ex = new RuntimeException("Unexpected error");

		// When
		ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(ex);

		// Then
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().isSuccess()).isFalse();
		assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
	}

}
