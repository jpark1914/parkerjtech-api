package com.parkerjtech.backendserviceapi.config;

import com.parkerjtech.backendserviceapi.utils.ApiProblemWriter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain demoSecurity(HttpSecurity http,ApiProblemWriter problems) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/demo/**","/error").permitAll().anyRequest().denyAll())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .exceptionHandling(errors -> errors
                .accessDeniedHandler((req,res,ex) -> problems.write(res,403,"CSRF_INVALID","The CSRF token is missing or invalid."))
                .authenticationEntryPoint((req,res,ex) -> problems.write(res,401,"WORKSPACE_REQUIRED","Start your demo session using GET /api/demo/session.")))
            .build();
    }
}
