package com.sbm.application.business.concretes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sbm.application.business.abstracts.RealEstateService;
import com.sbm.application.core.utilities.results.DataResult;
import com.sbm.application.core.utilities.results.ErrorDataResult;
import com.sbm.application.core.utilities.results.ErrorResult;
import com.sbm.application.core.utilities.results.Result;
import com.sbm.application.core.utilities.results.SuccessDataResult;
import com.sbm.application.core.utilities.results.SuccessResult;
import com.sbm.application.entities.concretes.RealEstate;
import com.sbm.application.entities.dtos.RealEstateDetailDTO;
import com.sbm.application.repositories.concretes.RealEstateRepository;

@Service
public class RealEstateManager implements RealEstateService {
	
	private final String entityName = "Emlak";
	Logger logger = LoggerFactory.getLogger(RealEstateManager.class);
	@Autowired
	private RealEstateRepository realEstateRepository;
	@Override
	public Result save(RealEstate entity) {
		try {
			/*
			 * UAVT CHECK
			 * */
			Set<RealEstate> realEstatesWithSameIdentifier = new HashSet<RealEstate>();
			realEstateRepository.findAllByUAVT(entity.getUavt()).doOnNext(realEstatesWithSameIdentifier::add).blockLast();
			realEstatesWithSameIdentifier.removeIf(re -> re.getId()== entity.getId());
			for(RealEstate realEstateWithSameIdentifier: realEstatesWithSameIdentifier) {
				if(realEstateWithSameIdentifier.getUavt().contentEquals(entity.getUavt())) {
					return new ErrorResult("UAVT kodu ile kayıtlı %s var".formatted(entityName));
				}
			}
			/*
			 * END UAVT CHECK
			 * */
			if (entity.getId() == 0) {
				realEstateRepository.save(entity).block(Duration.ofSeconds(1));
				return new SuccessResult("%s eklendi".formatted(entityName));
			}
			RealEstate foundRealEstate = realEstateRepository.findById(entity.getId()).block(Duration.ofSeconds(1));
			if (foundRealEstate == null) {
				return new ErrorResult("%s idli %s bulunamadı".formatted(entity.getId(), entityName));
			}
			realEstateRepository.save(entity).block(Duration.ofSeconds(1));
			return new SuccessResult("%s güncelleme başarılı!".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public Result delete(RealEstate entity) {
		try {
			realEstateRepository.delete(entity);
			return new SuccessResult("Başarılı");
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public Result deleteById(int id) {
		var result = getById(id);
		if (!result.isSuccess()) {
			return new ErrorResult("%s bulunamadı".formatted(entityName));
		}
		try {
			realEstateRepository.delete(result.getData()).block(Duration.ofSeconds(1));
			return new SuccessResult("%s silindi".formatted(entityName));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorResult("Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public DataResult<RealEstate> getById(int id) {
		RealEstate realEstate = new RealEstate();
		try {
			realEstate = realEstateRepository.findById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<RealEstate>(realEstate, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
		if (realEstate == null) {
			return new ErrorDataResult<RealEstate>(new RealEstate(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<RealEstate>(realEstate, "Başarılı");
	}

	@Override
	public DataResult<List<RealEstate>> getAll() {
		List<RealEstate> realEstates = new ArrayList<RealEstate>();
		try {
			realEstateRepository.findAll().doOnNext(realEstates::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<RealEstate>>(realEstates, "Başarılı");
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<List<RealEstate>>(realEstates, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public DataResult<List<RealEstateDetailDTO>> getAllDetails() {
		List<RealEstateDetailDTO> details = new ArrayList<RealEstateDetailDTO>();
		try {
			realEstateRepository.findAllDetails().doOnNext(details::add).blockLast(Duration.ofSeconds(10));
			return new SuccessDataResult<List<RealEstateDetailDTO>>(details, "Başarılı");
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<List<RealEstateDetailDTO>>(details, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
	}

	@Override
	public DataResult<RealEstateDetailDTO> getDetailById(int id) {
		RealEstateDetailDTO detail = new RealEstateDetailDTO();
		try {
			detail = realEstateRepository.findDetailById(id).block(Duration.ofSeconds(1));
		} catch (RuntimeException ex) {
			logger.error(ExceptionUtils.getStackTrace(ex));
			return new ErrorDataResult<RealEstateDetailDTO>(detail, "Beklenmeyen bir hatayla karşılaşıldı!");
		}
		if (detail == null) {
			return new ErrorDataResult<RealEstateDetailDTO>(new RealEstateDetailDTO(), "%s Bulunamadı!".formatted(entityName));
		}
		return new SuccessDataResult<RealEstateDetailDTO>(detail, "Başarılı");
	}

}
