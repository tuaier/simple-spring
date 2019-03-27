package com.tuaier.mvc.v3.servlet;

import com.tuaier.mvc.annotation.TuaierAutowired;
import com.tuaier.mvc.annotation.TuaierController;
import com.tuaier.mvc.annotation.TuaierRequestMapping;
import com.tuaier.mvc.annotation.TuaierService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 入口
 *
 * @author tuaier
 * @since 2019-03-25
 */
public class TuaierDispatcherServlet extends HttpServlet {
    /**
     * 主配置文件
     */
    private Properties contextConfig = new Properties();

    /**
     * 类名保存容器
     */
    private List<String> classNameContainer = new ArrayList<>();

    /**
     * ioc容器
     */
    private Map<String, Object> ioc = new HashMap<>();

    /**
     * mapping容器，保存url和method对应关系以及对象等的关系
     * 彩蛋：为什么不用Map了？
     * 用Map的话key只能为url，handlerMapping本身的功能就是把url和method对应，本身已经具备Map的功能，不必将关系强加给Map
     * 单一职责，最少知道原则
     * 从性能说，Map遍历与自己遍历
     */
    private List<HandlerMapping> handlerMapping = new ArrayList<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 6、调用方法
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 EXCEPTION, DETAIL: " + Arrays.toString(e.getStackTrace()));
        }
    }

    /**
     * 调用阶段
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {


        HandlerMapping handlerMapping =  getHandlerMapping(req);
        if (handlerMapping == null) {
            resp.getWriter().write("404 NOT FOUNT.");
            return;
        }
        Map<String, Integer> paramIndexMapping = handlerMapping.getParamIndexMapping();

        Class<?>[] paramTypes = handlerMapping.getParamTypes();
        Object[] paramValues = new Object[paramTypes.length];
        Map<String, String[]> parameterMap = req.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String value = Arrays.toString(entry.getValue()).replaceAll("\\[|]", "").replaceAll("\\s", "");
            if (!paramIndexMapping.containsKey(entry.getKey())) {
                continue;
            }
            int index = paramIndexMapping.get(entry.getKey());
            paramValues[index] = convert(paramTypes[index],value);
        }
        if (paramIndexMapping.containsKey(HttpServletRequest.class.getName())) {
            int reqIndex = paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
        }

        if (paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
            int respIndex = paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
        }

        Object returnResult = handlerMapping.getMethod().invoke(handlerMapping.getController(), paramValues);

        if (returnResult == null || returnResult instanceof Void) {
            return;
        }
        resp.getWriter().write(returnResult.toString());
    }

    private HandlerMapping getHandlerMapping(HttpServletRequest req) {
        if (handlerMapping.isEmpty()) {
            return null;
        }
        // 获得相对路径
        String uri = req.getRequestURI();
        String context = req.getContextPath();
        uri = uri.replaceAll(context, "").replaceAll("/+", "/");

        for (HandlerMapping mapping : this.handlerMapping) {
            Matcher matcher = mapping.getUrl().matcher(uri);
            if (!matcher.matches()) {
                continue;
            }
            return mapping;
        }
        return null;
    }

    /**
     * 初始化阶段
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        /*
         * 初始化
         *  1、加载配置文文件
         *  2、扫描类
         *  3、实例化对象并刚入ioc容器
         *  4、依赖注入
         *  5、初始化Mapping
         */
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        doScan(contextConfig.getProperty("scanPackage"));
        doInstance();
        doAutowired();
        doMapping();
        System.out.println("Framework init success.");
    }

    /**
     * 加载配置文件
     *
     * @param contextConfigLocation 配置文件位置
     */
    private void doLoadConfig(String contextConfigLocation) {
        // 加载配置文件到properties
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭流
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 扫描scanPackage路径下所有的类
     *
     * @param scanPackage 包路径
     */
    private void doScan(String scanPackage) {

        /*
         * 根据报名获取文件路径
         * 1、将包路径转为文件路径，获取类路径
         * 2、遍历路径，扫描所有类（递归）
         *
         */
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        for (File file : classPath.listFiles()) {
            String fileName = file.getName();
            // 如果是文件夹则递归向内扫描
            if (file.isDirectory()) {
                doScan(scanPackage + "." + fileName);
            } else {
                if (!fileName.endsWith(".class")) {
                    continue;
                }
                String className = scanPackage + "." + fileName.replace(".class", "");
                classNameContainer.add(className);
            }
        }
    }

    /**
     * 初始化对象放入ioc容器
     */
    private void doInstance() {
        if (classNameContainer.isEmpty()) {
            return;
        }

        try {
            /*
             * 初始化对象，只有加了注解的类才需要初始化由ioc容器管理
             * 1、Controller初始化
             * 2、Service初始化
             */
            for (String className : classNameContainer) {
                Class<?> clazz = Class.forName(className);

                if (clazz.isAnnotationPresent(TuaierController.class)) {
                    // key获取
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    Object obj = clazz.newInstance();
                    ioc.put(beanName, obj);
                } else if (clazz.isAnnotationPresent(TuaierService.class)) {
                    // 1、先取自定义名
                    TuaierService tuaierService = clazz.getAnnotation(TuaierService.class);
                    String beanName = tuaierService.value();
                    // 2、如果为空则取默认名（首字母小写）
                    if ("".equals(beanName)) {
                        beanName = lowerFirstCase(clazz.getSimpleName());
                    }
                    Object obj = clazz.newInstance();
                    ioc.put(beanName, obj);
                    // 3、注入接口无法实例化，根据类型自动赋值,扫描所有借口注册成对应实现类
                    for (Class<?> cls : clazz.getInterfaces()) {
                        String clsName = cls.getName();
                        if (ioc.containsKey(clsName)) {
                            throw new Exception("The bean " + clsName + " is exists.");
                        }
                        ioc.put(clsName, obj);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * DI依赖注入
     */
    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 获取所有的字段，包括private/protected/default
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                if (!field.isAnnotationPresent(TuaierAutowired.class)) {
                    continue;
                }
                // 先根据自定义名字注入，为空则根据类型注入
                TuaierAutowired tuaierAutowired = field.getAnnotation(TuaierAutowired.class);
                String beanName = tuaierAutowired.value().trim();
                if ("".equals(beanName)) {
                    if (field.getType().isInterface()) {
                        beanName = field.getType().getName();
                    } else {
                        beanName = lowerFirstCase(field.getType().getName());
                    }
                }
                // 如果是public之外的修饰符，允许强制赋值
                field.setAccessible(true);
                try {
                    // 反射机制动态给字段赋值（加强学习）
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 初始化url和method的关系
     */
    private void doMapping() {
        if (ioc.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(TuaierController.class)) {
                continue;
            }
            // 类上的地址获取
            String baseURI = "";
            if (clazz.isAnnotationPresent(TuaierRequestMapping.class)) {
                baseURI = clazz.getAnnotation(TuaierRequestMapping.class).value();
            }
            // 获取所有方法上的地址
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(TuaierRequestMapping.class)) {
                    continue;
                }
                String methodURI = method.getAnnotation(TuaierRequestMapping.class).value();
                // URL处理/
                String regexURI = ("/" + baseURI + "/" + methodURI).replace("", "").replaceAll("/+", "/");

                HandlerMapping handlerMapping = new HandlerMapping(Pattern.compile(regexURI), entry.getValue(), method);
                this.handlerMapping.add(handlerMapping);
                System.out.println("Mapped :" + Pattern.compile(regexURI) + "," + method);
            }

        }

    }

    /**
     * 首字符小写
     * A-Z的ASCII码为65-90
     */

    private String lowerFirstCase(String simpleName) {
        // 大写A和Z的ASCII码
        int upA = 65, upZ = 90;
        char[] chars = simpleName.toCharArray();
        // 只翻译大写字母
        if (upA <= chars[0] && chars[0] <= upZ) {
            chars[0] += 32;
        }
        return String.valueOf(chars);
    }


    /**
     * 类型转换器，把String转为其他类型
     * URL传进来的都是String类型的，HTTP基于字符串协议
     *
     * @param clazz 类型
     * @param value 值
     * @return <CODE>Object</CODE>
     */
    private Object convert(Class<?> clazz, String value) {
        // TODO 策略模式解决
        if (Integer.class == clazz) {
            return Integer.valueOf(value);
        } else if (Double.class == clazz) {
            return Double.valueOf(value);
        }
        return value;
    }
}
