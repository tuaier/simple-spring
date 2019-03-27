package com.tuaier.demo.service.impl;

import com.tuaier.demo.service.IDemoService;
import com.tuaier.mvc.annotation.TuaierService;

/**
 *
 * @author tuaier
 * @since 2019-03-25
 */
@TuaierService
public class DemoServiceImpl implements IDemoService {
    /**
     * @param name 姓名
     * @return <code>String</code> 姓名
     */
    @Override
    public String getName(String name) {
        return "Name is " + name;
    }
}
