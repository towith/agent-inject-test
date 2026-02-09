package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

@RestController
@RequestMapping("/api")
public class HelloController {

    String testField = null;

    public String getTestField() {
        return testField;
    }

    public void setTestField(String testField) {
        this.testField = testField;
    }

    @PostConstruct
    public void init() {
        this.testField = "Hello, Test Field";
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";
    }

}
