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

package org.springframework.boot;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import groovy.lang.Closure;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.filter.AbstractTypeHierarchyTraversingFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Loads bean definitions from underlying sources, including XML and JavaConfig. Acts as a
 * simple facade over {@link AnnotatedBeanDefinitionReader},
 * {@link XmlBeanDefinitionReader} and {@link ClassPathBeanDefinitionScanner}. See
 * {@link SpringApplication} for the types of sources that are supported.
 *
 * @author Phillip Webb
 * @see #setBeanNameGenerator(BeanNameGenerator)
 */
class BeanDefinitionLoader {

	/**
	 * 来源的数组
	 */
	private final Object[] sources;

	/**
	 * 注解的 BeanDefinition 读取器
	 */
	private final AnnotatedBeanDefinitionReader annotatedReader;

	/**
	 * XML 的 BeanDefinition 读取器
	 */
	private final XmlBeanDefinitionReader xmlReader;
	/**
	 * Groovy 的 BeanDefinition 读取器
	 */
	private BeanDefinitionReader groovyReader;
	/**
	 * Classpath 的 BeanDefinition 扫描器
	 */
	private final ClassPathBeanDefinitionScanner scanner;
	/**
	 * 资源加载器
	 */
	private ResourceLoader resourceLoader;

	/**
	 * Create a new {@link BeanDefinitionLoader} that will load beans into the specified
	 * {@link BeanDefinitionRegistry}.
	 *
	 * @param registry the bean definition registry that will contain the loaded beans
	 * @param sources  the bean sources
	 */
	BeanDefinitionLoader(BeanDefinitionRegistry registry, Object... sources) {
		Assert.notNull(registry, "Registry must not be null");
		Assert.notEmpty(sources, "Sources must not be empty");
		this.sources = sources;
		// 创建AnnotatedBeanDefinitionReader 对象
		this.annotatedReader = new AnnotatedBeanDefinitionReader(registry);
		// 创建XmlBeanDefinitionReader 对象
		this.xmlReader = new XmlBeanDefinitionReader(registry);
		//创建 GroovyBeanDefinitionReader 对象
		if (isGroovyPresent()) {
			this.groovyReader = new GroovyBeanDefinitionReader(registry);
		}
		// 创建 ClassPathBeanDefinitionScanner 对象
		this.scanner = new ClassPathBeanDefinitionScanner(registry);
		this.scanner.addExcludeFilter(new ClassExcludeFilter(sources));
	}

