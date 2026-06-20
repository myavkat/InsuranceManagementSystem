package com.insurancemanagementsystem.skeleton.controller;

import com.insurancemanagementsystem.skeleton.dto.ApiResponse;
import com.insurancemanagementsystem.skeleton.entity.SampleEntity;
import com.insurancemanagementsystem.skeleton.service.SampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/samples")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SampleEntity>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(service.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SampleEntity>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SampleEntity>> create(@RequestBody Map<String, String> body) {
        SampleEntity created = service.create(body.get("name"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Sample created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SampleEntity>> update(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success("Sample updated successfully",
                service.update(id, body.get("name"))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Sample deleted successfully", null));
    }
}
