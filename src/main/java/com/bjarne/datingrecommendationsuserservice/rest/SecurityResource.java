package com.bjarne.datingrecommendationsuserservice.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/private")
public class SecurityResource {

    @GetMapping("/test")
    public String testProtectedEndpoint() {
        return "This is a private endpoint. You are authenticated!";
    }
}
