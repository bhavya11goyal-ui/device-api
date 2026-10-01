package com.deviceapi.service;

import com.deviceapi.domain.Device;
import com.deviceapi.domain.DeviceState;
import com.deviceapi.exception.DeviceNotFoundException;
import com.deviceapi.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates persistence around {@link Device}, which owns the domain
 * rules itself: creation time is immutable because there is no setter for
 * it, and name/brand-while-in-use and delete-while-in-use are enforced by
 * {@code Device.update} and {@code Device.assertDeletable}. Keeping the
 * rules in the entity means no future caller can bypass them by mutating
 * a device some other way - see docs/DECISIONS.md #3.
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
        device.update(name, brand, state);
        return deviceRepository.save(device);
    }

    @Transactional(readOnly = true)
    public Device getById(UUID id) {
        return deviceRepository.findById(id).orElseThrow(() -> new DeviceNotFoundException(id));
    }

    /**
     * Fetches devices, optionally filtered by brand and/or state and
     * always paginated. Both filters are optional and compose (brand AND
     * state) rather than being separate endpoints - see
     * docs/DECISIONS.md #8.
     */
    @Transactional(readOnly = true)
    public Page<Device> search(String brand, DeviceState state, Pageable pageable) {
        if (brand != null && state != null) {
            return deviceRepository.findByBrandAndState(brand, state, pageable);
        }
        if (brand != null) {
            return deviceRepository.findByBrand(brand, pageable);
        }
        if (state != null) {
            return deviceRepository.findByState(state, pageable);
        }
        return deviceRepository.findAll(pageable);
    }

    public void delete(UUID id) {
        Device device = getById(id);
        device.assertDeletable();
        deviceRepository.delete(device);
        log.info("Deleted device {}", id);
    }
}
