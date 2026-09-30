package com.deviceapi.repository;

import com.deviceapi.domain.Device;
import com.deviceapi.domain.DeviceState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findByBrand(String brand);

    List<Device> findByState(DeviceState state);
}
