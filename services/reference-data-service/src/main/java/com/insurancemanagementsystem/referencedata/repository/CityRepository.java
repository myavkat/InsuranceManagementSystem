package com.insurancemanagementsystem.referencedata.repository;

import com.insurancemanagementsystem.referencedata.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CityRepository extends JpaRepository<City, Integer> {

	List<City> findAllByOrderByNameAsc();

}
