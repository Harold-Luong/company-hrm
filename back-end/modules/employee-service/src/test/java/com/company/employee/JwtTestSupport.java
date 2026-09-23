package com.company.employee;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Test-only keys generated in memory; no production key or Auth process is needed. */
abstract class JwtTestSupport {
    protected static final KeyPair ACCESS_KEYS = generateKeyPair();
    private static final Path PUBLIC_KEY = writePublicKey();

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.access-public-key", () -> PUBLIC_KEY.toUri().toString());
        registry.add("jwt.access-issuer", () -> "auth-service");
        registry.add("jwt.access-audience", () -> "hrm-api-access");
    }

    protected static KeyPair generateKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot generate test key", exception);
        }
    }

    private static Path writePublicKey() {
        try {
            Path path = Files.createTempFile("employee-test-access-public-", ".pem");
            String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'})
                    .encodeToString(ACCESS_KEYS.getPublic().getEncoded());
            Files.writeString(path, "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n");
            path.toFile().deleteOnExit();
            return path;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot write test public key", exception);
        }
    }
}
