package com.deviceapi.service;

import com.deviceapi.domain.Device;
import com.deviceapi.domain.DeviceState;
import com.deviceapi.exception.DeviceInUseException;
import com.deviceapi.exception.DeviceNameBrandLockedException;
import com.deviceapi.exception.DeviceNotFoundException;
import com.deviceapi.repository.DeviceRepository;
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

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public Device create(String name, String brand, DeviceState state) {
        return deviceRepository.save(Device.create(name, brand, state));
    }

    public Device update(UUID id, String name, String brand, DeviceState state) {
        Device device = getById(id);

        boolean nameChanged = name != null && !name.equals(device.getName());
        boolean brandChanged = brand != null && !brand.equals(device.getBrand());
        if ((nameChanged || brandChanged) && device.isInUse()) {
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

    @Transactional(readOnly = true)
    public List<Device> getAll() {
        return deviceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Device> getByBrand(String brand) {
        return deviceRepository.findByBrand(brand);
    }

    @Transactional(readOnly = true)
    public List<Device> getByState(DeviceState state) {
        return deviceRepository.findByState(state);
    }

    public void delete(UUID id) {
        Device device = getById(id);
        if (device.isInUse()) {
            throw new DeviceInUseException(id);
        }
        deviceRepository.delete(device);
    }
}
