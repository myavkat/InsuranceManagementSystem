package com.sbm.application.business.abstracts;

import java.util.List;

import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.entities.concretes.Customer;
import com.sbm.application.entities.dtos.CustomerDetailDTO;


public interface CustomerService extends EntityService<Customer> {

	public DataResult<CustomerDetailDTO> getCustomerDetailById(int id);
	public DataResult<List<CustomerDetailDTO>> getCustomerDetails();
}
