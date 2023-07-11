package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.CarEngineSizeService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.CarEngineSize;
import com.sbm.application.repositories.concretes.CarEngineSizeRepository;

@Service
public class CarEngineSizeManager implements CarEngineSizeService {

	private final String entityName = "Araba Motoru Büyüklüğü";
	@Autowired
	private CarEngineSizeRepository carEngineSizeRepository;

	@Override
	public Result save(CarEngineSize carEngineSize) {
		try {
			if (carEngineSize.getId() == 0) {
				carEngineSizeRepository.save(carEngineSize).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			CarEngineSize foundCarBrand = carEngineSizeRepository.findById(carEngineSize.getId())
					.block(Duration.ofSeconds(1));
			if (foundCarBrand == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(carEngineSize.getId(), entityName));
			}
			carEngineSizeRepository.save(carEngineSize).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(CarEngineSize carEngineSize) {
		try {
			carEngineSizeRepository.delete(carEngineSize);
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
			carEngineSizeRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<CarEngineSize> getById(int id) {
		CarEngineSize carEngineSize = new CarEngineSize();
		try {
			carEngineSize = carEngineSizeRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<CarEngineSize>(carEngineSize, "İstek zaman aşımına uğradı!");
		}
		if (carEngineSize == null) {
			return new ErrorDataResult<CarEngineSize>(new CarEngineSize(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<CarEngineSize>(carEngineSize, "Başarılı");
	}

	@Override
	public DataResult<List<CarEngineSize>> getAll() {
		List<CarEngineSize> carEngineSizes = new ArrayList<CarEngineSize>();
		try {
			carEngineSizeRepository.findAll().doOnNext(carEngineSizes::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<CarEngineSize>>(carEngineSizes, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<CarEngineSize>>(carEngineSizes, "İstek zaman aşımına uğradı!");
		}
	}

}
