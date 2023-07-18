package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.ProfessionService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.Profession;
import com.sbm.application.repositories.concretes.ProfessionRepository;

@Service
public class ProfessionManager implements ProfessionService {

	private final String entityName = "Meslek";
	Logger logger = LoggerFactory.getLogger(ProfessionManager.class);
	@Autowired
	private ProfessionRepository professionRepository;

	@Override
	public Result save(Profession profession) {
		try {
			if (profession.getId() == 0) {
				professionRepository.save(profession).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			Profession foundProfession = professionRepository.findById(profession.getId()).block(Duration.ofSeconds(1));
			if (foundProfession == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(profession.getId(), entityName));
			}
			professionRepository.save(profession).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public Result delete(Profession profession) {
		try {
			professionRepository.delete(profession);
			return new SuccessResult("Başarılı");
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public DataResult<Profession> getById(int id) {
		Profession profession = new Profession();
		try {
			profession = professionRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<Profession>(profession, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
		if (profession == null) {
			return new ErrorDataResult<Profession>(new Profession(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<Profession>(profession, "Başarılı");
	}

	@Override
	public DataResult<List<Profession>> getAll() {
		List<Profession> professions = new ArrayList<Profession>();
		try {
			professionRepository.findAll().doOnNext(professions::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<Profession>>(professions, "Başarılı");
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<List<Profession>>(professions, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public Result deleteById(int id) {
		var result = getById(id);
		if (!result.isSuccess()) {
			return new ErrorResult("%s bulunamadı".formatted(entityName));
		}
		try {
			professionRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

}
