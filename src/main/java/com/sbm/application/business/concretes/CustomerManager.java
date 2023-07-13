package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.CustomerService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.Customer;
import com.sbm.application.entities.dtos.CustomerDetailDTO;
import com.sbm.application.repositories.concretes.CustomerRepository;

@Service
public class CustomerManager implements CustomerService {

	private final String entityName = "Müşteri";
	@Autowired
	private CustomerRepository customerRepository;

	@Override
	public Result delete(Customer customer) {
		customerRepository.delete(customer);
		return new SuccessResult("%s silindi".formatted(entityName));
	}

	@Override
	public Result save(Customer customer) {
		try {
			if (customer.getId() == 0) {
				customerRepository.save(customer).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			Customer foundCustomer = customerRepository.findById(customer.getId()).block(Duration.ofSeconds(1));
			if (foundCustomer == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(customer.getId(), entityName));
			}
			customerRepository.save(customer).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public DataResult<Customer> getById(int id) {
		Customer customer = new Customer();
		try {
			customer = customerRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<Customer>(customer, "İstek zaman aşımına uğradı!");
		}
		if (customer == null) {
			return new ErrorDataResult<Customer>(new Customer(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<Customer>(customer, "Başarılı");
	}

	@Override
	public DataResult<List<Customer>> getAll() {
		List<Customer> customers = new ArrayList<Customer>();
		try {
			customerRepository.findAll().doOnNext(customers::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<Customer>>(customers, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<Customer>>(customers, "İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result deleteById(int id) {
		var result = getById(id);
		if (!result.isSuccess()) {
			return new ErrorResult("%s bulunamadı".formatted(entityName));
		}
		try {
			customerRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<CustomerDetailDTO> getCustomerDetailById(int id) {
		CustomerDetailDTO customerDetail = new CustomerDetailDTO();
		try {
			customerDetail = customerRepository.getCustomerDetailById(id).block(Duration.ofSeconds(1));
			return new SuccessDataResult<CustomerDetailDTO>(customerDetail, "Başarılı");
		} catch (RuntimeException ex) {
			return new ErrorDataResult<CustomerDetailDTO>(customerDetail, "İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<List<CustomerDetailDTO>> getCustomerDetails() {
		List<CustomerDetailDTO> customerDetails = new ArrayList<CustomerDetailDTO>();
		try {
			customerRepository.getCustomerDetails().doOnNext(customerDetails::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<CustomerDetailDTO>>(customerDetails, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<CustomerDetailDTO>>(customerDetails, "İstek zaman aşımına uğradı!");
		}
	}

}
