package com.deviceapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the devices table. Intentionally has no setter for
 * {@code creationTime} - it is set once at construction, making the
 * "creation time is immutable" invariant structurally impossible to
 * violate rather than merely unenforced. All other invariants (name/brand
 * frozen while IN_USE, no delete while IN_USE) are enforced by
 * {@code DeviceService}, not here.
 */
@Entity
@Table(name = "devices")
public class Device {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceState state;

    @Column(name = "creation_time", nullable = false)
    private Instant creationTime;

    protected Device() {
        // required by JPA
    }

    private Device(UUID id, String name, String brand, DeviceState state, Instant creationTime) {
        this.id = id;
        this.name = name;
        this.brand = brand;
        this.state = state;
        this.creationTime = creationTime;
    }

    public static Device create(String name, String brand, DeviceState state) {
        return new Device(UUID.randomUUID(), name, brand, state, Instant.now());
    }

    public void rename(String name, String brand) {
        this.name = name;
        this.brand = brand;
    }

    public void changeState(DeviceState state) {
        this.state = state;
    }

    public boolean isInUse() {
        return state == DeviceState.IN_USE;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public DeviceState getState() {
        return state;
    }

    public Instant getCreationTime() {
        return creationTime;
    }
}
