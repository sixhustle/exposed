package com.example.shop.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record OrderLineRequest(
        @Positive long itemId,
        @Min(1) @Max(1_000) int quantity) {
}
