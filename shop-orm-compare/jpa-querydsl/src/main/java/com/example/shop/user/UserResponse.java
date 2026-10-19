package com.example.shop.user;

import java.time.LocalDateTime;

public record UserResponse(long id, String email, String name, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getCreatedAt());
    }
}
