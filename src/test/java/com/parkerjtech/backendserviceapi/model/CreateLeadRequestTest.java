package com.parkerjtech.backendserviceapi.model;
import java.math.BigDecimal;
import java.util.stream.Stream;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import static org.assertj.core.api.Assertions.*;
class CreateLeadRequestTest {
    static final jakarta.validation.ValidatorFactory FACTORY=Validation.buildDefaultValidatorFactory();
    @AfterAll static void closeValidator() { FACTORY.close(); }
    static Stream<Arguments> invalidFields() {
        return Stream.of(Arguments.of(null,"Demo","10","name"),Arguments.of(" ","Demo","10","name"),
            Arguments.of("a".repeat(101),"Demo","10","name"),Arguments.of("Sam",null,"10","company"),
            Arguments.of("Sam"," ","10","company"),Arguments.of("Sam","c".repeat(151),"10","company"),
            Arguments.of("Sam","Demo",null,"estimatedValue"),Arguments.of("Sam","Demo","-0.01","estimatedValue"),
            Arguments.of("Sam","Demo","1000000.01","estimatedValue"),Arguments.of("Sam","Demo","1.001","estimatedValue"));
    }
    @ParameterizedTest @MethodSource("invalidFields")
    void rejectsInvalidFields(String name,String company,String amount,String field) {
        var request=new CreateLeadRequest(name,company,Lead.ServiceInterest.WEBSITE,Lead.Source.OTHER,amount==null?null:new BigDecimal(amount),null);
        assertThat(FACTORY.getValidator().validate(request)).extracting(v -> v.getPropertyPath().toString()).contains(field);
    }
    @Test void acceptsTrimmedBoundariesAndOptionalContactPreference() {
        var request=new CreateLeadRequest("  "+"a".repeat(100)+"  "," "+"c".repeat(150)+" ",Lead.ServiceInterest.CUSTOM_APPLICATION,Lead.Source.SOCIAL_MEDIA,new BigDecimal("1000000.00"),null);
        assertThat(FACTORY.getValidator().validate(request)).isEmpty();
        assertThat(request.name()).hasSize(100);
        assertThat(request.company()).hasSize(150);
        assertThat(request.toCommand().contactPreference()).isNull();
    }
    @Test void requiresServiceInterestAndSource() {
        var request=new CreateLeadRequest("Sam","Demo",null,null,BigDecimal.ZERO,Lead.ContactPreference.EMAIL);
        assertThat(FACTORY.getValidator().validate(request)).extracting(v -> v.getPropertyPath().toString()).containsExactlyInAnyOrder("serviceInterest","source");
    }
}
