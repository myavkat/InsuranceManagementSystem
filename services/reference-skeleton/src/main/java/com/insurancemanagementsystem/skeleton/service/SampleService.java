package com.insurancemanagementsystem.skeleton.service;

import com.insurancemanagementsystem.skeleton.entity.SampleEntity;
import com.insurancemanagementsystem.skeleton.repository.SampleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SampleService {

    private final SampleRepository repository;

    @Transactional(readOnly = true)
    public List<SampleEntity> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public SampleEntity findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sample not found with id: " + id));
    }

    @Transactional
    public SampleEntity create(String name) {
        SampleEntity entity = SampleEntity.builder()
                .name(name)
                .build();
        return repository.save(entity);
    }

    @Transactional
    public SampleEntity update(UUID id, String name) {
        SampleEntity entity = findById(id);
        entity.setName(name);
        return entity;  // Hibernate dirty checking persists the change automatically
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Sample not found with id: " + id);
        }
        repository.deleteById(id);
    }
}
