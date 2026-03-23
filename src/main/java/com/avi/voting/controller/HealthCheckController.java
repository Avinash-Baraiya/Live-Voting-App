package com.avi.voting.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthCheckController {
    @RequestMapping
    public String healthCheck() {
        return "OK";
    }
}
