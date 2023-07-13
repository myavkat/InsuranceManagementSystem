package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.CityService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.City;
import com.sbm.application.repositories.concretes.CityRepository;

@Service
public class CityManager implements CityService {

	private final String entityName = "Şehir";
	@Autowired
	private CityRepository cityRepository;

	@Override
	public Result save(City city) {
		try {
			if (city.getId() == 0) {
				cityRepository.save(city).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			City foundCity = cityRepository.findById(city.getId()).block(Duration.ofSeconds(1));
			if (foundCity == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(city.getId(), entityName));
			}
			cityRepository.save(city).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(City city) {
		try {
			cityRepository.delete(city);
			return new SuccessResult("Silindi");
		} catch (Exception ex) {
			return new ErrorResult(ex.getMessage());
		}
	}

	@Override
	public DataResult<City> getById(int id) {
		City city = new City();
		try {
			city = cityRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<City>(city, "İstek zaman aşımına uğradı!");
		}
		if (city == null) {
			return new ErrorDataResult<City>(new City(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<City>(city, "Başarılı");
	}

	@Override
	public DataResult<List<City>> getAll() {
		List<City> cities = new ArrayList<City>();
		try {
			cityRepository.findAll().doOnNext(cities::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<City>>(cities, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<City>>(cities, "İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result deleteById(int id) {
		var result = getById(id);
		if (!result.isSuccess()) {
			return new ErrorResult("%s bulunamadı".formatted(entityName));
		}
		try {
			cityRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<City> getByPlateCode(String plateCode) {
		City city = new City();
		try {
			city = cityRepository.findByPlateCode(plateCode).block(Duration.ofSeconds(1));
			return new SuccessDataResult<City>(city, "başarılı");
		} catch (RuntimeException ex) {
			return new ErrorDataResult<City>(city, "İstek zaman aşımına uğradı veya plaka şehir kodu geçersiz");
		}
	}

}
