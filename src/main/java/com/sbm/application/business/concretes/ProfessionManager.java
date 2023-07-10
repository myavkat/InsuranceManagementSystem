package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.ProfessionService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.entities.concretes.Profession;
import com.sbm.application.repositories.concretes.ProfessionRepository;

@Service
public class ProfessionManager implements ProfessionService {

	@Autowired
	private ProfessionRepository professionRepository;
	@Override
	public Result save(Profession entity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Result delete(Profession entity) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DataResult<Profession> getById(int id) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public DataResult<List<Profession>> getAll() {
		List<Profession> professions = new ArrayList<Profession>();
		try {
			professionRepository.findAll().doOnNext(professions::add).blockLast(Duration.ofSeconds(10));
		  return new SuccessDataResult<List<Profession>>(professions,"Başarılı");
		}
		catch(RuntimeException ex) {
			System.out.println(ex.getMessage());
			return new ErrorDataResult<List<Profession>>(professions,"İstek zaman aşımına uğradı!");
		}
	}

	@Override
	public Result deleteById(int id) {
		// TODO Auto-generated method stub
		return null;
	}

}
