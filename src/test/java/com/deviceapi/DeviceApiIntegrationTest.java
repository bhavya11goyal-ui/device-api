package com.deviceapi;

import com.deviceapi.web.dto.DeviceRequest;
import com.deviceapi.web.dto.DeviceResponse;
import com.deviceapi.web.dto.UpdateDeviceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.URI;

import static com.deviceapi.domain.DeviceState.AVAILABLE;
import static com.deviceapi.domain.DeviceState.IN_USE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full acceptance flow over real HTTP, against a real Postgres
 * (AbstractIntegrationTest's Testcontainer) and the real Spring context -
 * no mocking or slicing anywhere. Complements the unit/slice tests by
 * proving the whole stack actually wires together correctly end to end,
 * the same flow verified manually against the containerized app while
 * building the Dockerfile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void useJdkHttpClientForPatchSupport() {
        // TestRestTemplate's default request factory is backed by
        // HttpURLConnection, which doesn't support PATCH at all. The JDK's
        // own java.net.http.HttpClient (11+) does - no extra dependency
        // needed, just a different built-in Spring wrapper.
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void fullDeviceLifecycle() {
        ResponseEntity<DeviceResponse> created = restTemplate.postForEntity(
                "/api/v1/devices",
                new DeviceRequest("Pixel 9", "Google", AVAILABLE),
                DeviceResponse.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        URI deviceUri = created.getHeaders().getLocation();
        assertThat(created.getBody()).isNotNull();

        ResponseEntity<DeviceResponse> fetched = restTemplate.getForEntity(deviceUri, DeviceResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().name()).isEqualTo("Pixel 9");

        // PUT is a full replacement, so it carries every field.
        restTemplate.put(deviceUri, new DeviceRequest("Pixel 9", "Google", IN_USE));
        ResponseEntity<DeviceResponse> afterStateChange = restTemplate.getForEntity(deviceUri, DeviceResponse.class);
        assertThat(afterStateChange.getBody().state()).isEqualTo(IN_USE);

        ResponseEntity<String> deleteWhileInUse = restTemplate.exchange(
                deviceUri, HttpMethod.DELETE, null, String.class);
        assertThat(deleteWhileInUse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> renameWhileInUse = restTemplate.exchange(
                deviceUri,
                HttpMethod.PATCH,
                new HttpEntity<>(new UpdateDeviceRequest("New Name", null, null)),
                String.class
        );
        assertThat(renameWhileInUse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        restTemplate.exchange(
                deviceUri,
                HttpMethod.PATCH,
                new HttpEntity<>(new UpdateDeviceRequest(null, null, AVAILABLE)),
                Void.class
        );

        restTemplate.delete(deviceUri);
        ResponseEntity<String> afterDelete = restTemplate.getForEntity(deviceUri, String.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void putRejectsAnIncompleteBody() {
        ResponseEntity<DeviceResponse> created = restTemplate.postForEntity(
                "/api/v1/devices",
                new DeviceRequest("Pixel 9", "Google", AVAILABLE),
                DeviceResponse.class
        );
        URI deviceUri = created.getHeaders().getLocation();

        ResponseEntity<String> emptyPut = restTemplate.exchange(
                deviceUri, HttpMethod.PUT, new HttpEntity<>("{}", jsonHeaders()), String.class);

        assertThat(emptyPut.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        restTemplate.delete(deviceUri);
    }

    /**
     * Exercised end to end rather than with a mocked exception, because
     * the failure originates deep in Spring Data's sort resolution - only
     * a real query proves it surfaces as a 400 and not a 500.
     */
    @Test
    void unknownSortPropertyReturns400() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/devices?sort=notAField", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unparseableUuidReturns400() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/devices/abc", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
