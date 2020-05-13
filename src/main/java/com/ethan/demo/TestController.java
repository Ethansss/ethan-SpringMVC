package com.ethan.demo;

import com.ethan.annotation.MyController;
import com.ethan.annotation.MyRequestMapping;
import com.ethan.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
@MyRequestMapping("/test")
public class TestController {
	

	
    @MyRequestMapping("/doTest")
    public void test1(HttpServletRequest request, HttpServletResponse response,
    		@MyRequestParam("name") String name){
 		System.out.println(name);
	    try {
            response.getWriter().write( "My name is "+name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	 
	 
    @MyRequestMapping("/doTest2")
    public void test2(HttpServletRequest request, HttpServletResponse response){
        try {
            response.getWriter().println("I don't have name!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
