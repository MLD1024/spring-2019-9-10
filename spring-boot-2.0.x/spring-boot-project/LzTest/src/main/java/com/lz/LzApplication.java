package com.lz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LzApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(LzApplication.class);
		application.run(args);
	}
}
