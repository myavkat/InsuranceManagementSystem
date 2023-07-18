package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.CarModelService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.CarModel;
import com.sbm.application.repositories.concretes.CarModelRepository;

@Service
public class CarModelManager implements CarModelService {
	private final String entityName = "Araba Modeli";
	Logger logger = LoggerFactory.getLogger(CarModelManager.class);
	@Autowired
	private CarModelRepository carModelRepository;

	@Override
	public Result save(CarModel carModel) {
		try {
			if (carModel.getId() == 0) {
				carModelRepository.save(carModel).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			CarModel foundCarBrand = carModelRepository.findById(carModel.getId()).block(Duration.ofSeconds(1));
			if (foundCarBrand == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(carModel.getId(), entityName));
			}
			carModelRepository.save(carModel).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public Result delete(CarModel carModel) {
		try {
			carModelRepository.delete(carModel);
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
			carModelRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public DataResult<CarModel> getById(int id) {
		CarModel carModel = new CarModel();
		try {
			carModel = carModelRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<CarModel>(carModel, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
		if (carModel == null) {
			return new ErrorDataResult<CarModel>(new CarModel(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<CarModel>(carModel, "Başarılı");
	}

	@Override
	public DataResult<List<CarModel>> getAll() {
		List<CarModel> carModels = new ArrayList<CarModel>();
		try {
			carModelRepository.findAll().doOnNext(carModels::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<CarModel>>(carModels, "Başarılı");
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<List<CarModel>>(carModels, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}
}
