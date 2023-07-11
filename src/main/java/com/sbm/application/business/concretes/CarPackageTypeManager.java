package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.CarPackageTypeService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.CarPackageType;
import com.sbm.application.repositories.concretes.CarPackageTypeRepository;

@Service
public class CarPackageTypeManager implements CarPackageTypeService {
	private final String entityName = "Araba Paket Tipi";
	@Autowired
	private CarPackageTypeRepository carPackageTypeRepository;

	@Override
	public Result save(CarPackageType carPackageType) {
		try {
			if (carPackageType.getId() == 0) {
				carPackageTypeRepository.save(carPackageType).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			CarPackageType foundCarBrand = carPackageTypeRepository.findById(carPackageType.getId())
					.block(Duration.ofSeconds(1));
			if (foundCarBrand == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(carPackageType.getId(), entityName));
			}
			carPackageTypeRepository.save(carPackageType).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(CarPackageType carPackageType) {
		try {
			carPackageTypeRepository.delete(carPackageType);
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
			carPackageTypeRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<CarPackageType> getById(int id) {
		CarPackageType carPackageType = new CarPackageType();
		try {
			carPackageType = carPackageTypeRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<CarPackageType>(carPackageType, "İstek zaman aşımına uğradı!");
		}
		if (carPackageType == null) {
			return new ErrorDataResult<CarPackageType>(new CarPackageType(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<CarPackageType>(carPackageType, "Başarılı");
	}

	@Override
	public DataResult<List<CarPackageType>> getAll() {
		List<CarPackageType> carPackageTypes = new ArrayList<CarPackageType>();
		try {
			carPackageTypeRepository.findAll().doOnNext(carPackageTypes::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<CarPackageType>>(carPackageTypes, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<CarPackageType>>(carPackageTypes, "İstek zaman aşımına uğradı!");
		}
	}
}
