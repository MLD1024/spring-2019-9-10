package test;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
	public static void main(String[] args) {
		// 创建SpringApplication 对象
		SpringApplication application = new SpringApplication(DemoApplication.class);
		application.run(args);
	}
}
