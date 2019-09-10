package app;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

public class User implements BeanPostProcessor {

	private String name = "美年达";

	@Override
	public String toString() {
		return "User{" +
				"name='" + name + '\'' +
				'}';
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return null;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return null;
	}
}
