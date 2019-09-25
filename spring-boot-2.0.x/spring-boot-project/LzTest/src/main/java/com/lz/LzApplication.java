package com.lz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ServiceLoader;

@SpringBootApplication
public class LzApplication {


	public static void main(String[] args) {
		 // 创建SpringApplication 对象
		SpringApplication application = new SpringApplication(LzApplication.class);
		application.run(args);
	}
}
