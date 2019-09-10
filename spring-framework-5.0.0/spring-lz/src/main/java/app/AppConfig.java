package app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("app")
public class AppConfig {

	@Bean
	public User user(){
		return  new User();
	}

}
