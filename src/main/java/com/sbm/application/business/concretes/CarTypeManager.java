package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.CarTypeService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.CarType;
import com.sbm.application.entities.concretes.CarType;
import com.sbm.application.repositories.concretes.CarTypeRepository;

@Service
public class CarTypeManager implements CarTypeService {
	private final String entityName = "Araba Tipi";
	@Autowired
	private CarTypeRepository carTypeRepository;

	@Override
	public Result save(CarType carType) {
		try {
			if (carType.getId() == 0) {
				carTypeRepository.save(carType).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			CarType foundCarBrand = carTypeRepository.findById(carType.getId()).block(Duration.ofSeconds(1));
			if (foundCarBrand == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(carType.getId(), entityName));
			}
			carTypeRepository.save(carType).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(CarType carType) {
		try {
			carTypeRepository.delete(carType);
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
			carTypeRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<CarType> getById(int id) {
		CarType carType = new CarType();
		try {
			carType = carTypeRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<CarType>(carType, "İstek zaman aşımına uğradı!");
		}
		if (carType == null) {
			return new ErrorDataResult<CarType>(new CarType(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<CarType>(carType, "Başarılı");
	}

	@Override
	public DataResult<List<CarType>> getAll() {
		List<CarType> carTypes = new ArrayList<CarType>();
		try {
			carTypeRepository.findAll().doOnNext(carTypes::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<CarType>>(carTypes, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<CarType>>(carTypes, "İstek zaman aşımına uğradı!");
		}
	}
}
