package com.example.shop.routing;

public record RoutingResponse(String implementation, ConnectionInfo write, ConnectionInfo readOnly) {
}
