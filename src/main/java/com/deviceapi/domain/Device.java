package com.deviceapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.deviceapi.exception.DeviceInUseException;
import com.deviceapi.exception.DeviceNameBrandLockedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * JPA entity for the devices table, and the owner of the device business
 * rules: {@link #update} refuses a name or brand change while the device
 * is in use, and {@link #assertDeletable} refuses deletion of one.
 * Keeping them here rather than in the service means no caller can bypass
 * them by mutating a device another way. {@code creationTime} has no
 * setter at all, so its immutability is structural rather than checked.
 *
 * <p>{@code version} guards against lost updates when two requests read
 * and write the same device concurrently - see docs/DECISIONS.md #11.
 *
 * <p>It is a boxed {@code Long} rather than a primitive on purpose. The id
 * is assigned by the application, so Spring Data cannot use "is the id
 * null" to decide whether {@code save()} should insert or merge; with a
 * boxed version it uses "is the version null" instead, and a newly created
 * device goes straight to {@code persist}. With a primitive version that
 * check falls back to the id, which is always populated here, so every new
 * device would look pre-existing and be merged - costing a {@code SELECT}
 * before each {@code INSERT}.
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
        // Postgres stores timestamps at microsecond precision, so truncate
        // here rather than let the value silently change between the
        // response to a create and a subsequent read of the same row.
        return new Device(UUID.randomUUID(), name, brand, state, Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * Applies an update, enforcing the rule that name and brand are frozen
     * while the device is in use. A null argument means "leave unchanged",
     * so this serves a partial update directly; a caller doing a full
     * replacement simply passes every field.
     *
     * <p>The guard lives here rather than in the service so it cannot be
     * bypassed by a future caller that mutates the entity another way.
     *
     * @throws DeviceNameBrandLockedException if name or brand would
     *                                        actually change while in use
     */
    public void update(String name, String brand, DeviceState state) {
        boolean nameChanged = name != null && !name.equals(this.name);
        boolean brandChanged = brand != null && !brand.equals(this.brand);
        if ((nameChanged || brandChanged) && isInUse()) {
            throw new DeviceNameBrandLockedException(id);
        }

        if (name != null) {
            this.name = name;
        }
        if (brand != null) {
            this.brand = brand;
        }
        if (state != null) {
            this.state = state;
        }
    }

    /**
     * @throws DeviceInUseException if this device may not be deleted
     */
    public void assertDeletable() {
        if (isInUse()) {
            throw new DeviceInUseException(id);
        }
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
