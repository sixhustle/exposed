package com.example.shop.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(
        @Email @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 50) String name) {
}
