package com.parkerjtech.backendserviceapi.model;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public record CreateLeadCommand(
    @NotBlank @Size(max=100) String name,
    @NotBlank @Size(max=150) String company,
    @NotNull Lead.ServiceInterest serviceInterest,
    @NotNull Lead.Source source,
    @NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer=7,fraction=2) BigDecimal estimatedValue,
    Lead.ContactPreference contactPreference) {
    public CreateLeadCommand {
        name=name==null?null:name.strip();
        company=company==null?null:company.strip();
    }
}
