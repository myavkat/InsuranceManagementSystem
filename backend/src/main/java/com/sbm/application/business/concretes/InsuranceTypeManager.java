package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.InsuranceTypeService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.InsuranceType;
import com.sbm.application.repositories.concretes.InsuranceTypeRepository;

@Service
public class InsuranceTypeManager implements InsuranceTypeService {
	private final String entityName = "Sigorta Tipi";
	Logger logger = LoggerFactory.getLogger(InsuranceTypeManager.class);
	@Autowired
	private InsuranceTypeRepository insuranceTypeRepository;

	@Override
	public Result save(InsuranceType insuranceType) {
		try {
			if (insuranceType.getId() == 0) {
				insuranceTypeRepository.save(insuranceType).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			InsuranceType foundCarBrand = insuranceTypeRepository.findById(insuranceType.getId())
					.block(Duration.ofSeconds(1));
			if (foundCarBrand == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(insuranceType.getId(), entityName));
			}
			insuranceTypeRepository.save(insuranceType).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public Result delete(InsuranceType insuranceType) {
		try {
			insuranceTypeRepository.delete(insuranceType);
			return new SuccessResult("Silindi");
		} catch (Exception ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public Result deleteById(int id) {
		var result = getById(id);
		if (!result.isSuccess()) {
			return new ErrorResult("%s bulunamadı".formatted(entityName));
		}
		try {
			insuranceTypeRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public DataResult<InsuranceType> getById(int id) {
		InsuranceType insuranceType = new InsuranceType();
		try {
			insuranceType = insuranceTypeRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<InsuranceType>(insuranceType, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
		if (insuranceType == null) {
			return new ErrorDataResult<InsuranceType>(new InsuranceType(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<InsuranceType>(insuranceType, "Başarılı");
	}

	@Override
	public DataResult<List<InsuranceType>> getAll() {
		List<InsuranceType> insuranceTypes = new ArrayList<InsuranceType>();
		try {
			insuranceTypeRepository.findAll().doOnNext(insuranceTypes::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<InsuranceType>>(insuranceTypes, "Başarılı");
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<List<InsuranceType>>(insuranceTypes, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}
}
