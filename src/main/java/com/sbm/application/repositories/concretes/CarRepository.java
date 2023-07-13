package com.sbm.application.repositories.concretes;

import org.springframework.data.r2dbc.repository.Query;

import com.sbm.application.entities.concretes.Car;
import com.sbm.application.entities.dtos.CarDetailDTO;
import com.sbm.application.repositories.abstracts.EntityRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CarRepository extends EntityRepository<Car> {

	@Query("""
			SELECT car.[Id] as id
			      ,car.[EstimatedValue] as estimated_value
			      ,car.[Year] as "year"
				  ,fuelType.Name as fuel_type_name
				  ,engineSize.Size as engine_size
				  ,carType.Name as car_type_name
				  ,carBrand.Name + ' ' + carModel.Name + ' ' +  carPackage.Name as car_name
			      ,(car.[ValueFactor] + engine.ValueFactor + carPackage.ValueFactor + fuelType.ValueFactor + engineSize.ValueFactor + carModel.ValueFactor + carBrand.ValueFactor + carType.ValueFactor) as value_factor
			      ,(car.[ScaleFactor] + engine.ScaleFactor + carPackage.ScaleFactor + fuelType.ScaleFactor + engineSize.ScaleFactor + carModel.ScaleFactor + carBrand.ScaleFactor + carType.ScaleFactor)/8 as scale_factor
			      ,car.[CarEngineId] as car_engine_id
			      ,car.[CarPackageId] as car_package_id
				  ,engine.CarEngineSizeId as car_engine_size_id
				  ,engine.CarFuelTypeId as car_fuel_type_id
				  ,carPackage.CarModelId as car_model_id
				  ,carModel.BrandId as car_brand_id
				  ,carModel.CarTypeId as car_type_id
			  FROM [InsuranceDB].[dbo].[Cars] car
				  JOIN [InsuranceDB].[dbo].[CarEngines] engine on car.CarEngineId = engine.Id
				  JOIN [InsuranceDB].[dbo].[CarPackages] carPackage on car.CarPackageId = carPackage.Id
				  JOIN [InsuranceDB].[dbo].[CarFuelTypes] fuelType on engine.CarFuelTypeId = fuelType.Id
				  JOIN [InsuranceDB].[dbo].[CarEngineSizes] engineSize on engine.CarEngineSizeId = engineSize.Id
				  JOIN [InsuranceDB].[dbo].[CarModels] carModel on carPackage.CarModelId = carModel.Id
				  JOIN [InsuranceDB].[dbo].[CarBrands] carBrand on carModel.BrandId = carBrand.Id
				  JOIN [InsuranceDB].[dbo].[CarTypes] carType on carModel.CarTypeId = carType.Id
			""")
	Flux<CarDetailDTO> findCarDetails();

	@Query("""
			SELECT car.[Id] as id
			      ,car.[EstimatedValue] as estimated_value
			      ,car.[Year] as "year"
				  ,fuelType.Name as fuel_type_name
				  ,engineSize.Size as engine_size
				  ,carType.Name as car_type_name
				  ,carBrand.Name + ' ' + carModel.Name + ' ' +  carPackage.Name as car_name
			      ,(car.[ValueFactor] + engine.ValueFactor + carPackage.ValueFactor + fuelType.ValueFactor + engineSize.ValueFactor + carModel.ValueFactor + carBrand.ValueFactor + carType.ValueFactor) as value_factor
			      ,(car.[ScaleFactor] + engine.ScaleFactor + carPackage.ScaleFactor + fuelType.ScaleFactor + engineSize.ScaleFactor + carModel.ScaleFactor + carBrand.ScaleFactor + carType.ScaleFactor)/8 as scale_factor
			      ,car.[CarEngineId] as car_engine_id
			      ,car.[CarPackageId] as car_package_id
				  ,engine.CarEngineSizeId as car_engine_size_id
				  ,engine.CarFuelTypeId as car_fuel_type_id
				  ,carPackage.CarModelId as car_model_id
				  ,carModel.BrandId as car_brand_id
				  ,carModel.CarTypeId as car_type_id
			  FROM [InsuranceDB].[dbo].[Cars] car
				  JOIN [InsuranceDB].[dbo].[CarEngines] engine on car.CarEngineId = engine.Id
				  JOIN [InsuranceDB].[dbo].[CarPackages] carPackage on car.CarPackageId = carPackage.Id
				  JOIN [InsuranceDB].[dbo].[CarFuelTypes] fuelType on engine.CarFuelTypeId = fuelType.Id
				  JOIN [InsuranceDB].[dbo].[CarEngineSizes] engineSize on engine.CarEngineSizeId = engineSize.Id
				  JOIN [InsuranceDB].[dbo].[CarModels] carModel on carPackage.CarModelId = carModel.Id
				  JOIN [InsuranceDB].[dbo].[CarBrands] carBrand on carModel.BrandId = carBrand.Id
				  JOIN [InsuranceDB].[dbo].[CarTypes] carType on carModel.CarTypeId = carType.Id
				  WHERE car.[Id] = :id
			""")
	Mono<CarDetailDTO> findCarDetailById(int id);
}
