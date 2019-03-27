package com.tuaier.mvc.v3.servlet;

import com.tuaier.mvc.annotation.TuaierRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 保存URL和对象以及方法的关系，方法的参数列表
 */
public class HandlerMapping {
    /** 正则 */
    private Pattern url;
    private Method method;
    private Object controller;
    private Class<?>[] paramTypes;
    /** 形参列表,参数名作为key,位置作为值 */
    private Map<String, Integer> paramIndexMapping;

    public HandlerMapping(Pattern url, Object controller, Method method) {
        this.url = url;
        this.method = method;
        this.controller = controller;

        paramTypes = method.getParameterTypes();
        paramIndexMapping = new HashMap<>();
        putParamIndexMapping(method);
    }

    private void putParamIndexMapping(Method method) {
        // 方法的注解，因为一个字段可以有多个注解以及一个方法有多个参数所以是个二维数组
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length; i ++) {
            for (Annotation a : parameterAnnotations[i]) {
                if (a instanceof TuaierRequestParam) {
                    String paramName = ((TuaierRequestParam) a).value();
                    if (!"".equals(paramName.trim())) {
                        paramIndexMapping.put(paramName, i);
                    }
                }
            }
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] == HttpServletRequest.class || parameterTypes[i] == HttpServletResponse.class) {
                paramIndexMapping.put(parameterTypes[i].getName(), i);
            }
        }
    }

    public Pattern getUrl() {
        return url;
    }

    public Method getMethod() {
        return method;
    }

    public Object getController() {
        return controller;
    }

    public Class<?>[] getParamTypes() {
        return paramTypes;
    }

    public Map<String, Integer> getParamIndexMapping() {
        return paramIndexMapping;
    }
}
