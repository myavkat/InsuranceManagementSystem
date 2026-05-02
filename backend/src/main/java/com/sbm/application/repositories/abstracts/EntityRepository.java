package com.sbm.application.repositories.abstracts;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.sbm.application.entities.abstracts.Entity;

@Repository
public interface EntityRepository<T extends Entity> extends ReactiveCrudRepository<T, Integer> {

}
