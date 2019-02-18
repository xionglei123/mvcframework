package com.xionglei.demo.mvc.service.impl;

import com.xionglei.demo.mvc.service.IDemoService;
import com.xionglei.framework.annotation.GPService;

@GPService
public class DemoService implements IDemoService {

    public String get(String name) {
        return "My name is " + name;
    }
}
