package com.tttn.qlnvl.auth.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/login")
    String login() {
        return "auth/login";
    }
}
