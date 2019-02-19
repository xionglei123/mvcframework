package com.xionglei.framework.webmvc.servlet;

import com.xionglei.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPDispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();
    private List<String> classNameList = new ArrayList<>();
    //IOC容器
    private Map<String, Object> ioc = new HashMap<>();
    //HandlerMapping
    //private Map<String, Method> handlerMapping = new HashMap<>();
    private List<Handler> handlerMapping = new ArrayList<>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        //6、等待请求
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception");
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
       /* String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        if (!handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found");
            return;
        }
        Method method = handlerMapping.get(url);*/
        Handler handler = getHandler(req);
        if(handler ==null){
            //如果没有匹配上，返回404错误
            resp.getWriter().write("404 Not Found");
            return;
        }

        //获取方法参数列表
        Class<?>[] parameterTypes = handler.method.getParameterTypes();
        //保存所有需要自动赋值的参数值
        Object[] paramValues= new Object[parameterTypes.length];

        Map<String, String[]> params = req. getParameterMap() ;
        for (Map.Entry<String,String[]> param : params.entrySet()) {

            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
            //如果找到匹配的对象，则开始填充参数值
            if(!handler.paramIndexMapping.containsKey(param.getKey())) {continue;}
            int index = handler.paramIndexMapping.get(param.getKey());
            paramValues [index] = castStringValue(value,parameterTypes[index]) ;
        }

        //设置方法中的request和response对象
        Integer reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
        paramValues[reqIndex] = req;
        Integer respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
        paramValues[respIndex] = resp;


        handler.method.invoke(handler.controller,paramValues);

        //System.out.println(method);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        //从这里开始启动
        //1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2、扫描所有相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        //3、初始化所有相关的类
        doInstance();
        //4、自动注入
        doAutoWried();
        //=====================Spring的核心初始化完成
        //5、初始化HandleMapping 属于SpringMvC
        initHandleMapping();
        System.out.println("GP Spring init ...");
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != is) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                String className = scanPackage + "." + file.getName().replace(".class", "");
                classNameList.add(className);
            }
        }

    }

    private void doInstance() {

        if (classNameList.isEmpty()) {
            return;
        }
        try {

            //不是所有类都要实例化，只让加了注解的类
            for (String className : classNameList) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(GPController.class)) {

                    //key默认首字母小写
                    String baseName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(baseName, clazz.newInstance());

                } else if (clazz.isAnnotationPresent(GPService.class)) {


                    //2、如果有自定名字的话，优先用自定义的名字
                    GPService service = clazz.getAnnotation(GPService.class);
                    String beanName = service.value();

                    //1、默认采用首字母小写
                    if ("".equals(beanName.trim())) {
                        beanName = lowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);

                    //3、根据接口类型来赋值
                    for (Class<?> i : clazz.getInterfaces()) {
                        ioc.put(lowerFirstCase(i.getSimpleName()), instance);
                    }

                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    private void doAutoWried() {

        if (ioc.isEmpty()) {
            return;
        }
        //循环ioc容器中所有的类，然后对需要自动赋值的属性进行赋值
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {

            //依赖注入，不管是谁，强吻
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(GPAutowried.class)) {
                    continue;
                }
                GPAutowried gpAutowried = field.getAnnotation(GPAutowried.class);
                String beanName = gpAutowried.value().trim();
                if ("".equals(beanName)) {
                    beanName = lowerFirstCase(field.getType().getSimpleName());
                }
                //暴力访问
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

            }
        }
    }


    private void initHandleMapping() {

        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(GPController.class)) {
                continue;
            }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
                GPRequestMapping requestMapping = clazz.getAnnotation(GPRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            //扫描所有的公共方法
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(GPRequestMapping.class)) {
                    continue;
                }
                GPRequestMapping requestMapping = method.getAnnotation(GPRequestMapping.class);
                String regex = ("/" + baseUrl + requestMapping.value()).replaceAll("/+", "/");

                Pattern pattern = Pattern.compile(regex);
                //handlerMapping.put(methodUrl, method);
                handlerMapping.add(new Handler(pattern,entry.getValue(),method));

                System.out.println("Mapping：" + regex + "，" + method);
            }
        }
    }

    private String lowerFirstCase(String str) {
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private Handler getHandler(HttpServletRequest req) throws Exception {
        if (handlerMapping.isEmpty()) {
            return null;
        }

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : handlerMapping) {
            try {

                Matcher matcher = handler.pattern.matcher(url);//如果没有匹配上继续下一个匹配
                if (!matcher.matches()) {
                    continue;
                }
                return handler;
            } catch (Exception e) {
                throw e;
            }
        }
        return null;
    }

    private Object castStringValue(String value,Class<?> clazz){

        if(clazz == String.class){
            return value;
        }else if(clazz == Integer.class){
            return Integer.valueOf(value);
        }else if(clazz == int.class){
            return Integer.valueOf(value).intValue();
        }else{
            return null;
        }

    }

    /**
     * Handler记录Controller中的RequestMapping和Method的对应关系
     */
    private class Handler {

        protected Object controller;//保存方法对应的实例
        protected Method method;//保存映射的方法
        protected Pattern pattern;
        protected Map<String, Integer> paramIndexMapping;//参数顺序

        /**
         * 构造一个Handler基本的参数
         *
         * @param pattern
         * @param controller
         * @param method
         */
        protected Handler(Pattern pattern, Object controller, Method method) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;

            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);

        }

        private void putParamIndexMapping(Method method) {
            //提取方法中加了注解的参数
            Annotation[][] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                for (Annotation a : pa[i]) {
                    if (a instanceof GPRequestParam) {
                        String paramName = ((GPRequestParam) a).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }
            //提取方法中的request和response参数
            Class<?>[] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length; i++) {
                Class<?> type = paramsTypes[i];
                if (type == HttpServletRequest.class ||
                        type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);

                }
            }
        }

    }


}
