package com.bjarne.datingrecommendationsuserservice.config;

import com.bjarne.datingrecommendationsuserservice.entity.UserStatus;
import com.bjarne.datingrecommendationsuserservice.presence.PresenceFilter;
import com.bjarne.datingrecommendationsuserservice.presence.PresenceService;
import com.bjarne.datingrecommendationsuserservice.repository.UserRepository;
import com.bjarne.datingrecommendationsuserservice.security.AccountStatusFilter;
import com.bjarne.datingrecommendationsuserservice.security.AccountStatusService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private static final int MIN_JWT_SECRET_BYTES = 32;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey(@Value("${app.security.jwt.secret:}") String secret) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret is " + (secret.isBlank() ? "not set" : "too short")
                    + ": set the environment variable JWT_SECRET to at least " + MIN_JWT_SECRET_BYTES
                    + " bytes (HS256), or start with the dev profile");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, @Value("${app.security.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /**
     * Authorities from the token: SCOPE_* from "scope" (chosen by the client, so never used for permissions)
     * and ROLE_* from "roles" (set from the database at login, e.g. ROLE_ADMIN).
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName("scope");
        scopes.setAuthorityPrefix("SCOPE_");
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            authorities.addAll(roles.convert(jwt));
            return authorities;
        });
        return converter;
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> {
            com.bjarne.datingrecommendationsuserservice.entity.User user = userRepository.findByEmailIgnoreCase(username.trim())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            return User
                    .withUsername(user.getEmail())
                    .password(user.getPassword())
                    .authorities("ROLE_" + user.getRole().name())
                    .accountLocked(user.getStatus() == UserStatus.BLOCKED)
                    .disabled(user.getStatus() == UserStatus.MARKED_FOR_DELETION)
                    .build();
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtDecoder jwtDecoder,
                                           JwtAuthenticationConverter jwtAuthenticationConverter,
                                           AccountStatusService accountStatusService,
                                           PresenceService presenceService) {
        http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/error").permitAll()
                    // WebSocket handshake; STOMP sessions authenticate with the JWT in the CONNECT frame
                    .requestMatchers("/ws", "/ws/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    // only reachable when springdoc is enabled (dev profile)
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                    .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/user").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/tags").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().authenticated()
            )
            .csrf(AbstractHttpConfigurer::disable)
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtConfigurer ->
                jwtConfigurer.decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)
            ))
            // a valid token of a meanwhile blocked or deleted account is rejected
            .addFilterAfter(new AccountStatusFilter(accountStatusService), BearerTokenAuthenticationFilter.class)
            // every authenticated request keeps the user online
            .addFilterAfter(new PresenceFilter(presenceService), AccountStatusFilter.class);

        return http.build();
    }
}