package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.CarBrandService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.CarBrand;
import com.sbm.application.repositories.concretes.CarBrandRepository;

@Service
public class CarBrandManager implements CarBrandService {

	private final String entityName = "Araba Markası";
	@Autowired
	private CarBrandRepository carBrandRepository;

	@Override
	public Result save(CarBrand carBrand) {
		try {
			if (carBrand.getId() == 0) {
				carBrandRepository.save(carBrand).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			CarBrand foundCarBrand = carBrandRepository.findById(carBrand.getId()).block(Duration.ofSeconds(1));
			if (foundCarBrand == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(carBrand.getId(), entityName));
			}
			carBrandRepository.save(carBrand).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(CarBrand carBrand) {
		try {
			carBrandRepository.delete(carBrand);
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
			carBrandRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<CarBrand> getById(int id) {
		CarBrand carBrand = new CarBrand();
		try {
			carBrand = carBrandRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<CarBrand>(carBrand, "İstek zaman aşımına uğradı!");
		}
		if (carBrand == null) {
			return new ErrorDataResult<CarBrand>(new CarBrand(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<CarBrand>(carBrand, "Başarılı");
	}

	@Override
	public DataResult<List<CarBrand>> getAll() {
		List<CarBrand> carBrands = new ArrayList<CarBrand>();
		try {
			carBrandRepository.findAll().doOnNext(carBrands::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<CarBrand>>(carBrands, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<CarBrand>>(carBrands, "İstek zaman aşımına uğradı!");
		}
	}

}
