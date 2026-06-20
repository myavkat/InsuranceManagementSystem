package com.insurancemanagementsystem.skeleton.repository;

import com.insurancemanagementsystem.skeleton.entity.SampleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SampleRepository extends JpaRepository<SampleEntity, UUID> {
}
