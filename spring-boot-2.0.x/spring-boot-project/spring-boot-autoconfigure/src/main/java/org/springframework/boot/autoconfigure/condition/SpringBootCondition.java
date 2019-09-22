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

package org.springframework.boot.autoconfigure.condition;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Base of all {@link Condition} implementations used with Spring Boot. Provides sensible
 * logging to help the user diagnose what classes are loaded.
 * 实现 Condition 接口，Spring Boot Condition 的抽象基类，主要用于提供相应的日志，帮助开发者判断哪些被进行加载
 *
 * @author Phillip Webb
 * @author Greg Turnquist
 * @since 1.0.0
 */
public abstract class SpringBootCondition implements Condition {

	private final Log logger = LogFactory.getLog(getClass());

	@Override
	public final boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		// <1> 获得注解的是方法名还是类名
		String classOrMethodName = getClassOrMethodName(metadata);
		try {
			// <2> 条件匹配结果
			//#getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) 抽象方法，执行匹配，返回匹配结果。
			//这是一个抽象方法，由子类进行实现。
			//org.springframework.boot.autoconfigure.condition.ConditionOutcome ，匹配结果。
			//org.springframework.boot.autoconfigure.condition.ConditionMessage ，匹配消息。
			ConditionOutcome outcome = getMatchOutcome(context, metadata);
			// <3> 打印结果
			logOutcome(classOrMethodName, outcome);
			// <4> 记录
			recordEvaluation(context, classOrMethodName, outcome);
			// <5> 返回是否匹配
			return outcome.isMatch();
		} catch (NoClassDefFoundError ex) {
			throw new IllegalStateException("Could not evaluate condition on " + classOrMethodName + " due to "
					+ ex.getMessage() + " not " + "found. Make sure your own configuration does not rely on "
					+ "that class. This can also happen if you are "
					+ "@ComponentScanning a springframework package (e.g. if you "
					+ "put a @ComponentScan in the default package by mistake)", ex);
		} catch (RuntimeException ex) {
			throw new IllegalStateException("Error processing condition on " + getName(metadata), ex);
		}
	}

	private String getName(AnnotatedTypeMetadata metadata) {
		// 类
		if (metadata instanceof AnnotationMetadata) {
			return ((AnnotationMetadata) metadata).getClassName();
		}
		// 方法
		if (metadata instanceof MethodMetadata) {
			MethodMetadata methodMetadata = (MethodMetadata) metadata;
			return methodMetadata.getDeclaringClassName() + "." + methodMetadata.getMethodName();
		}
		return metadata.toString();
	}

	private static String getClassOrMethodName(AnnotatedTypeMetadata metadata) {
		if (metadata instanceof ClassMetadata) {
			ClassMetadata classMetadata = (ClassMetadata) metadata;
			return classMetadata.getClassName();
		}
		MethodMetadata methodMetadata = (MethodMetadata) metadata;
		return methodMetadata.getDeclaringClassName() + "#" + methodMetadata.getMethodName();
	}

	protected final void logOutcome(String classOrMethodName, ConditionOutcome outcome) {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(getLogMessage(classOrMethodName, outcome));
		}
	}

	private StringBuilder getLogMessage(String classOrMethodName, ConditionOutcome outcome) {
		StringBuilder message = new StringBuilder();
		message.append("Condition ");
		message.append(ClassUtils.getShortName(getClass()));
		message.append(" on ");
		message.append(classOrMethodName);
		message.append(outcome.isMatch() ? " matched" : " did not match");
		if (StringUtils.hasLength(outcome.getMessage())) {
			message.append(" due to ");
			message.append(outcome.getMessage());
		}
		return message;
	}

	private void recordEvaluation(ConditionContext context, String classOrMethodName, ConditionOutcome outcome) {
		if (context.getBeanFactory() != null) {
			ConditionEvaluationReport.get(context.getBeanFactory()).recordConditionEvaluation(classOrMethodName, this,
					outcome);
		}
	}

	/**
	 * Determine the outcome of the match along with suitable log output.
	 *
	 * @param context  the condition context
	 * @param metadata the annotation metadata
	 * @return the condition outcome
	 */
	public abstract ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata);

	/**
	 * Return true if any of the specified conditions match.
	 * 判断是否匹配指定的 Condition 们中的任一一个
	 * @param context    the context
	 * @param metadata   the annotation meta-data
	 * @param conditions conditions to test
	 * @return {@code true} if any condition matches.
	 */
	protected final boolean anyMatches(ConditionContext context, AnnotatedTypeMetadata metadata,
									   Condition... conditions) {
		// 遍历 Condition
		for (Condition condition : conditions) {
			// 执行是否匹配
			if (matches(context, metadata, condition)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return true if any of the specified condition matches.
	 *
	 * @param context   the context
	 * @param metadata  the annotation meta-data
	 * @param condition condition to test
	 * @return {@code true} if the condition matches.
	 */
	protected final boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata, Condition condition) {
		if (condition instanceof SpringBootCondition) {
			// 如果是 SpringBootCondition 类型，执行 SpringBootCondition 的直接匹配方法（无需日志）
			return ((SpringBootCondition) condition).getMatchOutcome(context, metadata).isMatch();
		}
		return condition.matches(context, metadata);
	}

}
