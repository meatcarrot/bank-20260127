package com.example.bank.controller.test;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class ThreadTestController {

    @GetMapping("/sleep")
    public String sleep() throws InterruptedException {
        Thread.sleep(5000);
        return "done";
    }
}