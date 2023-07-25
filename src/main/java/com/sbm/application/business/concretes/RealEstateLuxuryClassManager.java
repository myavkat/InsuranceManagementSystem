package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.RealEstateLuxuryClassService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.RealEstateLuxuryClass;
import com.sbm.application.repositories.concretes.RealEstateLuxuryClassRepository;

@Service
public class RealEstateLuxuryClassManager implements RealEstateLuxuryClassService {

	private final String entityName = "Emlak Kalite Sınıfı";
	Logger logger = LoggerFactory.getLogger(RealEstateUsageTypeManager.class);

	@Autowired
	private RealEstateLuxuryClassRepository luxuryClassRepository;
	@Override
	public Result save(RealEstateLuxuryClass entity) {
		try {
			if (entity.getId() == 0) {
				luxuryClassRepository.save(entity).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			RealEstateLuxuryClass foundLuxuryClass = luxuryClassRepository.findById(entity.getId()).block(Duration.ofSeconds(1));
			if (foundLuxuryClass == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(entity.getId(), entityName));
			}
			luxuryClassRepository.save(entity).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public Result delete(RealEstateLuxuryClass entity) {
		try {
			luxuryClassRepository.delete(entity);
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
			luxuryClassRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public DataResult<RealEstateLuxuryClass> getById(int id) {
		RealEstateLuxuryClass luxuryClass = new RealEstateLuxuryClass();
		try {
			luxuryClass = luxuryClassRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<RealEstateLuxuryClass>(luxuryClass, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
		if (luxuryClass == null) {
			return new ErrorDataResult<RealEstateLuxuryClass>(new RealEstateLuxuryClass(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<RealEstateLuxuryClass>(luxuryClass, "Başarılı");
	}

	@Override
	public DataResult<List<RealEstateLuxuryClass>> getAll() {
		List<RealEstateLuxuryClass> luxuryClasses = new ArrayList<RealEstateLuxuryClass>();
		try {
			luxuryClassRepository.findAll().doOnNext(luxuryClasses::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<RealEstateLuxuryClass>>(luxuryClasses, "Başarılı");
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<List<RealEstateLuxuryClass>>(luxuryClasses, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

}
