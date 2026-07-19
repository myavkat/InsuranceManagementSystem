package com.insurancemanagementsystem.referencedata.repository;

import com.insurancemanagementsystem.referencedata.entity.Profession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfessionRepository extends JpaRepository<Profession, Integer> {

	List<Profession> findAllByOrderByNameAsc();

}
