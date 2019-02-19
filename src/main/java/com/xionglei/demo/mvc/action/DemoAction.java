package com.xionglei.demo.mvc.action;

import com.xionglei.demo.mvc.service.impl.DemoService;
import com.xionglei.framework.annotation.GPAutowried;
import com.xionglei.framework.annotation.GPController;
import com.xionglei.framework.annotation.GPRequestMapping;
import com.xionglei.framework.annotation.GPRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@GPController
@GPRequestMapping("/demo")
public class DemoAction {

    @GPAutowried
    private DemoService demoService;

    @GPRequestMapping("/query.json")
    public void query(HttpServletRequest request, HttpServletResponse response,
                        @GPRequestParam("name") String name) {

        String result = demoService.get(name);
        try {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @GPRequestMapping("/add.json")
    public void add(HttpServletRequest request, HttpServletResponse response,
                    @GPRequestParam("a") Integer a,@GPRequestParam("b") Integer b){
        try {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(a+"+"+b +"="+(a+b) );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @GPRequestMapping("/remove.json")
    public void remove(HttpServletRequest request, HttpServletResponse response,
                    @GPRequestParam("name") Integer name){

    }

}
