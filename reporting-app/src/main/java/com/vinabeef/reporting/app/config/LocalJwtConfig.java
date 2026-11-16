package com.vinabeef.reporting.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * LOCAL DEV ONLY: replaces the issuer-based JWT decoder with an HS256 shared secret,
 * so tokens can be minted locally (see scripts/dev-token.sh). Never enable in production.
 */
@Configuration
@Profile("local")
public class LocalJwtConfig {

    @Bean
    JwtDecoder jwtDecoder(@Value("${local.jwt.secret}") String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
