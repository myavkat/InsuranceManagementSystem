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

	@Autowired
	private CityRepository cityRepository;

	@Override
	public Result save(City city) {
		try {
			if(city.getId()==0) {
				cityRepository.save(city).block(Duration.ofSeconds(1));
				return new SuccessResult("Şehir eklendi");
			}
			City foundCity = cityRepository.findById(city.getId()).block(Duration.ofSeconds(1));
			if(foundCity == null) {
				return new ErrorResult("%s idli Şehir bulunamadı".formatted(city.getId()));
			}
			cityRepository.save(city).block(Duration.ofSeconds(1));
			return new SuccessResult("Şehir güncelleme başarılı!");
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(City city) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DataResult<City> getById(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DataResult<List<City>> getAll() {
		List<City> cities = new ArrayList<City>();
		try {
		  cityRepository.findAll().doOnNext(cities::add).blockLast(Duration.ofSeconds(10));
		  return new SuccessDataResult<List<City>>(cities,"Başarılı");
		}
		catch(RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<City>>(cities,"İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result deleteById(int id) {
		// TODO Auto-generated method stub
		return null;
	}

}
