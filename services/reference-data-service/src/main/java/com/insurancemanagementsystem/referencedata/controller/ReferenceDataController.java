package com.insurancemanagementsystem.referencedata.controller;

import com.insurancemanagementsystem.common.web.dto.ApiResponse;
import com.insurancemanagementsystem.referencedata.dto.CityResponse;
import com.insurancemanagementsystem.referencedata.dto.ProfessionResponse;
import com.insurancemanagementsystem.referencedata.service.ReferenceDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/reference-data")
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataController {

    private final ReferenceDataService service;

    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<CityResponse>>> getCities() {
        List<CityResponse> cities = service.getCities();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS))
                .body(ApiResponse.success(cities));
    }

    @GetMapping("/professions")
    public ResponseEntity<ApiResponse<List<ProfessionResponse>>> getProfessions() {
        List<ProfessionResponse> professions = service.getProfessions();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS))
                .body(ApiResponse.success(professions));
    }
}
