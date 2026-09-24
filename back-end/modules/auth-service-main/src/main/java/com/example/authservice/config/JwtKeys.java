package com.example.authservice.config;

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
                "JWT access and refresh must use different RSA key pairs");
    }

    private static KeyPair loadKeyPair(String purpose, Resource privateResource, Resource publicResource) {
        Assert.notNull(privateResource, "jwt." + purpose + "-private-key is required");
        Assert.notNull(publicResource, "jwt." + purpose + "-public-key is required");
        try (var privateInput = privateResource.getInputStream();
                var publicInput = publicResource.getInputStream()) {
            var privateKey = RsaKeyConverters.pkcs8().convert(privateInput);
            var publicKey = RsaKeyConverters.x509().convert(publicInput);
            Assert.notNull(privateKey, "RSA private key is required");
            Assert.notNull(publicKey, "RSA public key is required");
            Assert.isTrue(privateKey.getModulus().bitLength() >= 2048
                    && publicKey.getModulus().bitLength() >= 2048,
                    "RSA keys must be at least 2048 bits");

            byte[] probe = "JWT key pair validation".getBytes(StandardCharsets.US_ASCII);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(probe);
            byte[] signed = signature.sign();
            signature.initVerify(publicKey);
            signature.update(probe);
            Assert.isTrue(signature.verify(signed), "RSA private and public keys do not match");
            return new KeyPair(publicKey, privateKey);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid JWT " + purpose
                    + " key pair: configure matching RSA PEM keys (PKCS#8 private, X.509 public)", e);
        }
    }
}
