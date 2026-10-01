package com.deviceapi.domain;

import com.deviceapi.exception.DeviceInUseException;
import com.deviceapi.exception.DeviceNameBrandLockedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The domain rules live on the entity, so they are tested here directly -
 * no mocks, no Spring - rather than only through the service that calls it.
 */
class DeviceTest {

    @Test
    void updateChangesEveryProvidedFieldWhenNotInUse() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.AVAILABLE);

        device.update("Pixel 9 Pro", "Alphabet", DeviceState.INACTIVE);

        assertThat(device.getName()).isEqualTo("Pixel 9 Pro");
        assertThat(device.getBrand()).isEqualTo("Alphabet");
        assertThat(device.getState()).isEqualTo(DeviceState.INACTIVE);
    }

    @Test
    void updateLeavesNullFieldsUnchanged() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.AVAILABLE);

        device.update(null, null, DeviceState.IN_USE);

        assertThat(device.getName()).isEqualTo("Pixel 9");
        assertThat(device.getBrand()).isEqualTo("Google");
        assertThat(device.getState()).isEqualTo(DeviceState.IN_USE);
    }

    @Test
    void updateRejectsANameChangeWhileInUse() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.IN_USE);

        assertThatThrownBy(() -> device.update("Pixel 9 Pro", null, null))
                .isInstanceOf(DeviceNameBrandLockedException.class);
        assertThat(device.getName()).isEqualTo("Pixel 9");
    }

    @Test
    void updateRejectsABrandChangeWhileInUse() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.IN_USE);

        assertThatThrownBy(() -> device.update(null, "Alphabet", null))
                .isInstanceOf(DeviceNameBrandLockedException.class);
        assertThat(device.getBrand()).isEqualTo("Google");
    }

    @Test
    void updateAllowsResubmittingTheSameNameAndBrandWhileInUse() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.IN_USE);

        assertThatCode(() -> device.update("Pixel 9", "Google", DeviceState.AVAILABLE))
                .doesNotThrowAnyException();
        assertThat(device.getState()).isEqualTo(DeviceState.AVAILABLE);
    }

    @Test
    void assertDeletableRejectsAnInUseDevice() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.IN_USE);

        assertThatThrownBy(device::assertDeletable).isInstanceOf(DeviceInUseException.class);
    }

    @Test
    void assertDeletableAllowsADeviceThatIsNotInUse() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.INACTIVE);

        assertThatCode(device::assertDeletable).doesNotThrowAnyException();
    }

    @Test
    void creationTimeIsTruncatedToThePrecisionPostgresStores() {
        Device device = Device.create("Pixel 9", "Google", DeviceState.AVAILABLE);

        assertThat(device.getCreationTime().getNano() % 1000).isZero();
    }
}
