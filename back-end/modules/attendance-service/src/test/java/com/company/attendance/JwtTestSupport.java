package com.company.attendance;

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
    protected static final Path TEST_SCHEMA = writeTestSchema();

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.sql.init.schema-locations", () -> TEST_SCHEMA.toUri().toString());
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

    /** Dùng schema chính; H2 bỏ riêng trigger/index PostgreSQL, không duy trì schema SQL thứ hai. */
    private static Path writeTestSchema() {
        try {
            String sql = Files.readString(Path.of("docs/sql/001_attendance_schema.sql"));
            sql = sql.replaceAll("(?s)-- BEGIN POSTGRESQL ONLY.*?-- END POSTGRESQL ONLY", "");
            Path path = Files.createTempFile("attendance-test-schema-", ".sql");
            Files.writeString(path, sql);
            path.toFile().deleteOnExit();
            return path;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot prepare attendance test schema", exception);
        }
    }

    private static Path writePublicKey() {
        try {
            Path path = Files.createTempFile("attendance-test-access-public-", ".pem");
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
