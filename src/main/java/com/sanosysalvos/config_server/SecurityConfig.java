package com.sanosysalvos.config_server;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Autenticación básica del servidor de configuración.
 *
 * Con CONFIG_SERVER_AUTH_PASSWORD definida, todo exige credenciales salvo /actuator/health (healthcheck de
 * Docker/ECS, que solo devuelve el estado). Los clientes las mandan con SPRING_CLOUD_CONFIG_USERNAME y
 * SPRING_CLOUD_CONFIG_PASSWORD. Sin ella el servidor queda abierto, solo para desarrollo local, y lo avisa en el log.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final String usuario;
    private final String clave;

    public SecurityConfig(
            @Value("${config-server.auth.user:config}") String usuario,
            @Value("${config-server.auth.password:}") String clave) {
        this.usuario = usuario;
        this.clave = clave;
    }

    private boolean autenticacionActiva() {
        return !clave.isBlank();
    }

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        if (!autenticacionActiva()) {
            log.warn("CONFIG_SERVER_AUTH_PASSWORD no está definida: el Config Server atiende SIN autenticación. "
                    + "Solo es aceptable en desarrollo local.");
        }
        http
                // API de solo lectura para otros servicios, sin sesión ni formularios: CSRF no aplica
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    if (autenticacionActiva()) {
                        auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                                .anyRequest().authenticated();
                    } else {
                        auth.anyRequest().permitAll();
                    }
                })
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /* Sin contraseña configurada se crea un usuario con clave aleatoria imposible de adivinar, para que Spring Boot
       no genere (ni escriba en el log) una contraseña propia */
    @Bean
    UserDetailsService usuarios(PasswordEncoder encoder) {
        String efectiva = autenticacionActiva() ? clave : UUID.randomUUID().toString();
        return new InMemoryUserDetailsManager(
                User.withUsername(usuario).password(encoder.encode(efectiva)).roles("CONFIG_CLIENT").build());
    }
}
