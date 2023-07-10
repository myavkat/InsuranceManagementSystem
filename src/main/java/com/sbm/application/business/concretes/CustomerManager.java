package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.CustomerService;
import com.sbm.application.core.utilities.results.*;
import com.sbm.application.entities.concretes.Customer;
import com.sbm.application.repositories.concretes.CustomerRepository;

@Service
public class CustomerManager implements CustomerService {

	@Autowired
	private CustomerRepository customerRepository;

	@Override
	public Result delete(Customer customer) {
		customerRepository.delete(customer);
		return new SuccessResult();
	}

	@Override
	public Result save(Customer customer) {
		try {
			if (customer.getId() == 0) {
				customerRepository.save(customer).block(Duration.ofSeconds(1));
				return new SuccessResult("Müşteri eklendi");
			}
			Customer foundCustomer = customerRepository.findById(customer.getId()).block(Duration.ofSeconds(1));
			if (foundCustomer == null) {
				return new ErrorResult("%s idli Müşteri bulunamadı".formatted(customer.getId()));
			}
			customerRepository.save(customer).block(Duration.ofSeconds(1));
			return new SuccessResult("Müşteri güncelleme başarılı!");
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
			return new ErrorDataResult<Customer>(new Customer(), "Müşteri Bulunamadı!");
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
			return new ErrorResult("Müşteri bulunamadı");
		}
		try {
			customerRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("Müşteri silindi");
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

}
