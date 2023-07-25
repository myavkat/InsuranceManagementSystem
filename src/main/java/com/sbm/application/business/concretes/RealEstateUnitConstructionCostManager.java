package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.RealEstateUnitConstructionCostService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.RealEstateUnitConstructionCost;
import com.sbm.application.repositories.concretes.RealEstateUnitConstructionCostRepository;

@Service
public class RealEstateUnitConstructionCostManager implements RealEstateUnitConstructionCostService {

	private final String entityName = "Emlak Birim İnşa Maliyeti";
	Logger logger = LoggerFactory.getLogger(RealEstateUsageTypeManager.class);

	@Autowired
	private RealEstateUnitConstructionCostRepository unitConstructionCostRepository; 
	@Override
	public Result save(RealEstateUnitConstructionCost entity) {
		try {
			if (entity.getId() == 0) {
				unitConstructionCostRepository.save(entity).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			RealEstateUnitConstructionCost foundUnitConstructionCost = unitConstructionCostRepository.findById(entity.getId()).block(Duration.ofSeconds(1));
			if (foundUnitConstructionCost == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(entity.getId(), entityName));
			}
			unitConstructionCostRepository.save(entity).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public Result delete(RealEstateUnitConstructionCost entity) {
		try {
			unitConstructionCostRepository.delete(entity);
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
			unitConstructionCostRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public DataResult<RealEstateUnitConstructionCost> getById(int id) {
		RealEstateUnitConstructionCost unitConstructionCost = new RealEstateUnitConstructionCost();
		try {
			unitConstructionCost = unitConstructionCostRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<RealEstateUnitConstructionCost>(unitConstructionCost, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
		if (unitConstructionCost == null) {
			return new ErrorDataResult<RealEstateUnitConstructionCost>(new RealEstateUnitConstructionCost(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<RealEstateUnitConstructionCost>(unitConstructionCost, "Başarılı");
	}

	@Override
	public DataResult<List<RealEstateUnitConstructionCost>> getAll() {
		List<RealEstateUnitConstructionCost> unitConstructionCosts = new ArrayList<RealEstateUnitConstructionCost>();
		try {
			unitConstructionCostRepository.findAll().doOnNext(unitConstructionCosts::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<RealEstateUnitConstructionCost>>(unitConstructionCosts, "Başarılı");
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<List<RealEstateUnitConstructionCost>>(unitConstructionCosts, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

}
