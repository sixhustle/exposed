package com.example.shop.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderPlaceRequest(
        @Positive long userId,
        @Size(min = 1, max = 100) List<@Valid OrderLineRequest> lines) {
}
