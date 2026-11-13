package com.parkerjtech.backendserviceapi.config;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
@Configuration(proxyBeanMethods=false)
public class JsonConfiguration {
    @Bean JsonMapperBuilderCustomizer strictRequestJson() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
    }
}
