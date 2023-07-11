package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.CarEngineService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.CarEngine;
import com.sbm.application.repositories.concretes.CarEngineRepository;

@Service
public class CarEngineManager implements CarEngineService {

	private final String entityName = "Araba Motoru";
	@Autowired
	private CarEngineRepository carEngineRepository;

	@Override
	public Result save(CarEngine carEngine) {
		try {
			if (carEngine.getId() == 0) {
				carEngineRepository.save(carEngine).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			CarEngine foundCarBrand = carEngineRepository.findById(carEngine.getId()).block(Duration.ofSeconds(1));
			if (foundCarBrand == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(carEngine.getId(), entityName));
			}
			carEngineRepository.save(carEngine).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(CarEngine carEngine) {
		try {
			carEngineRepository.delete(carEngine);
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
			carEngineRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<CarEngine> getById(int id) {
		CarEngine carEngine = new CarEngine();
		try {
			carEngine = carEngineRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<CarEngine>(carEngine, "İstek zaman aşımına uğradı!");
		}
		if (carEngine == null) {
			return new ErrorDataResult<CarEngine>(new CarEngine(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<CarEngine>(carEngine, "Başarılı");
	}

	@Override
	public DataResult<List<CarEngine>> getAll() {
		List<CarEngine> carEngines = new ArrayList<CarEngine>();
		try {
			carEngineRepository.findAll().doOnNext(carEngines::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<CarEngine>>(carEngines, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<CarEngine>>(carEngines, "İstek zaman aşımına uğradı!");
		}
	}

}
