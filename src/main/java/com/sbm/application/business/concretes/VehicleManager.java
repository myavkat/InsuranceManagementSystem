package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.VehicleService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.Vehicle;
import com.sbm.application.repositories.concretes.VehicleRepository;

@Service
public class VehicleManager implements VehicleService {

	private final String entityName = "Araç";
	@Autowired
	private VehicleRepository vehicleRepository;

	@Override
	public Result save(Vehicle vehicle) {
		try {
			if (vehicle.getId() == 0) {
				vehicleRepository.save(vehicle).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			Vehicle foundCar = vehicleRepository.findById(vehicle.getId()).block(Duration.ofSeconds(1));
			if (foundCar == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(vehicle.getId(), entityName));
			}
			vehicleRepository.save(vehicle).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result delete(Vehicle vehicle) {
		try {
			vehicleRepository.delete(vehicle);
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
			vehicleRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			return new ErrorResult("İstek zaman aşımına uğradı");
		}
	}

	@Override
	public DataResult<Vehicle> getById(int id) {
		Vehicle vehicle = new Vehicle();
		try {
			vehicle = vehicleRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<Vehicle>(vehicle, "İstek zaman aşımına uğradı!");
		}
		if (vehicle == null) {
			return new ErrorDataResult<Vehicle>(new Vehicle(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<Vehicle>(vehicle, "Başarılı");
	}

	@Override
	public DataResult<List<Vehicle>> getAll() {
		List<Vehicle> vehicles = new ArrayList<Vehicle>();
		try {
			vehicleRepository.findAll().doOnNext(vehicles::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<Vehicle>>(vehicles, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<Vehicle>>(vehicles, "İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public DataResult<List<Vehicle>> getVehiclesByCustomerId(int customerId) {
		List<Vehicle> vehicles = new ArrayList<Vehicle>();
		try {
			vehicleRepository.findByCustomerId(customerId).doOnNext(vehicles::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<Vehicle>>(vehicles, "Başarılı");
		} catch (RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<Vehicle>>(vehicles, "İstek zaman aşımına uğradı!");
		}
	}

}
