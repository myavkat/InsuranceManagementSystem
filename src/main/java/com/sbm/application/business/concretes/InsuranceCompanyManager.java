package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.InsuranceCompanyService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.InsuranceCompany;
import com.sbm.application.repositories.concretes.InsuranceCompanyRepository;

@Service
public class InsuranceCompanyManager implements InsuranceCompanyService {
	private final String entityName = "Sigorta Şirketi";
	@Autowired
	private InsuranceCompanyRepository insuranceCompanyRepository;

	@Override
	public Result save(InsuranceCompany insuranceCompany) {
		try {
			if (insuranceCompany.getId() == 0) {
				insuranceCompanyRepository.save(insuranceCompany).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			InsuranceCompany foundCarBrand = insuranceCompanyRepository.findById(insuranceCompany.getId())
					.block(Duration.ofSeconds(1));
			if (foundCarBrand == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(insuranceCompany.getId(), entityName));
			}
			insuranceCompanyRepository.save(insuranceCompany).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(InsuranceCompany insuranceCompany) {
		try {
			insuranceCompanyRepository.delete(insuranceCompany);
			return new SuccessResult("Silindi");
		} catch (Exception ex) {
			return new ErrorResult(ex.getMessage());
		}
	}

	@Override
	public Result deleteById(int id) {
		var result = getById(id);
		if (!result.isSuccess()) {
			return new ErrorResult("%s bulunamadı".formatted(entityName));
		}
		try {
			insuranceCompanyRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<InsuranceCompany> getById(int id) {
		InsuranceCompany insuranceCompany = new InsuranceCompany();
		try {
			insuranceCompany = insuranceCompanyRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<InsuranceCompany>(insuranceCompany, "İstek zaman aşımına uğradı!");
		}
		if (insuranceCompany == null) {
			return new ErrorDataResult<InsuranceCompany>(new InsuranceCompany(),
					"%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<InsuranceCompany>(insuranceCompany, "Başarılı");
	}

	@Override
	public DataResult<List<InsuranceCompany>> getAll() {
		List<InsuranceCompany> insuranceCompanies = new ArrayList<InsuranceCompany>();
		try {
			insuranceCompanyRepository.findAll().doOnNext(insuranceCompanies::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<InsuranceCompany>>(insuranceCompanies, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<InsuranceCompany>>(insuranceCompanies, "İstek zaman aşımına uğradı!");
		}
	}
}