	/**
	 * Set the bean name generator to be used by the underlying readers and scanner.
	 *
	 * @param beanNameGenerator the bean name generator
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.annotatedReader.setBeanNameGenerator(beanNameGenerator);
		this.xmlReader.setBeanNameGenerator(beanNameGenerator);
		this.scanner.setBeanNameGenerator(beanNameGenerator);
	}

	/**
	 * Set the resource loader to be used by the underlying readers and scanner.
	 *
	 * @param resourceLoader the resource loader
	 */
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
		this.xmlReader.setResourceLoader(resourceLoader);
		this.scanner.setResourceLoader(resourceLoader);
	}

	/**
	 * Set the environment to be used by the underlying readers and scanner.
	 *
	 * @param environment the environment
	 */
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.annotatedReader.setEnvironment(environment);
		this.xmlReader.setEnvironment(environment);
		this.scanner.setEnvironment(environment);
	}

	/**
	 * Load the sources into the reader.
	 *
	 * @return the number of loaded beans
	 */
	public int load() {
		int count = 0;
		//  遍历 sources 数组，逐个加载
		for (Object source : this.sources) {
			count += load(source);
		}
		return count;
	}

	private int load(Object source) {
		Assert.notNull(source, "Source must not be null");
		// <1> 如果是 Class 类型，则使用 AnnotatedBeanDefinitionReader 执行加载
		if (source instanceof Class<?>) {
			return load((Class<?>) source);
		}
		// <2> 如果是 Resource 类型，则使用 XmlBeanDefinitionReader 执行加载
		if (source instanceof Resource) {
			return load((Resource) source);
		}
		// <3> 如果是 Package 类型，则使用 ClassPathBeanDefinitionScanner 执行加载
		if (source instanceof Package) {
			return load((Package) source);
		}
		// <4> 如果是 CharSequence 类型，则各种尝试去加载
		if (source instanceof CharSequence) {
			return load((CharSequence) source);
		}
		// <5> 无法处理的类型，抛出 IllegalArgumentException 异常
		throw new IllegalArgumentException("Invalid source type " + source.getClass());
	}

	private int load(Class<?> source) {
		if (isGroovyPresent() && GroovyBeanDefinitionSource.class.isAssignableFrom(source)) {
			// Any GroovyLoaders added in beans{} DSL can contribute beans here
			GroovyBeanDefinitionSource loader = BeanUtils.instantiateClass(source, GroovyBeanDefinitionSource.class);
			load(loader);
		}
		// <1> 如果是 Component ，则执行注册
		if (isComponent(source)) {
			this.annotatedReader.register(source);
			return 1;
		}
		return 0;
	}

	private int load(GroovyBeanDefinitionSource source) {
		int before = this.xmlReader.getRegistry().getBeanDefinitionCount();
		((GroovyBeanDefinitionReader) this.groovyReader).beans(source.getBeans());
		int after = this.xmlReader.getRegistry().getBeanDefinitionCount();
		return after - before;
	}

	private int load(Resource source) {
		if (source.getFilename().endsWith(".groovy")) {
			if (this.groovyReader == null) {
				throw new BeanDefinitionStoreException("Cannot load Groovy beans without Groovy on classpath");
			}
			return this.groovyReader.loadBeanDefinitions(source);
		}
		return this.xmlReader.loadBeanDefinitions(source);
	}

	/**
	 * ClassPathBeanDefinitionScanner
	 * @param source
	 * @return
	 */
	private int load(Package source) {
		return this.scanner.scan(source.getName());
	}

	/**
	 *  各种尝试加载
	 * @param source
	 * @return
	 */
	private int load(CharSequence source) {
		// <1> 解析 source 。因为，有可能里面带有占位符。
		String resolvedSource = this.xmlReader.getEnvironment().resolvePlaceholders(source.toString());
		// Attempt as a Class
		// <2> 尝试按照 Class 进行加载
		try {
			return load(ClassUtils.forName(resolvedSource, null));
		} catch (IllegalArgumentException | ClassNotFoundException ex) {
			// swallow exception and continue
		}
		// Attempt as resources
		// <3> 尝试按照 Resource 进行加载
		Resource[] resources = findResources(resolvedSource);
		int loadCount = 0;
		boolean atLeastOneResourceExists = false;
		for (Resource resource : resources) {
			if (isLoadCandidate(resource)) {
				atLeastOneResourceExists = true;
				loadCount += load(resource);
			}
		}
		if (atLeastOneResourceExists) {
			return loadCount;
		}
		// Attempt as package
		// <4> 尝试按照 Package 进行加载
		Package packageResource = findPackage(resolvedSource);
		if (packageResource != null) {
			return load(packageResource);
		}
		// <5> 无法处理，抛出 IllegalArgumentException 异常
		throw new IllegalArgumentException("Invalid source '" + resolvedSource + "'");
	}

	private boolean isGroovyPresent() {
		return ClassUtils.isPresent("groovy.lang.MetaClass", null);
	}

	private Resource[] findResources(String source) {
		// 创建 ResourceLoader 对象
		ResourceLoader loader = (this.resourceLoader != null) ? this.resourceLoader
				: new PathMatchingResourcePatternResolver();
		try {
			// 获得 Resource 数组
			if (loader instanceof ResourcePatternResolver) {
				return ((ResourcePatternResolver) loader).getResources(source);
			}
			// 获得 Resource 对象
			return new Resource[]{loader.getResource(source)};
		} catch (IOException ex) {
			throw new IllegalStateException("Error reading source '" + source + "'");
		}
	}

	private boolean isLoadCandidate(Resource resource) {
		// 不存在，则返回 false
		if (resource == null || !resource.exists()) {
			return false;
		}
		if (resource instanceof ClassPathResource) {
			// A simple package without a '.' may accidentally get loaded as an XML
			// document if we're not careful. The result of getInputStream() will be
			// a file list of the package content. We double check here that it's not
			// actually a package.
			String path = ((ClassPathResource) resource).getPath();
			if (path.indexOf('.') == -1) {
				try {
					return Package.getPackage(path) == null;
				} catch (Exception ex) {
					// Ignore
				}
			}
		}
		return true;
	}

	private Package findPackage(CharSequence source) {
		// <X> 获得 source 对应的 Package 。如果存在，则返回
		Package pkg = Package.getPackage(source.toString());
		if (pkg != null) {
			return pkg;
		}
		try {
			// Attempt to find a class in this package
			//创建 ResourcePatternResolver 对象
			ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(getClass().getClassLoader());
			/// 尝试加载 source 目录下的 class 们
			Resource[] resources = resolver
					.getResources(ClassUtils.convertClassNameToResourcePath(source.toString()) + "/*.class");
			// 遍历 resources 数组
			for (Resource resource : resources) {
				String className = StringUtils.stripFilenameExtension(resource.getFilename());
				load(Class.forName(source.toString() + "." + className));
				break;
			}
		} catch (Exception ex) {
			// swallow exception and continue
		}
		// 返回 Package
		return Package.getPackage(source.toString());
	}

	/**
	 * 因为 Configuration 类，上面有 @Configuration 注解，而 @Configuration 上，自带 @Component 注解，所以该方法返回 true 。
	 * @param type
	 * @return
	 */
	private boolean isComponent(Class<?> type) {
		// This has to be a bit of a guess. The only way to be sure that this type is
		// eligible is to make a bean definition out of it and try to instantiate it.
		if (AnnotationUtils.findAnnotation(type, Component.class) != null) {
			return true;
		}
		// Nested anonymous classes are not eligible for registration, nor are groovy
		// closures
		if (type.getName().matches(".*\\$_.*closure.*") || type.isAnonymousClass() || type.getConstructors() == null
				|| type.getConstructors().length == 0) {
			return false;
		}
		return true;
	}

	/**
	 * Simple {@link TypeFilter} used to ensure that specified {@link Class} sources are
	 * not accidentally re-added during scanning.
	 */
	private static class ClassExcludeFilter extends AbstractTypeHierarchyTraversingFilter {

		private final Set<String> classNames = new HashSet<>();

		ClassExcludeFilter(Object... sources) {
			super(false, false);
			for (Object source : sources) {
				if (source instanceof Class<?>) {
					this.classNames.add(((Class<?>) source).getName());
				}
			}
		}

		@Override
		protected boolean matchClassName(String className) {
			return this.classNames.contains(className);
		}

	}

	/**
	 * Source for Bean definitions defined in Groovy.
	 */
	@FunctionalInterface
	protected interface GroovyBeanDefinitionSource {

		Closure<?> getBeans();

	}

}
