package com.tuaier.demo.controller;

import com.tuaier.demo.service.IDemoService;
import com.tuaier.mvc.annotation.TuaierAutowired;
import com.tuaier.mvc.annotation.TuaierController;
import com.tuaier.mvc.annotation.TuaierRequestMapping;
import com.tuaier.mvc.annotation.TuaierRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 * @author tuaier
 * @since 2019-03-25
 */
@TuaierController
@TuaierRequestMapping("/tuaier")
public class DemoController {

    @TuaierAutowired
    private IDemoService demoService;

    @TuaierRequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp, @TuaierRequestParam("name") String name) {
        String result = demoService.getName(name);
        //String result = "My name is " + name;
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @TuaierRequestMapping("/add")
    public void add(HttpServletRequest req, HttpServletResponse resp, @TuaierRequestParam("a") Integer a, @TuaierRequestParam("b") Integer b) {
        try {
            resp.getWriter().write(a + "+" + b + "=" + (a + b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @TuaierRequestMapping("/sub")
    public void sub (HttpServletRequest req, HttpServletResponse resp, @TuaierRequestParam("a") Double a, @TuaierRequestParam("b") Double b) {
        try {
            resp.getWriter().write(a + "-" + b + "=" + (a - b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @TuaierRequestMapping("/up.*")
    public String up (@TuaierRequestParam("up") Integer up) {
        return "" + up;
    }
}
