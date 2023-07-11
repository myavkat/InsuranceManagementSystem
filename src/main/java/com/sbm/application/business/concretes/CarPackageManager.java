package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.CarPackageService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.CarPackage;
import com.sbm.application.repositories.concretes.CarPackageRepository;

@Service
public class CarPackageManager implements CarPackageService {
	private final String entityName = "Araba Paketi";
	@Autowired
	private CarPackageRepository carPackageRepository;

	@Override
	public Result save(CarPackage carPackage) {
		try {
			if (carPackage.getId() == 0) {
				carPackageRepository.save(carPackage).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			CarPackage foundCarBrand = carPackageRepository.findById(carPackage.getId()).block(Duration.ofSeconds(1));
			if (foundCarBrand == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(carPackage.getId(), entityName));
			}
			carPackageRepository.save(carPackage).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(CarPackage carPackage) {
		try {
			carPackageRepository.delete(carPackage);
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
			carPackageRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<CarPackage> getById(int id) {
		CarPackage carPackage = new CarPackage();
		try {
			carPackage = carPackageRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<CarPackage>(carPackage, "İstek zaman aşımına uğradı!");
		}
		if (carPackage == null) {
			return new ErrorDataResult<CarPackage>(new CarPackage(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<CarPackage>(carPackage, "Başarılı");
	}

	@Override
	public DataResult<List<CarPackage>> getAll() {
		List<CarPackage> carPackages = new ArrayList<CarPackage>();
		try {
			carPackageRepository.findAll().doOnNext(carPackages::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<CarPackage>>(carPackages, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<CarPackage>>(carPackages, "İstek zaman aşımına uğradı!");
		}
	}

}
