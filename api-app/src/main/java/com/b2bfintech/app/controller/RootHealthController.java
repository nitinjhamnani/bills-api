package com.b2bfintech.app.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public liveness response for the App Platform health check, which calls GET /. */
@RestController
public class RootHealthController {

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of("status", "UP");
    }
}
