package com.deviceapi.service;

import com.deviceapi.domain.Device;
import com.deviceapi.domain.DeviceState;
import com.deviceapi.exception.DeviceInUseException;
import com.deviceapi.exception.DeviceNameBrandLockedException;
import com.deviceapi.exception.DeviceNotFoundException;
import com.deviceapi.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Owns the three domain invariants (creation time is immutable - enforced
 * structurally by {@link Device} having no setter for it; name/brand frozen
 * while IN_USE; no delete while IN_USE). Controllers and any other caller
 * must go through this service rather than the repository directly.
 */
@Service
@Transactional
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public Device create(String name, String brand, DeviceState state) {
        Device device = deviceRepository.save(Device.create(name, brand, state));
        log.info("Created device {} ({} {})", device.getId(), brand, name);
        return device;
    }

    public Device update(UUID id, String name, String brand, DeviceState state) {
        Device device = getById(id);

        boolean nameChanged = name != null && !name.equals(device.getName());
        boolean brandChanged = brand != null && !brand.equals(device.getBrand());
        if ((nameChanged || brandChanged) && device.isInUse()) {
            log.warn("Rejected name/brand change on in-use device {}", id);
            throw new DeviceNameBrandLockedException(id);
        }

        device.rename(
                name != null ? name : device.getName(),
                brand != null ? brand : device.getBrand()
        );
        if (state != null) {
            device.changeState(state);
        }

        return deviceRepository.save(device);
    }

    @Transactional(readOnly = true)
    public Device getById(UUID id) {
        return deviceRepository.findById(id).orElseThrow(() -> new DeviceNotFoundException(id));
    }

    /**
     * Fetches all devices, optionally filtered by brand and/or state. Both
     * filters are optional and compose (brand AND state) rather than being
     * separate endpoints - see docs/DECISIONS.md #8.
     */
    @Transactional(readOnly = true)
    public List<Device> search(String brand, DeviceState state) {
        if (brand != null && state != null) {
            return deviceRepository.findByBrandAndState(brand, state);
        }
        if (brand != null) {
            return deviceRepository.findByBrand(brand);
        }
        if (state != null) {
            return deviceRepository.findByState(state);
        }
        return deviceRepository.findAll();
    }

    public void delete(UUID id) {
        Device device = getById(id);
        if (device.isInUse()) {
            log.warn("Rejected delete of in-use device {}", id);
            throw new DeviceInUseException(id);
        }
        deviceRepository.delete(device);
        log.info("Deleted device {}", id);
    }
}
