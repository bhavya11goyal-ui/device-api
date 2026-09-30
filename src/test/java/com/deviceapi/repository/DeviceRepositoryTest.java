package com.deviceapi.repository;

import com.deviceapi.AbstractIntegrationTest;
import com.deviceapi.domain.Device;
import com.deviceapi.domain.DeviceState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeviceRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private DeviceRepository deviceRepository;

    @Test
    void persistsAndReloadsADeviceWithAllFields() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.AVAILABLE);

        deviceRepository.saveAndFlush(device);
        deviceRepository.flush();

        Optional<Device> found = deviceRepository.findById(device.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(device.getId());
        assertThat(found.get().getName()).isEqualTo("Pixel 9");
        assertThat(found.get().getBrand()).isEqualTo("Google");
        assertThat(found.get().getState()).isEqualTo(DeviceState.AVAILABLE);
        assertThat(found.get().getCreationTime()).isNotNull();
        assertThat(found.get().getCreationTime()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void findsDevicesByBrand() {
        deviceRepository.saveAndFlush(Device.create("iPhone 16", "Apple", DeviceState.AVAILABLE));
        deviceRepository.saveAndFlush(Device.create("iPhone 16 Pro", "Apple", DeviceState.IN_USE));
        deviceRepository.saveAndFlush(Device.create("Galaxy S25", "Samsung", DeviceState.AVAILABLE));

        List<Device> appleDevices = deviceRepository.findByBrand("Apple");

        assertThat(appleDevices).hasSize(2)
                .extracting(Device::getBrand)
                .containsOnly("Apple");
    }

    @Test
    void findsDevicesByState() {
        deviceRepository.saveAndFlush(Device.create("Pixel 9", "Google", DeviceState.IN_USE));
        deviceRepository.saveAndFlush(Device.create("iPhone 16", "Apple", DeviceState.AVAILABLE));
        deviceRepository.saveAndFlush(Device.create("Galaxy S25", "Samsung", DeviceState.IN_USE));

        List<Device> inUseDevices = deviceRepository.findByState(DeviceState.IN_USE);

        assertThat(inUseDevices).hasSize(2)
                .extracting(Device::getState)
                .containsOnly(DeviceState.IN_USE);
    }

    @Test
    void findsDevicesByBrandAndState() {
        deviceRepository.saveAndFlush(Device.create("iPhone 16", "Apple", DeviceState.AVAILABLE));
        deviceRepository.saveAndFlush(Device.create("iPhone 16 Pro", "Apple", DeviceState.IN_USE));
        deviceRepository.saveAndFlush(Device.create("Galaxy S25", "Samsung", DeviceState.IN_USE));

        List<Device> appleInUse = deviceRepository.findByBrandAndState("Apple", DeviceState.IN_USE);

        assertThat(appleInUse).hasSize(1);
        assertThat(appleInUse.get(0).getName()).isEqualTo("iPhone 16 Pro");
    }

    @Test
    void deletingARemovedDeviceLeavesNoTrace() {
        Device device = deviceRepository.saveAndFlush(Device.create("Old Phone", "Nokia", DeviceState.INACTIVE));

        deviceRepository.delete(device);
        deviceRepository.flush();

        assertThat(deviceRepository.findById(device.getId())).isEmpty();
    }
}
