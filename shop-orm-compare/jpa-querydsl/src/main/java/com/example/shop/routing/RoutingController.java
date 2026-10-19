package com.example.shop.routing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoutingController {

    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    // 쓰기는 server 1(shop), 읽기는 server 2(shop_reader)
    @GetMapping("/api/db/routing")
    public RoutingResponse routing() {
        return new RoutingResponse("jpa-querydsl", routingService.inWriteTransaction(), routingService.inReadOnlyTransaction());
    }
}
