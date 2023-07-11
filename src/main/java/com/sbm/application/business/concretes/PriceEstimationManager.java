package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.PriceEstimationService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.PriceEstimation;
import com.sbm.application.repositories.concretes.PriceEstimationRepository;

@Service
public class PriceEstimationManager implements PriceEstimationService {
	private final String entityName = "Fiyat Tahmini";
	@Autowired
	private PriceEstimationRepository priceEstimationRepository;

	@Override
	public Result save(PriceEstimation priceEstimation) {
		try {
			if (priceEstimation.getId() == 0) {
				priceEstimationRepository.save(priceEstimation).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			PriceEstimation foundCarBrand = priceEstimationRepository.findById(priceEstimation.getId())
					.block(Duration.ofSeconds(1));
			if (foundCarBrand == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(priceEstimation.getId(), entityName));
			}
			priceEstimationRepository.save(priceEstimation).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(PriceEstimation priceEstimation) {
		try {
			priceEstimationRepository.delete(priceEstimation);
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
			priceEstimationRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<PriceEstimation> getById(int id) {
		PriceEstimation priceEstimation = new PriceEstimation();
		try {
			priceEstimation = priceEstimationRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<PriceEstimation>(priceEstimation, "İstek zaman aşımına uğradı!");
		}
		if (priceEstimation == null) {
			return new ErrorDataResult<PriceEstimation>(new PriceEstimation(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<PriceEstimation>(priceEstimation, "Başarılı");
	}

	@Override
	public DataResult<List<PriceEstimation>> getAll() {
		List<PriceEstimation> priceEstimations = new ArrayList<PriceEstimation>();
		try {
			priceEstimationRepository.findAll().doOnNext(priceEstimations::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<PriceEstimation>>(priceEstimations, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<PriceEstimation>>(priceEstimations, "İstek zaman aşımına uğradı!");
		}
	}
}
