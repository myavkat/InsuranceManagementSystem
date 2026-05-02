package com.sbm.application.repositories.concretes;

import org.springframework.stereotype.Repository;

import com.sbm.application.entities.concretes.CarType;
import com.sbm.application.repositories.abstracts.EntityRepository;

@Repository
public interface CarTypeRepository extends EntityRepository<CarType> {

}
