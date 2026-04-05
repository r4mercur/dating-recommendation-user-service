package com.bjarne.datingrecommendationsuserservice.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class HealthResource {
    @GetMapping
    public String health() {
        return "Service is up and running!";
    }
}
