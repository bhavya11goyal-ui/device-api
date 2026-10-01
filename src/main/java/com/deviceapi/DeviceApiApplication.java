package com.deviceapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * {@code pageSerializationMode = VIA_DTO} makes the paginated collection
 * endpoint's JSON shape ({@link PagedModel}) a stable, documented contract
 * instead of serializing {@code PageImpl} directly, which Spring Data
 * itself warns is not guaranteed to stay the same across versions.
 */
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class DeviceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceApiApplication.class, args);
    }
}
