package com.insurancemanagementsystem.vehicle.service;

import com.insurancemanagementsystem.vehicle.client.CustomerServiceClient;
import com.insurancemanagementsystem.vehicle.config.VehicleEventPublisher;
import com.insurancemanagementsystem.vehicle.dto.VehicleRequest;
import com.insurancemanagementsystem.vehicle.dto.VehicleResponse;
import com.insurancemanagementsystem.vehicle.entity.*;
import com.insurancemanagementsystem.vehicle.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final CarBrandRepository carBrandRepository;
    private final CarModelRepository carModelRepository;
    private final CarEngineRepository carEngineRepository;
    private final CarFuelTypeRepository carFuelTypeRepository;
    private final CarTypeRepository carTypeRepository;
    private final CarPackageRepository carPackageRepository;
    private final VehicleEventPublisher vehicleEventPublisher;
    private final CustomerServiceClient customerServiceClient;

    // ---------- Vehicle CRUD ----------

    @Transactional(readOnly = true)
    public Page<VehicleResponse> findAll(Pageable pageable, String search, UUID customerId) {
        Page<Vehicle> vehiclePage;
        if (customerId != null && search != null && !search.isBlank()) {
            vehiclePage = vehicleRepository.searchByCustomerIdAndSearch(customerId, search.trim(), pageable);
        } else if (customerId != null) {
            vehiclePage = vehicleRepository.findByCustomerId(customerId, pageable);
        } else if (search != null && !search.isBlank()) {
            vehiclePage = vehicleRepository.search(search.trim(), pageable);
        } else {
            vehiclePage = vehicleRepository.findAll(pageable);
        }

        // Collect unique non-null customer IDs from the page
        java.util.Set<UUID> customerIds = vehiclePage.getContent().stream()
                .map(Vehicle::getCustomerId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());

        // Resolve each customer name once into a lookup map
        java.util.Map<UUID, String> customerNameMap = new java.util.HashMap<>();
        for (UUID customerIdEntry : customerIds) {
            String name = customerServiceClient.getCustomerName(customerIdEntry);
            if (name != null) {
                customerNameMap.put(customerIdEntry, name);
            }
        }

        // Map entities to DTOs using pre-resolved names
        return vehiclePage.map(vehicle -> {
            String customerName = customerNameMap.get(vehicle.getCustomerId());
            return toResponse(vehicle, customerName);
        });
    }

    @Transactional(readOnly = true)
    public VehicleResponse findById(UUID id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found with id: " + id));
        String customerName = customerServiceClient.getCustomerName(vehicle.getCustomerId());
        return toResponse(vehicle, customerName);
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        // Validate plate uniqueness
        vehicleRepository.findByPlate(request.getPlate().trim())
                .ifPresent(v -> {
                    throw new IllegalArgumentException("Vehicle with plate " + request.getPlate() + " already exists");
                });

        // Validate reference IDs exist
        validateReferenceIds(request);

        Vehicle vehicle = Vehicle.builder()
                .plate(request.getPlate().trim())
                .chassisNumber(request.getChassisNumber())
                .licenseFirstDate(request.getLicenseFirstDate())
                .carBrandId(request.getCarBrandId())
                .carModelId(request.getCarModelId())
                .carEngineId(request.getCarEngineId())
                .carFuelTypeId(request.getCarFuelTypeId())
                .carTypeId(request.getCarTypeId())
                .carPackageId(request.getCarPackageId())
                .customerId(request.getCustomerId())
                .build();

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle created with id: {} and plate: {}", saved.getId(), saved.getPlate());
        vehicleEventPublisher.publishVehicleCreated(saved);
        String customerName = customerServiceClient.getCustomerName(saved.getCustomerId());
        return toResponse(saved, customerName);
    }

    @Transactional
    public VehicleResponse update(UUID id, VehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found with id: " + id));

        // Validate plate uniqueness (skip if same plate)
        if (!vehicle.getPlate().equals(request.getPlate().trim())) {
            vehicleRepository.findByPlate(request.getPlate().trim())
                    .ifPresent(v -> {
                        throw new IllegalArgumentException("Vehicle with plate " + request.getPlate() + " already exists");
                    });
        }

        validateReferenceIds(request);

        vehicle.setPlate(request.getPlate().trim());
        vehicle.setChassisNumber(request.getChassisNumber());
        vehicle.setLicenseFirstDate(request.getLicenseFirstDate());
        vehicle.setCarBrandId(request.getCarBrandId());
        vehicle.setCarModelId(request.getCarModelId());
        vehicle.setCarEngineId(request.getCarEngineId());
        vehicle.setCarFuelTypeId(request.getCarFuelTypeId());
        vehicle.setCarTypeId(request.getCarTypeId());
        vehicle.setCarPackageId(request.getCarPackageId());
        vehicle.setCustomerId(request.getCustomerId());

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Vehicle updated with id: {}", saved.getId());
        vehicleEventPublisher.publishVehicleUpdated(saved);
        String customerName = customerServiceClient.getCustomerName(saved.getCustomerId());
        return toResponse(saved, customerName);
    }

    @Transactional
    public void delete(UUID id) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found with id: " + id));
        vehicleEventPublisher.publishVehicleDeleted(vehicle);
        vehicleRepository.delete(vehicle);
        log.info("Vehicle deleted with id: {}", id);
    }

    // ---------- Reference data endpoints ----------

    @Transactional(readOnly = true)
    public List<CarBrand> getBrands() {
        return carBrandRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CarModel> getModelsByBrand(Integer brandId) {
        return carModelRepository.findByBrandId(brandId);
    }

    @Transactional(readOnly = true)
    public List<CarEngine> getEngines() {
        return carEngineRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CarFuelType> getFuelTypes() {
        return carFuelTypeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CarType> getTypes() {
        return carTypeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<CarPackage> getPackages() {
        return carPackageRepository.findAll();
    }

    // ---------- Helper methods ----------

    private VehicleResponse toResponse(Vehicle vehicle, String customerName) {
        CarBrand brand = carBrandRepository.findById(vehicle.getCarBrandId()).orElse(null);
        CarModel model = carModelRepository.findById(vehicle.getCarModelId()).orElse(null);
        CarEngine engine = carEngineRepository.findById(vehicle.getCarEngineId()).orElse(null);
        CarFuelType fuelType = carFuelTypeRepository.findById(vehicle.getCarFuelTypeId()).orElse(null);
        CarType type = carTypeRepository.findById(vehicle.getCarTypeId()).orElse(null);
        CarPackage pkg = carPackageRepository.findById(vehicle.getCarPackageId()).orElse(null);

        return VehicleResponse.fromEntity(vehicle,
                brand != null ? brand.getName() : null,
                model != null ? model.getName() : null,
                engine != null ? engine.getName() : null,
                engine != null ? engine.getVolume() : null,
                engine != null ? engine.getPower() : null,
                fuelType != null ? fuelType.getName() : null,
                type != null ? type.getName() : null,
                pkg != null ? pkg.getName() : null,
                customerName);
    }

    private void validateReferenceIds(VehicleRequest request) {
        if (!carBrandRepository.existsById(request.getCarBrandId())) {
            throw new IllegalArgumentException("Car brand not found with id: " + request.getCarBrandId());
        }
        if (!carModelRepository.existsById(request.getCarModelId())) {
            throw new IllegalArgumentException("Car model not found with id: " + request.getCarModelId());
        }
        if (!carEngineRepository.existsById(request.getCarEngineId())) {
            throw new IllegalArgumentException("Car engine not found with id: " + request.getCarEngineId());
        }
        if (!carFuelTypeRepository.existsById(request.getCarFuelTypeId())) {
            throw new IllegalArgumentException("Car fuel type not found with id: " + request.getCarFuelTypeId());
        }
        if (!carTypeRepository.existsById(request.getCarTypeId())) {
            throw new IllegalArgumentException("Car type not found with id: " + request.getCarTypeId());
        }
        if (!carPackageRepository.existsById(request.getCarPackageId())) {
            throw new IllegalArgumentException("Car package not found with id: " + request.getCarPackageId());
        }
    }
}
