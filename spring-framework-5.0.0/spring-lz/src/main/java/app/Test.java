package app;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import processor.MyBeanFactoryPostProcessor;

import java.util.Arrays;

public class Test {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext app = new AnnotationConfigApplicationContext();
		app.register(AppConfig.class);
		// 实现BeanFactoryPostProcessor 接口
		app.addBeanFactoryPostProcessor(new MyBeanFactoryPostProcessor());
		// java 8 函数式接口
		app.addBeanFactoryPostProcessor(beanFactory->{
			System.out.println(Arrays.toString(beanFactory.getBeanDefinitionNames()));
		});
		// 手动刷新
		app.refresh();
		System.out.println(app.getBean(User.class));
	}
}
