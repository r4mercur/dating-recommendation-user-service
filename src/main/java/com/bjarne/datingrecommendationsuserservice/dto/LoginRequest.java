package com.bjarne.datingrecommendationsuserservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotNull(message = "Email is needed")
    @NotEmpty(message = "Email cant be empty")
    @Email(message = "Invalid email format")
    String email,

    @NotNull(message = "Password is needed")
    @NotEmpty(message = "Password cant be empty")
    @Size(min = 1, message = "Password cant be empty")
    String password
) {
}

