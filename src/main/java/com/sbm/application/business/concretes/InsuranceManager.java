package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.InsuranceService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.entities.concretes.Insurance;
import com.sbm.application.repositories.concretes.InsuranceRepository;

@Service
public class InsuranceManager implements InsuranceService {

	@Autowired
	private InsuranceRepository insuranceRepository;
	@Override
	public Result save(Insurance entity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result delete(Insurance entity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DataResult<Insurance> getById(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DataResult<List<Insurance>> getAll() {
		List<Insurance> insurances = new ArrayList<Insurance>();
		try {
		  insuranceRepository.findAll().doOnNext(insurances::add).blockLast(Duration.ofSeconds(10));
		  return new SuccessDataResult<List<Insurance>>(insurances,"Başarılı");
		}
		catch(RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<Insurance>>(insurances,"İstek zaman aşımına uğradı!");
		}
	}

}
