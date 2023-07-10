package com.sbm.application.business.abstracts;

import java.util.List;

import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.entities.abstracts.Entity;

public interface EntityService<T extends Entity> {

	public Result save(T entity);
	public Result delete(T entity);
	public Result deleteById(int id);
	public DataResult<T> getById(int id);
	public DataResult<List<T>> getAll();
}
