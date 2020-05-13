package com.ethan.servlet;

import com.ethan.annotation.MyAutowired;
import com.ethan.annotation.MyController;
import com.ethan.annotation.MyRequestMapping;
import com.ethan.annotation.MyService;

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

/**
 *  <Description>
 *  @author ethan
 *  @version 1.0
 *  @date 2020/05/11
 */
public class MyDispatcherServlet extends HttpServlet{
    Properties contextConfig = new Properties();
    
    List<String> classNames = new ArrayList<>();
    Map<String, Object> ioc = new HashMap<>();
    Map<String,Method> handlerMapping = new HashMap<>();
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDisPatcher(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doDisPatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        //处理成性对路径
        String contextPath = req.getContextPath();

        url = url.replace(contextPath,"").replaceAll("/+","/");
        if(!handlerMapping.containsKey(url)){
            resp.getWriter().write("404 not found");
            return;
        }

        Method method = handlerMapping.get(url);

        Class<?>[] parameterTypes = method.getParameterTypes();

        Object[] objects = new Object[parameterTypes.length];
        //写死方法，req，resp是必传的，String是因为我controller里只有String类型的参数，这里并不是真实SpringMVC处理方式。
        Map<String,String[]> map = req.getParameterMap();
        for (int i =0;i<parameterTypes.length;i++) {
            String params = parameterTypes[i].getSimpleName();
            if(params.equals("HttpServletRequest")) {
                objects[i] = req;
                continue;
            }
            if(params.equals("HttpServletResponse")) {
                objects[i] = resp;
                continue;
            }
            //参数传递是二维数组，所以要遍历取值。
            if(params.equals("String")){
                for (Map.Entry<String, String[]> stringEntry : map.entrySet()) {
                    String value = Arrays.toString(stringEntry.getValue()).replaceAll("\\[|\\]","").replaceAll(",\\s","");
                    //这里写法欠妥当，如果真的传了两个值是同一个字段，会出现只有最后一个字段的问题，目前测试controller只有一个，所以没有暴露问题。
                    //下个版本可以补充优化掉这个问题
                    objects[i] = value;
                }
            }
        }
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName),objects);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        //初始化扫描到的类并放入ioc容器
        doInstance();
        //完成依赖注入
        doAutowired();
        //初始化handlerMapping
        doHandlerMapping();
        System.out.println("springMVC is init success");
    }
    //初始化handlerMapping
    private void doHandlerMapping() {
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(MyController.class)) {
                continue;
            }

            MyRequestMapping myRequestMapping = clazz.getAnnotation(MyRequestMapping.class);

            for (Method method : clazz.getMethods()) {
                String baseUrl = "";
                baseUrl = myRequestMapping.value();
                if(!method.isAnnotationPresent(MyRequestMapping.class)){
                    continue;
                }
                MyRequestMapping myRequestMappingMethod = method.getAnnotation(MyRequestMapping.class);
                //防止多个"/" 直接全部replace
                baseUrl = ("/"+baseUrl+"/"+myRequestMappingMethod.value()).replaceAll("/+","/");
                System.out.println(baseUrl);
                handlerMapping.put(baseUrl,method);
            }
        }
    }
    //完成依赖注入
    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(MyAutowired.class)) {
                    MyAutowired myAutowired = field.getClass().getAnnotation(MyAutowired.class);
                    String beanName = myAutowired.value().trim();
                    if (beanName.equals("")) {
                        beanName = field.getType().getName();
                    }
                    field.setAccessible(true);
                    try {
                        field.set(entry.getValue(),ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    //初始化扫描到的类并放入ioc容器
    private void doInstance() {
        //首先确定要扫描的类，这里我只以controller和service做例子，先获取这个类，看是否含有这两种注解
        if (classNames.isEmpty()) {
            return;
        }
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(MyController.class)) {
                    //获取实例并放入ioc，以beanName为名字
                    Object object = clazz.newInstance();
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,object);
                }else if(clazz.isAnnotationPresent(MyService.class)){
                    //如果是service 考虑可能出现的注解上面自己命名的情况，所以分两种情况，一种是可以获取的到注解的value，一种是获取不到则用类名代替。
                    Object object = clazz.newInstance();
                    MyService myService = clazz.getAnnotation(MyService.class);
                    String value = myService.value();
                    if (!value.trim().equals("")) {
                        String beanName = value;
                        ioc.put(beanName,object);
                    }else{
                        String beanName = toLowerFirstCase(clazz.getSimpleName());
                        ioc.put(beanName,object);
                    }
                    //查找接口放入ioc
                    for (Class<?> anInterface : clazz.getInterfaces()) {
                        if(ioc.containsKey(anInterface.getName())){
                            try {
                                throw new Exception("ioc has a same source");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        ioc.put(anInterface.getName(),object);
                    }

                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0]+=32;
        return String.valueOf(chars);

    }

    //扫描相关的类
    private void doScanner(String scanPackage) {
        //先把.替换成/
        URL url = this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.","/"));
        File file = new File(url.getFile());
        for (File listFile : file.listFiles()) {
            if (listFile.isDirectory()) {
                doScanner(scanPackage+"."+listFile.getName());
            }
            if(listFile.getName().endsWith(".class")){
               String uri = scanPackage+"."+listFile.getName().replace(".class","");
               classNames.add(uri);
            }
        }
    }

    //加载配置文件
    private void doLoadConfig(String contextConfigLocation) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
