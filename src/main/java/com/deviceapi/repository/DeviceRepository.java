package com.deviceapi.repository;

import com.deviceapi.domain.Device;
import com.deviceapi.domain.DeviceState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Page<Device> findByBrand(String brand, Pageable pageable);

    Page<Device> findByState(DeviceState state, Pageable pageable);

    Page<Device> findByBrandAndState(String brand, DeviceState state, Pageable pageable);
}
