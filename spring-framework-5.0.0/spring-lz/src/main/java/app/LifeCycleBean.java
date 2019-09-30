package app;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class LifeCycleBean implements BeanNameAware, BeanFactoryAware, BeanClassLoaderAware, BeanPostProcessor, InitializingBean, DisposableBean {

	private String test;

	public String getTest() {
		return test;
	}

	public void setTest(String test) {
		System.out.println("属性注入");
		this.test = test;
	}



	public LifeCycleBean() {
		System.out.println("构造器调用");
	}


	public void display(){
		System.out.println("销毁方法");
	}
	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		System.out.println("BeanFactoryAware 被调用...");
	}

	@Override
	public void setBeanName(String name) {
		System.out.println("BeanNameAware");
	}

	@Override
	public void setBeanClassLoader(@NonNull ClassLoader classLoader) {
		System.out.println("BeanClassLoaderAware");
	}
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("BeanPostProcessor postProcessBeforeInitialization 被调用...");
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		System.out.println("BeanPostProcessor postProcessAfterInitialization 被调用...");
		return bean;
	}

	@Override
	public void destroy() throws Exception {
		System.out.println("DisposableBean destroy");
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		System.out.println("InitializingBean afterPropertiesSet  被调动");
	}

	@PostConstruct
	public void initMethod(){
		System.out.println("init-method 被调用...");
	}

	@PreDestroy
	public  void destroyMethod(){
		System.out.println("destroy-method 被调用...");
	}
}
