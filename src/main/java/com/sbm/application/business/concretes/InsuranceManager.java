package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.InsuranceService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.Insurance;
import com.sbm.application.repositories.concretes.InsuranceRepository;

@Service
public class InsuranceManager implements InsuranceService {

	private final String entityName = "Sigorta";
	@Autowired
	private InsuranceRepository insuranceRepository;

	@Override
	public Result save(Insurance insurance) {
		try {
			if (insurance.getId() == 0) {
				insuranceRepository.save(insurance).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			Insurance foundInsurance = insuranceRepository.findById(insurance.getId()).block(Duration.ofSeconds(1));
			if (foundInsurance == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(insurance.getId(), entityName));
			}
			insuranceRepository.save(insurance).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(Insurance insurance) {
		insuranceRepository.delete(insurance);
		return new SuccessResult();
	}

	@Override
	public DataResult<Insurance> getById(int id) {
		Insurance insurance = new Insurance();
		try {
			insurance = insuranceRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<Insurance>(insurance, "İstek zaman aşımına uğradı!");
		}
		if (insurance == null) {
			return new ErrorDataResult<Insurance>(new Insurance(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<Insurance>(insurance, "Başarılı");
	}

	@Override
	public DataResult<List<Insurance>> getAll() {
		List<Insurance> insurances = new ArrayList<Insurance>();
		try {
			insuranceRepository.findAll().doOnNext(insurances::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<Insurance>>(insurances, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<Insurance>>(insurances, "İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result deleteById(int id) {
		var result = getById(id);
		if (!result.isSuccess()) {
			return new ErrorResult("%s bulunamadı".formatted(entityName));
		}
		try {
			insuranceRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

}
