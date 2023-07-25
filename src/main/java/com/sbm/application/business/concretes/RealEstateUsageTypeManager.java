package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.RealEstateUsageTypeService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.RealEstateUsageType;
import com.sbm.application.repositories.concretes.RealEstateUsageTypeRepository;

@Service
public class RealEstateUsageTypeManager implements RealEstateUsageTypeService {

	private final String entityName = "Emlak Kullanım Tipi";
	Logger logger = LoggerFactory.getLogger(RealEstateUsageTypeManager.class);
	@Autowired
	private RealEstateUsageTypeRepository usageTypeRepository;
	@Override
	public Result save(RealEstateUsageType entity) {
		try {
			if (entity.getId() == 0) {
				usageTypeRepository.save(entity).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			RealEstateUsageType foundUsageType = usageTypeRepository.findById(entity.getId()).block(Duration.ofSeconds(1));
			if (foundUsageType == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(entity.getId(), entityName));
			}
			usageTypeRepository.save(entity).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public Result delete(RealEstateUsageType entity) {
		try {
			usageTypeRepository.delete(entity);
			return new SuccessResult("Başarılı");
		} catch (RuntimeException ex) {
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
			usageTypeRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public DataResult<RealEstateUsageType> getById(int id) {
		RealEstateUsageType usageType = new RealEstateUsageType();
		try {
			usageType = usageTypeRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<RealEstateUsageType>(usageType, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
		if (usageType == null) {
			return new ErrorDataResult<RealEstateUsageType>(new RealEstateUsageType(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<RealEstateUsageType>(usageType, "Başarılı");
	}

	@Override
	public DataResult<List<RealEstateUsageType>> getAll() {
		List<RealEstateUsageType> usageTypes = new ArrayList<RealEstateUsageType>();
		try {
			usageTypeRepository.findAll().doOnNext(usageTypes::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<RealEstateUsageType>>(usageTypes, "Başarılı");
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<List<RealEstateUsageType>>(usageTypes, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

}
