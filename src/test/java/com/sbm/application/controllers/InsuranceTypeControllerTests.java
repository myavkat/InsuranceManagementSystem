package com.sbm.application.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sbm.application.business.abstracts.InsuranceTypeService;

@WebMvcTest(controllers = InsuranceTypeController.class)
public class InsuranceTypeControllerTests {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private InsuranceTypeService insuranceTypeService;
	@Test
	public void contextLoads() {
		assertThat(mockMvc).isNotNull();
		assertThat(insuranceTypeService).isNotNull();
	}
}
