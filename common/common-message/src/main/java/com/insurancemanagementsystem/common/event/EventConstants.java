package com.insurancemanagementsystem.common.event;

public final class EventConstants {

    private EventConstants() {}

    // Topic names
    public static final String ESTIMATION_SAGA = "estimation.saga";
    public static final String CUSTOMER_EVENTS = "customer.events";
    public static final String VEHICLE_EVENTS = "vehicle.events";
    public static final String REALESTATE_EVENTS = "realestate.events";
    public static final String INSURANCE_EVENTS = "insurance.events";
    public static final String REFERENCE_DATA_EVENTS = "reference-data.events";

    // SAGA event types
    public static final String ESTIMATION_REQUESTED = "EstimationRequested";
    public static final String CUSTOMER_VALIDATED = "CustomerValidated";
    public static final String CUSTOMER_INVALIDATED = "CustomerInvalidated";
    public static final String VEHICLE_VALIDATED = "VehicleValidated";
    public static final String VEHICLE_INVALIDATED = "VehicleInvalidated";
    public static final String PREMIUM_CALCULATED = "PremiumCalculated";
    public static final String CALCULATION_FAILED = "CalculationFailed";
    public static final String ESTIMATION_FAILED = "EstimationFailed";

    // Domain event types
    public static final String CUSTOMER_CREATED = "CustomerCreated";
    public static final String CUSTOMER_UPDATED = "CustomerUpdated";
    public static final String CUSTOMER_DELETED = "CustomerDeleted";
    public static final String VEHICLE_CREATED = "VehicleCreated";
    public static final String VEHICLE_UPDATED = "VehicleUpdated";
    public static final String VEHICLE_DELETED = "VehicleDeleted";
    public static final String REAL_ESTATE_CREATED = "RealEstateCreated";
    public static final String REAL_ESTATE_UPDATED = "RealEstateUpdated";
    public static final String REAL_ESTATE_DELETED = "RealEstateDeleted";
    public static final String INSURANCE_CREATED = "InsuranceCreated";
    public static final String INSURANCE_UPDATED = "InsuranceUpdated";
    public static final String REFERENCE_DATA_CHANGED = "ReferenceDataChanged";
}
