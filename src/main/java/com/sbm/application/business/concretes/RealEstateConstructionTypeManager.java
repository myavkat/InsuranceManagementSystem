package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.RealEstateConstructionTypeService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.RealEstateConstructionType;
import com.sbm.application.repositories.concretes.RealEstateConstructionTypeRepository;

@Service
public class RealEstateConstructionTypeManager implements RealEstateConstructionTypeService {

	private final String entityName = "Emlak Yapı Malzeme Tipi";
	Logger logger = LoggerFactory.getLogger(RealEstateUsageTypeManager.class);

	@Autowired
	private RealEstateConstructionTypeRepository constructionTypeRepository;
	@Override
	public Result save(RealEstateConstructionType entity) {
		try {
			if (entity.getId() == 0) {
				constructionTypeRepository.save(entity).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			RealEstateConstructionType foundConstructionType = constructionTypeRepository.findById(entity.getId()).block(Duration.ofSeconds(1));
			if (foundConstructionType == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(entity.getId(), entityName));
			}
			constructionTypeRepository.save(entity).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public Result delete(RealEstateConstructionType entity) {
		try {
			constructionTypeRepository.delete(entity);
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
			constructionTypeRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public DataResult<RealEstateConstructionType> getById(int id) {
		RealEstateConstructionType constructionType = new RealEstateConstructionType();
		try {
			constructionType = constructionTypeRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<RealEstateConstructionType>(constructionType, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
		if (constructionType == null) {
			return new ErrorDataResult<RealEstateConstructionType>(new RealEstateConstructionType(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<RealEstateConstructionType>(constructionType, "Başarılı");
	}

	@Override
	public DataResult<List<RealEstateConstructionType>> getAll() {
		List<RealEstateConstructionType> constructionTypes = new ArrayList<RealEstateConstructionType>();
		try {
			constructionTypeRepository.findAll().doOnNext(constructionTypes::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<RealEstateConstructionType>>(constructionTypes, "Başarılı");
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<List<RealEstateConstructionType>>(constructionTypes, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

}
