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

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

/**
 * {@link Condition} and {@link AutoConfigurationImportFilter} that checks for the
 * presence or absence of specific classes.
 *
 * @author Phillip Webb
 * @see ConditionalOnClass
 * @see ConditionalOnMissingClass
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class OnClassCondition extends SpringBootCondition
		implements AutoConfigurationImportFilter, BeanFactoryAware, BeanClassLoaderAware {
	//通过 Spring Aware 机制，进行注入。
	private BeanFactory beanFactory;

	private ClassLoader beanClassLoader;

	@Override
	public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
		// <1> 获得 ConditionEvaluationReport 对象
		ConditionEvaluationReport report = getConditionEvaluationReport();
		// <2> 执行批量的匹配，并返回匹配结果
		ConditionOutcome[] outcomes = getOutcomes(autoConfigurationClasses, autoConfigurationMetadata);
		// <3.1> 创建 match 数组
		boolean[] match = new boolean[outcomes.length];
		// <3.2> 遍历 outcomes 结果数组
		for (int i = 0; i < outcomes.length; i++) {
			// <3.2.1> 赋值 match 数组
			match[i] = (outcomes[i] == null || outcomes[i].isMatch()); // 如果返回结果结果为空，也认为匹配
			// <3.2.2> 如果不匹配，打印日志和记录。
			if (!match[i] && outcomes[i] != null) {
				// 打印日志
				logOutcome(autoConfigurationClasses[i], outcomes[i]);
				// 记录
				if (report != null) {
					report.recordConditionEvaluation(autoConfigurationClasses[i], this, outcomes[i]);
				}
			}
		}
		// <3.3> 返回 match 数组
		return match;
	}

	private ConditionEvaluationReport getConditionEvaluationReport() {
		if (this.beanFactory != null && this.beanFactory instanceof ConfigurableBeanFactory) {
			return ConditionEvaluationReport.get((ConfigurableListableBeanFactory) this.beanFactory);
		}
		return null;
	}

	private ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		// Split the work and perform half in a background thread. Using a single
		// additional thread seems to offer the best performance. More threads make
		// things worse
		// <1> 在后台线程中将工作一分为二。原因是：
		// * 使用单一附加线程，似乎提供了最好的性能。
		// * 多个线程，使事情变得更糟
		int split = autoConfigurationClasses.length / 2;
		// <2.1> 将前一半，创建一个 OutcomesResolver 对象（新线程）
		OutcomesResolver firstHalfResolver = createOutcomesResolver(autoConfigurationClasses, 0, split,
				autoConfigurationMetadata);
		// <2.2> 将后一半，创建一个 OutcomesResolver 对象
		OutcomesResolver secondHalfResolver = new StandardOutcomesResolver(autoConfigurationClasses, split,
				autoConfigurationClasses.length, autoConfigurationMetadata, this.beanClassLoader);
		// 执行解析（匹配）
		ConditionOutcome[] secondHalf = secondHalfResolver.resolveOutcomes();
		ConditionOutcome[] firstHalf = firstHalfResolver.resolveOutcomes();
		// <4> 创建 outcomes 结果数组，然后合并结果，最后返回
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
		System.arraycopy(firstHalf, 0, outcomes, 0, firstHalf.length);
		System.arraycopy(secondHalf, 0, outcomes, split, secondHalf.length);
		return outcomes;
	}

	private OutcomesResolver createOutcomesResolver(String[] autoConfigurationClasses, int start, int end,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		OutcomesResolver outcomesResolver = new StandardOutcomesResolver(autoConfigurationClasses, start, end,
				autoConfigurationMetadata, this.beanClassLoader);
		try {
			return new ThreadedOutcomesResolver(outcomesResolver);
		}
		catch (AccessControlException ex) {
			return outcomesResolver;
		}
	}

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		// <1> 声明变量
		ClassLoader classLoader = context.getClassLoader();
		ConditionMessage matchMessage = ConditionMessage.empty();
		// <2> 获得 `@ConditionalOnClass` 注解的属性
		List<String> onClasses = getCandidates(metadata, ConditionalOnClass.class);
		if (onClasses != null) {
			// 执行匹配，看看是否有缺失的
			List<String> missing = getMatches(onClasses, MatchType.MISSING, classLoader);
			// 如果有不匹配的，返回不匹配信息
			if (!missing.isEmpty()) {
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
						.didNotFind("required class", "required classes").items(Style.QUOTE, missing));
			}
			// 如果匹配，添加到 matchMessage 中
			matchMessage = matchMessage.andCondition(ConditionalOnClass.class)
					.found("required class", "required classes")
					.items(Style.QUOTE, getMatches(onClasses, MatchType.PRESENT, classLoader));
		}
		// <3> 获得 `@ConditionalOnMissingClass` 注解的属性
		List<String> onMissingClasses = getCandidates(metadata, ConditionalOnMissingClass.class);
		if (onMissingClasses != null) {
			// 执行匹配，看看是有多余的
			List<String> present = getMatches(onMissingClasses, MatchType.PRESENT, classLoader);
			// 如果有不匹配的，返回不匹配信息
			if (!present.isEmpty()) {
				return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnMissingClass.class)
						.found("unwanted class", "unwanted classes").items(Style.QUOTE, present));
			}
			// 如果匹配，添加到 matchMessage 中
			matchMessage = matchMessage.andCondition(ConditionalOnMissingClass.class)
					.didNotFind("unwanted class", "unwanted classes")
					.items(Style.QUOTE, getMatches(onMissingClasses, MatchType.MISSING, classLoader));
		}
		// <4> 返回匹配的结果
		return ConditionOutcome.match(matchMessage);
	}

	private List<String> getCandidates(AnnotatedTypeMetadata metadata, Class<?> annotationType) {
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(annotationType.getName(), true);
		if (attributes == null) {
			return Collections.emptyList();
		}
		List<String> candidates = new ArrayList<>();
		addAll(candidates, attributes.get("value"));
		addAll(candidates, attributes.get("name"));
		return candidates;
	}

	private void addAll(List<String> list, List<Object> itemsToAdd) {
		if (itemsToAdd != null) {
			for (Object item : itemsToAdd) {
				Collections.addAll(list, (String[]) item);
			}
		}
	}

	private List<String> getMatches(Collection<String> candidates, MatchType matchType, ClassLoader classLoader) {
		List<String> matches = new ArrayList<>(candidates.size());
		for (String candidate : candidates) {
			if (matchType.matches(candidate, classLoader)) {
				matches.add(candidate);
			}
		}
		return matches;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	private enum MatchType {

		PRESENT {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				return isPresent(className, classLoader);
			}

		},

		MISSING {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				return !isPresent(className, classLoader);
			}

		};

		private static boolean isPresent(String className, ClassLoader classLoader) {
			if (classLoader == null) {
				classLoader = ClassUtils.getDefaultClassLoader();
			}
			try {
				forName(className, classLoader);
				return true;
			}
			catch (Throwable ex) {
				return false;
			}
		}

		private static Class<?> forName(String className, ClassLoader classLoader) throws ClassNotFoundException {
			if (classLoader != null) {
				return classLoader.loadClass(className);
			}
			return Class.forName(className);
		}

		public abstract boolean matches(String className, ClassLoader classLoader);

	}
	// 内部接口
	private interface OutcomesResolver {
		//执行解析
		ConditionOutcome[] resolveOutcomes();

	}

	private static final class ThreadedOutcomesResolver implements OutcomesResolver {
		//新起的线程
		private final Thread thread;
		// 条件匹配结果
		private volatile ConditionOutcome[] outcomes;

		private ThreadedOutcomesResolver(OutcomesResolver outcomesResolver) {
			//<1.1> 创建线程
			this.thread = new Thread(() -> this.outcomes = outcomesResolver.resolveOutcomes());
			// <1.2> 启动线程
			this.thread.start();
		}

		@Override
		public ConditionOutcome[] resolveOutcomes() {
			// <2.1> 等待线程执行结束
			try {
				this.thread.join();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			// <2.2> 返回结果
			return this.outcomes;
		}

	}

	private final class StandardOutcomesResolver implements OutcomesResolver {
		// 所有的配置类的数组
		private final String[] autoConfigurationClasses;
		//匹配的 {@link #autoConfigurationClasses} 开始位置
		private final int start;
		//匹配的 {@link #autoConfigurationClasses} 结束位置
		private final int end;

		private final AutoConfigurationMetadata autoConfigurationMetadata;

		private final ClassLoader beanClassLoader;

		private StandardOutcomesResolver(String[] autoConfigurationClasses, int start, int end,
				AutoConfigurationMetadata autoConfigurationMetadata, ClassLoader beanClassLoader) {
			this.autoConfigurationClasses = autoConfigurationClasses;
			this.start = start;
			this.end = end;
			this.autoConfigurationMetadata = autoConfigurationMetadata;
			this.beanClassLoader = beanClassLoader;
		}

		@Override
		public ConditionOutcome[] resolveOutcomes() {
			return getOutcomes(this.autoConfigurationClasses, this.start, this.end, this.autoConfigurationMetadata);
		}

		private ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses, int start, int end,
				AutoConfigurationMetadata autoConfigurationMetadata) {
			// 创建 ConditionOutcome 结构数组
			ConditionOutcome[] outcomes = new ConditionOutcome[end - start];
			for (int i = start; i < end; i++) {
				// 遍历 autoConfigurationClasses 数组，从 start 到 end
				String autoConfigurationClass = autoConfigurationClasses[i];
				// <1> 获得指定自动配置类的 @ConditionalOnClass 注解的要求类
				Set<String> candidates = autoConfigurationMetadata.getSet(autoConfigurationClass, "ConditionalOnClass");
				if (candidates != null) {
					// 执行匹配
					outcomes[i - start] = getOutcome(candidates);
				}
			}
			return outcomes;
		}

		private ConditionOutcome getOutcome(Set<String> candidates) {
			try {
				// 如果没有 , ，说明只有一个，直接匹配即可
				List<String> missing = getMatches(candidates, MatchType.MISSING, this.beanClassLoader);
				if (!missing.isEmpty()) {
					return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
							.didNotFind("required class", "required classes").items(Style.QUOTE, missing));
				}
			}
			catch (Exception ex) {
				// We'll get another chance later
			}
			return null;
		}

	}

}
