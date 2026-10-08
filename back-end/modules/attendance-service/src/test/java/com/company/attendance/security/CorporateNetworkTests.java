package com.company.attendance.security;

import com.company.attendance.config.AttendanceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorporateNetworkTests {
    private final CorporateNetwork network = new CorporateNetwork(new AttendanceProperties(
            "", "", "", defaults("allowed-networks"), defaults("trusted-proxies")));

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "113.161.73.224", "10.20.30.40"})
    void configuredGatewayAcceptsLocalAndCompanyClientAddresses(String client) {
        assertThat(network.requireAllowed(request("127.0.0.1", client))).isEqualTo(client);
    }

    @Test
    void ipv6GatewayAcceptsIpv6LocalClient() {
        assertThat(network.requireAllowed(request("::1", "::1"))).isEqualTo("::1");
    }

    @Test
    void trustingGatewayDoesNotAllowAnExternalClient() {
        denied(request("127.0.0.1", "203.0.113.8"));
    }

    @Test
    void untrustedClientCannotSpoofCompanyOrLoopbackAddress() {
        denied(request("203.0.113.8", "113.161.73.224"));
        denied(request("203.0.113.8", "127.0.0.1"));
    }

    @Test
    void trustedGatewayMustSupplyExactlyOneClientAddress() {
        denied(request("127.0.0.1", null));
        denied(request("127.0.0.1", "113.161.73.224, 203.0.113.8"));
    }

    private void denied(MockHttpServletRequest request) {
        assertThatThrownBy(() -> network.requireAllowed(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private MockHttpServletRequest request(String peer, String client) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (client != null) request.addHeader("X-Forwarded-For", client);
        return request;
    }

    private List<String> defaults(String key) {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yaml"));
        String placeholder = yaml.getObject().getProperty("attendance." + key);
        // Read the checked-in defaults, independently of the developer's environment.
        String value = placeholder.substring(placeholder.indexOf(':') + 1, placeholder.length() - 1);
        return Arrays.asList(value.split(","));
    }
}
