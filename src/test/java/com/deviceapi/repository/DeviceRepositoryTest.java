package com.deviceapi.repository;

import com.deviceapi.AbstractIntegrationTest;
import com.deviceapi.domain.Device;
import com.deviceapi.domain.DeviceState;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DeviceRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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

        Page<Device> appleDevices = deviceRepository.findByBrand("Apple", Pageable.unpaged());

        assertThat(appleDevices.getContent()).hasSize(2)
                .extracting(Device::getBrand)
                .containsOnly("Apple");
    }

    @Test
    void findsDevicesByState() {
        deviceRepository.saveAndFlush(Device.create("Pixel 9", "Google", DeviceState.IN_USE));
        deviceRepository.saveAndFlush(Device.create("iPhone 16", "Apple", DeviceState.AVAILABLE));
        deviceRepository.saveAndFlush(Device.create("Galaxy S25", "Samsung", DeviceState.IN_USE));

        Page<Device> inUseDevices = deviceRepository.findByState(DeviceState.IN_USE, Pageable.unpaged());

        assertThat(inUseDevices.getContent()).hasSize(2)
                .extracting(Device::getState)
                .containsOnly(DeviceState.IN_USE);
    }

    @Test
    void findsDevicesByBrandAndState() {
        deviceRepository.saveAndFlush(Device.create("iPhone 16", "Apple", DeviceState.AVAILABLE));
        deviceRepository.saveAndFlush(Device.create("iPhone 16 Pro", "Apple", DeviceState.IN_USE));
        deviceRepository.saveAndFlush(Device.create("Galaxy S25", "Samsung", DeviceState.IN_USE));

        Page<Device> appleInUse = deviceRepository.findByBrandAndState("Apple", DeviceState.IN_USE, Pageable.unpaged());

        assertThat(appleInUse.getContent()).hasSize(1);
        assertThat(appleInUse.getContent().get(0).getName()).isEqualTo("iPhone 16 Pro");
    }

    @Test
    void concurrentUpdatesToTheSameDeviceRaiseAnOptimisticLockException() {
        Device device = deviceRepository.saveAndFlush(Device.create("Pixel 9", "Google", DeviceState.AVAILABLE));
        entityManager.clear();

        // Simulate two independent requests that both read the device
        // before either has written back. Each copy must be detached
        // immediately after loading - otherwise Hibernate's identity map
        // would hand back the SAME managed instance for both reads (or
        // coalesce them on save), defeating the point of the test.
        Device firstReaderCopy = deviceRepository.findById(device.getId()).orElseThrow();
        entityManager.detach(firstReaderCopy);
        Device secondReaderCopy = deviceRepository.findById(device.getId()).orElseThrow();
        entityManager.detach(secondReaderCopy);

        firstReaderCopy.changeState(DeviceState.IN_USE);
        deviceRepository.saveAndFlush(firstReaderCopy);

        secondReaderCopy.rename("Pixel 9 Pro", secondReaderCopy.getBrand());
        assertThatThrownBy(() -> deviceRepository.saveAndFlush(secondReaderCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    /**
     * Complements the deterministic test above with real thread
     * interleaving rather than a fixed read-then-write ordering.
     * {@code @DataJpaTest} normally wraps each test in one transaction on
     * one connection - that's disabled here (NOT_SUPPORTED) so the two
     * threads each get their own transaction and can genuinely race,
     * which is what actually exercises the database-level conflict
     * detection rather than just the version-comparison logic.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentThreadsRacingToUpdateTheSameDeviceOnlyOneWins() throws Exception {
        Device device = deviceRepository.saveAndFlush(Device.create("Pixel 9", "Google", DeviceState.AVAILABLE));
        UUID id = device.getId();

        CountDownLatch bothHaveRead = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Callable<Exception> setInUse = () -> {
                try {
                    Device read = deviceRepository.findById(id).orElseThrow();
                    bothHaveRead.countDown();
                    bothHaveRead.await(5, TimeUnit.SECONDS);
                    read.changeState(DeviceState.IN_USE);
                    deviceRepository.saveAndFlush(read);
                    return null;
                } catch (Exception ex) {
                    return ex;
                }
            };
            Callable<Exception> renameDevice = () -> {
                try {
                    Device read = deviceRepository.findById(id).orElseThrow();
                    bothHaveRead.countDown();
                    bothHaveRead.await(5, TimeUnit.SECONDS);
                    read.rename("Pixel 9 Pro", read.getBrand());
                    deviceRepository.saveAndFlush(read);
                    return null;
                } catch (Exception ex) {
                    return ex;
                }
            };

            Future<Exception> resultA = executor.submit(setInUse);
            Future<Exception> resultB = executor.submit(renameDevice);

            List<Exception> failures = new ArrayList<>();
            Exception outcomeA = resultA.get(10, TimeUnit.SECONDS);
            Exception outcomeB = resultB.get(10, TimeUnit.SECONDS);
            if (outcomeA != null) {
                failures.add(outcomeA);
            }
            if (outcomeB != null) {
                failures.add(outcomeB);
            }

            assertThat(failures).hasSize(1);
            assertThat(failures.get(0)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        } finally {
            executor.shutdownNow();
            deviceRepository.deleteById(id);
        }
    }

    @Test
    void versionIncrementsOnEachUpdate() {
        Device device = deviceRepository.saveAndFlush(Device.create("Pixel 9", "Google", DeviceState.AVAILABLE));
        assertThat(device.getVersion()).isEqualTo(0L);

        device.rename("Pixel 9 Pro", device.getBrand());
        Device updated = deviceRepository.saveAndFlush(device);
        assertThat(updated.getVersion()).isEqualTo(1L);

        updated.changeState(DeviceState.IN_USE);
        Device updatedAgain = deviceRepository.saveAndFlush(updated);
        assertThat(updatedAgain.getVersion()).isEqualTo(2L);
    }

    @Test
    void createIssuesNoSelectBeforeInsert() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        deviceRepository.saveAndFlush(Device.create("Pixel 9", "Google", DeviceState.AVAILABLE));

        // A misclassified isNew() (e.g. a primitive version treating 0 as
        // "new" for a row that already exists) would show up here as an
        // entity load/merge-find preceding the insert; a correctly
        // recognized new entity goes straight to INSERT with no lookup.
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(statistics.getEntityInsertCount()).isEqualTo(1);
    }

    @Test
    void deletingARemovedDeviceLeavesNoTrace() {
        Device device = deviceRepository.saveAndFlush(Device.create("Old Phone", "Nokia", DeviceState.INACTIVE));

        deviceRepository.delete(device);
        deviceRepository.flush();

        assertThat(deviceRepository.findById(device.getId())).isEmpty();
    }
}
