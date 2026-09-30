package com.deviceapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the devices table. Intentionally has no setter for
 * {@code creationTime} - it is set once at construction, making the
 * "creation time is immutable" invariant structurally impossible to
 * violate rather than merely unenforced. All other invariants (name/brand
 * frozen while IN_USE, no delete while IN_USE) are enforced by
 * {@code DeviceService}, not here.
 *
 * <p>{@code version} guards against lost updates when two requests read
 * and write the same device concurrently - see docs/DECISIONS.md #12.
 *
 * <p>{@code version} is a boxed {@code Long}, not a primitive, on purpose:
 * Spring Data's default new-vs-existing check for a versioned entity
 * branches on whether the version property is primitive. For a primitive,
 * it treats {@code == 0} as "new" - which is wrong here, since a device
 * loaded from the database but never yet updated also has version 0, and
 * would be misclassified as new and INSERTed again instead of UPDATEd.
 * For a boxed type it checks {@code == null} instead: a freshly
 * {@link #create}d device has never had its version set, so it's null;
 * Hibernate always populates the real value (0, 1, ...) when loading an
 * existing row. The null-check alone is enough to tell them apart, with
 * no need for a custom {@code Persistable} implementation.
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

    @Version
    private Long version;

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

    public Long getVersion() {
        return version;
    }
}
