package com.deviceapi.service;

import com.deviceapi.domain.Device;
import com.deviceapi.domain.DeviceState;
import com.deviceapi.exception.DeviceInUseException;
import com.deviceapi.exception.DeviceNameBrandLockedException;
import com.deviceapi.exception.DeviceNotFoundException;
import com.deviceapi.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void createPersistsAndReturnsANewDevice() {
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Device created = deviceService.create("Pixel 9", "Google", DeviceState.AVAILABLE);

        assertThat(created.getName()).isEqualTo("Pixel 9");
        assertThat(created.getBrand()).isEqualTo("Google");
        assertThat(created.getState()).isEqualTo(DeviceState.AVAILABLE);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreationTime()).isNotNull();
        verify(deviceRepository).save(created);
    }

    @Test
    void updateChangesNameBrandAndStateWhenNotInUse() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.AVAILABLE);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Device updated = deviceService.update(device.getId(), "Pixel 9 Pro", "Google", DeviceState.IN_USE);

        assertThat(updated.getName()).isEqualTo("Pixel 9 Pro");
        assertThat(updated.getBrand()).isEqualTo("Google");
        assertThat(updated.getState()).isEqualTo(DeviceState.IN_USE);
    }

    @Test
    void updateThrowsWhenChangingNameWhileInUse() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.IN_USE);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> deviceService.update(device.getId(), "Pixel 9 Pro", null, null))
                .isInstanceOf(DeviceNameBrandLockedException.class);

        verify(deviceRepository, never()).save(any());
    }

    @Test
    void updateThrowsWhenChangingBrandWhileInUse() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.IN_USE);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> deviceService.update(device.getId(), null, "Alphabet", null))
                .isInstanceOf(DeviceNameBrandLockedException.class);

        verify(deviceRepository, never()).save(any());
    }

    @Test
    void updateDoesNotThrowWhenResubmittingTheSameNameAndBrandWhileInUse() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.IN_USE);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Device updated = deviceService.update(device.getId(), "Pixel 9", "Google", DeviceState.IN_USE);

        assertThat(updated.getName()).isEqualTo("Pixel 9");
        assertThat(updated.getBrand()).isEqualTo("Google");
    }

    @Test
    void updateAllowsChangingStateAloneWhileInUse() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.IN_USE);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Device updated = deviceService.update(device.getId(), null, null, DeviceState.AVAILABLE);

        assertThat(updated.getState()).isEqualTo(DeviceState.AVAILABLE);
        assertThat(updated.getName()).isEqualTo("Pixel 9");
        assertThat(updated.getBrand()).isEqualTo("Google");
    }

    @Test
    void deleteRemovesADeviceThatIsNotInUse() {
        Device device = Device.create("Old Phone", "Nokia", DeviceState.INACTIVE);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        deviceService.delete(device.getId());

        verify(deviceRepository).delete(device);
    }

    @Test
    void deleteThrowsWhenDeviceIsInUse() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.IN_USE);
        when(deviceRepository.findById(device.getId())).thenReturn(Optional.of(device));

        assertThatThrownBy(() -> deviceService.delete(device.getId()))
                .isInstanceOf(DeviceInUseException.class);

        verify(deviceRepository, never()).delete(any());
    }

    @Test
    void getByIdThrowsWhenDeviceIsMissing() {
        UUID id = UUID.randomUUID();
        when(deviceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.getById(id))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void updateThrowsWhenDeviceIsMissing() {
        UUID id = UUID.randomUUID();
        when(deviceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.update(id, "Name", null, null))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void deleteThrowsWhenDeviceIsMissing() {
        UUID id = UUID.randomUUID();
        when(deviceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.delete(id))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void getAllDelegatesToRepository() {
        List<Device> devices = List.of(Device.create("Pixel 9", "Google", DeviceState.AVAILABLE));
        when(deviceRepository.findAll()).thenReturn(devices);

        assertThat(deviceService.getAll()).isEqualTo(devices);
    }

    @Test
    void getByBrandDelegatesToRepository() {
        List<Device> devices = List.of(Device.create("Pixel 9", "Google", DeviceState.AVAILABLE));
        when(deviceRepository.findByBrand("Google")).thenReturn(devices);

        assertThat(deviceService.getByBrand("Google")).isEqualTo(devices);
    }

    @Test
    void getByStateDelegatesToRepository() {
        List<Device> devices = List.of(Device.create("Pixel 9", "Google", DeviceState.IN_USE));
        when(deviceRepository.findByState(DeviceState.IN_USE)).thenReturn(devices);

        assertThat(deviceService.getByState(DeviceState.IN_USE)).isEqualTo(devices);
    }
}
