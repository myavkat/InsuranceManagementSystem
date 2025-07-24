package com.sbm.application.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sbm.application.business.abstracts.CustomerService;
import com.sbm.application.business.abstracts.EstimationService;
import com.sbm.application.business.abstracts.RealEstateService;
import com.sbm.application.business.abstracts.VehicleService;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.entities.concretes.Vehicle;
import com.sbm.application.entities.dtos.CustomerDetailDTO;

@WebMvcTest(controllers = EstimationController.class)
public class EstimationControllerTests {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private EstimationService estimationService;
	@MockitoBean
	private CustomerService customerService;
	@MockitoBean
	private VehicleService vehicleService;
	@MockitoBean
	private RealEstateService realEstateService;

	@Test
	public void contextLoads() throws Exception {
		assertThat(mockMvc).isNotNull();
		assertThat(estimationService).isNotNull();
		assertThat(customerService).isNotNull();
		assertThat(vehicleService).isNotNull();
		assertThat(realEstateService).isNotNull();
		
	}
	@Test
	public void estimateKonutCustomerSelect() throws Exception {
		List<CustomerDetailDTO> customerDetails = new ArrayList<CustomerDetailDTO>();
		CustomerDetailDTO customerDetail = new CustomerDetailDTO();
		customerDetail.setTc("12345678901");
		customerDetail.setFirstName("Ad");
		customerDetail.setLastName("Soyad");
		customerDetails.add(customerDetail);
		SuccessDataResult<List<CustomerDetailDTO>> dataResult = new SuccessDataResult<List<CustomerDetailDTO>>(customerDetails);
		when(customerService.getCustomerDetails()).thenReturn(dataResult);
		mockMvc.perform(get("/estimations/kasko"))
		.andExpect(status().isOk())
		.andExpect(content().string(containsString(customerDetail.getTc())))
		.andExpect(content().string(containsString(customerDetail.getFirstName())))
		.andExpect(content().string(containsString(customerDetail.getLastName())));
	}
	
	@Test
	public void estimateKonutVehicleSelect() throws Exception {
		List<CustomerDetailDTO> customerDetails = new ArrayList<CustomerDetailDTO>();
		CustomerDetailDTO customerDetail = new CustomerDetailDTO();
		customerDetail.setId(1);
		customerDetail.setTc("12345678901");
		customerDetail.setFirstName("Ad");
		customerDetail.setLastName("Soyad");
		customerDetails.add(customerDetail);
		SuccessDataResult<List<CustomerDetailDTO>> customerDataResult = new SuccessDataResult<List<CustomerDetailDTO>>(customerDetails);
		List<Vehicle> vehicleDetails = new ArrayList<Vehicle>();
		Vehicle vehicleDetail = new Vehicle();
		vehicleDetail.setLicensePlate("01ABC111");
		vehicleDetail.setCustomerId(customerDetail.getId());
		vehicleDetail.setCarId(1);
		vehicleDetails.add(vehicleDetail);
		SuccessDataResult<List<Vehicle>> vehicleDataResult = new SuccessDataResult<List<Vehicle>>(vehicleDetails);
		when(customerService.getCustomerDetails()).thenReturn(customerDataResult);
		when(vehicleService.getVehiclesByCustomerId(customerDetail.getId())).thenReturn(vehicleDataResult);
		mockMvc.perform(get("/estimations/kasko?customerId=" + customerDetail.getId() ))
		.andExpect(status().isOk())
		.andExpect(content().string(containsString(vehicleDetail.getLicensePlate())));
	}
}
