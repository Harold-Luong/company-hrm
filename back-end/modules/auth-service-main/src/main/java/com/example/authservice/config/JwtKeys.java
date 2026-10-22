package com.example.authservice.config;

import com.example.authservice.exception.ExceptionMessages;
import lombok.Getter;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;

@Getter
@Component
public class JwtKeys {
    private final KeyPair access;
    private final KeyPair refresh;

    public JwtKeys(JwtProperties properties) {
        access = loadKeyPair("access", properties.getAccessPrivateKey(), properties.getAccessPublicKey());
        refresh = loadKeyPair("refresh", properties.getRefreshPrivateKey(), properties.getRefreshPublicKey());
        Assert.isTrue(!access.getPublic().equals(refresh.getPublic()),
                ExceptionMessages.JWT_DISTINCT_KEY_PAIRS_REQUIRED);
    }

    private static KeyPair loadKeyPair(String purpose, Resource privateResource, Resource publicResource) {
        Assert.notNull(privateResource, ExceptionMessages.JWT_PRIVATE_KEY_REQUIRED.formatted(purpose));
        Assert.notNull(publicResource, ExceptionMessages.JWT_PUBLIC_KEY_REQUIRED.formatted(purpose));
        try (var privateInput = privateResource.getInputStream();
                var publicInput = publicResource.getInputStream()) {
            var privateKey = RsaKeyConverters.pkcs8().convert(privateInput);
            var publicKey = RsaKeyConverters.x509().convert(publicInput);
            Assert.notNull(privateKey, ExceptionMessages.RSA_PRIVATE_KEY_REQUIRED);
            Assert.notNull(publicKey, ExceptionMessages.RSA_PUBLIC_KEY_REQUIRED);
            Assert.isTrue(privateKey.getModulus().bitLength() >= 2048
                    && publicKey.getModulus().bitLength() >= 2048,
                    ExceptionMessages.RSA_KEY_TOO_SHORT);

            byte[] probe = "JWT key pair validation".getBytes(StandardCharsets.US_ASCII);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(probe);
            byte[] signed = signature.sign();
            signature.initVerify(publicKey);
            signature.update(probe);
            Assert.isTrue(signature.verify(signed), ExceptionMessages.RSA_KEY_PAIR_MISMATCH);
            return new KeyPair(publicKey, privateKey);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(ExceptionMessages.INVALID_JWT_KEY_PAIR.formatted(purpose), e);
        }
    }
}
