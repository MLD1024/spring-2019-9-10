/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.properties;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Utility class to memorize {@code @Bean} definition meta data during initialization of
 * the bean factory.
 *
 * @author Dave Syer
 * @since 1.1.0
 */
public class ConfigurationBeanFactoryMetadata implements BeanFactoryPostProcessor {

	/**
	 * The bean name that this class is registered with.
	 * FactoryMetadata 的映射
	 * KEY ：Bean 的名字
	 */
	public static final String BEAN_NAME = ConfigurationBeanFactoryMetadata.class.getName();

	private ConfigurableListableBeanFactory beanFactory;

	private final Map<String, FactoryMetadata> beansFactoryMetadata = new HashMap<>();

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		// <1> 初始化 beanFactory 属性
		this.beanFactory = beanFactory;
		// <2> 遍历所有的 BeanDefinition 的名字们
		for (String name : beanFactory.getBeanDefinitionNames()) {
			// <2.1> 获得 BeanDefinition 对象
			BeanDefinition definition = beanFactory.getBeanDefinition(name);
			// <2.2> 获得 method、bean 属性
			String method = definition.getFactoryMethodName();
			String bean = definition.getFactoryBeanName();
			// <2.3> 添加到 beansFactoryMetadata 中
			if (method != null && bean != null) {
				this.beansFactoryMetadata.put(name, new FactoryMetadata(bean, method));
			}
		}
	}

	public <A extends Annotation> Map<String, Object> getBeansWithFactoryAnnotation(Class<A> type) {
		Map<String, Object> result = new HashMap<>();
		for (String name : this.beansFactoryMetadata.keySet()) {
			if (findFactoryAnnotation(name, type) != null) {
				result.put(name, this.beanFactory.getBean(name));
			}
		}
		return result;
	}

	// 获得指定 Bean 的创建方法上的注解
	public <A extends Annotation> A findFactoryAnnotation(String beanName, Class<A> type) {
		// 获得方法
		Method method = findFactoryMethod(beanName);
		// 获得注解
		return (method != null) ? AnnotationUtils.findAnnotation(method, type) : null;
	}

	public Method findFactoryMethod(String beanName) {
		// 如果不存在，则返回 null
		if (!this.beansFactoryMetadata.containsKey(beanName)) {
			return null;
		}
		AtomicReference<Method> found = new AtomicReference<>(null);
		// 获得 beanName 对应的 FactoryMetadata 对象
		FactoryMetadata metadata = this.beansFactoryMetadata.get(beanName);
		// 获得对应的工厂类
		Class<?> factoryType = this.beanFactory.getType(metadata.getBean());
		// 获得对应的工厂类的方法
		String factoryMethod = metadata.getMethod();
		if (ClassUtils.isCglibProxyClass(factoryType)) {
			factoryType = factoryType.getSuperclass();
		}
		ReflectionUtils.doWithMethods(factoryType, (method) -> {
			if (method.getName().equals(factoryMethod)) {
				found.compareAndSet(null, method);
			}
		});
		return found.get();
	}

	private static class FactoryMetadata {

		/**
		 * Bean 的名字
		 */
		private final String bean;

		/**
		 * Bean 的方法名
		 */
		private final String method;

		FactoryMetadata(String bean, String method) {
			this.bean = bean;
			this.method = method;
		}

		public String getBean() {
			return this.bean;
		}

		public String getMethod() {
			return this.method;
		}

	}

}
