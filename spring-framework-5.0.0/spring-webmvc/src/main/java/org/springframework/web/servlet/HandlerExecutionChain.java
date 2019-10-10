/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.servlet;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

/**
 * Handler execution chain, consisting of handler object and any handler interceptors.
 * Returned by HandlerMapping's {@link HandlerMapping#getHandler} method.
 *
 * @author Juergen Hoeller
 * @see HandlerInterceptor
 * @since 20.06.2003
 */
public class HandlerExecutionChain {

	private static final Log logger = LogFactory.getLog(HandlerExecutionChain.class);

	/**
	 * 处理器
	 */
	private final Object handler;

	@Nullable
	/**
	 * 拦截器
	 */
	private HandlerInterceptor[] interceptors;

	@Nullable
	private List<HandlerInterceptor> interceptorList;
	/**
	 * 已执行 {@link HandlerInterceptor#preHandle(HttpServletRequest, HttpServletResponse, Object)} 的位置
	 * <p>
	 * 主要用于实现 {@link #applyPostHandle(HttpServletRequest, HttpServletResponse, ModelAndView)} 的逻辑
	 */
	private int interceptorIndex = -1;


	/**
	 * Create a new HandlerExecutionChain.
	 *
	 * @param handler the handler object to execute
	 */
	public HandlerExecutionChain(Object handler) {
		this(handler, (HandlerInterceptor[]) null);
	}











	/**
	 * Return the handler object to execute.
	 */
	public Object getHandler() {
		return this.handler;
	}


	/**
	 * Create a new HandlerExecutionChain.
	 * @param handler      the handler object to execute
	 * @param interceptors the array of interceptors to apply
	 *                     (in the given order) before the handler itself executes
	 */
	public HandlerExecutionChain(Object handler, @Nullable HandlerInterceptor... interceptors) {
		if (handler instanceof HandlerExecutionChain) {
			HandlerExecutionChain originalChain = (HandlerExecutionChain) handler;
			// 初始化到 interceptorList 中
			this.handler = originalChain.getHandler();
			this.interceptorList = new ArrayList<>();
			CollectionUtils.mergeArrayIntoCollection(originalChain.getInterceptors(), this.interceptorList);// 逻辑比较简单，就是将前者添加到后者中，即添加到 interceptorList 中
			CollectionUtils.mergeArrayIntoCollection(interceptors, this.interceptorList); // 逻辑比较简单，就是将前者添加到后者中，即添加到 interceptorList 中
		} else {
			this.handler = handler;
			this.interceptors = interceptors;
		}
	}




	public void addInterceptor(HandlerInterceptor interceptor) {
		initInterceptorList().add(interceptor);
	}

	public void addInterceptors(HandlerInterceptor... interceptors) {
		if (!ObjectUtils.isEmpty(interceptors)) {
			CollectionUtils.mergeArrayIntoCollection(interceptors, initInterceptorList());
		}
	}

	private List<HandlerInterceptor> initInterceptorList() {
		// 如果 interceptorList 为空，则初始化为 ArrayList
		if (this.interceptorList == null) {
			this.interceptorList = new ArrayList<>();
			// 如果 interceptors 非空，则添加到 interceptorList 中
			if (this.interceptors != null) {
				// An interceptor array specified through the constructor
				CollectionUtils.mergeArrayIntoCollection(this.interceptors, this.interceptorList);
			}
		}
		// 置空 interceptors
		this.interceptors = null;
		// 返回 interceptorList
		return this.interceptorList;
	}

	/**
	 * Return the array of interceptors to apply (in the given order).
	 *
	 * @return the array of HandlerInterceptors instances (may be {@code null})
	 */
	@Nullable
	public HandlerInterceptor[] getInterceptors() {
		// 将 interceptorList 初始化到 interceptors 中
		if (this.interceptors == null && this.interceptorList != null) {
			this.interceptors = this.interceptorList.toArray(new HandlerInterceptor[0]);
		}
		// 返回 interceptors 数组
		return this.interceptors;
	}


	/**
	 * Apply preHandle methods of registered interceptors.
	 *
	 * @return {@code true} if the execution chain should proceed with the
	 * next interceptor or the handler itself. Else, DispatcherServlet assumes
	 * that this interceptor has already dealt with the response itself.
	 */
	boolean applyPreHandle(HttpServletRequest request, HttpServletResponse response) throws Exception {
		// <1> 获得拦截器数组
		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			// <2> 遍历拦截器数组
			for (int i = 0; i < interceptors.length; i++) {
				HandlerInterceptor interceptor = interceptors[i];
				// <3> 前置处理
				if (!interceptor.preHandle(request, response, this.handler)) {
					// <3.1> 触发已完成处理
					triggerAfterCompletion(request, response, null);
					// 返回 false ，前置处理失败
					return false;
				}
				// <3.2> 标记 interceptorIndex 位置
				this.interceptorIndex = i;
			}
		}
		// <4> 返回 true ，前置处理成功
		return true;
	}

	/**
	 * Apply postHandle methods of registered interceptors.
	 */
	void applyPostHandle(HttpServletRequest request, HttpServletResponse response, @Nullable ModelAndView mv)
			throws Exception {
		// 获得拦截器数组
		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			// 遍历拦截器数组
			for (int i = interceptors.length - 1; i >= 0; i--) {
				HandlerInterceptor interceptor = interceptors[i];
				// 后置处理
				interceptor.postHandle(request, response, this.handler, mv);
			}
		}
	}

	/**
	 * Trigger afterCompletion callbacks on the mapped HandlerInterceptors.
	 * Will just invoke afterCompletion for all interceptors whose preHandle invocation
	 * has successfully completed and returned true.
	 */
	void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response, @Nullable Exception ex)
			throws Exception {
		// 获得拦截器数组
		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			// 遍历拦截器数组
			for (int i = this.interceptorIndex; i >= 0; i--) {
				HandlerInterceptor interceptor = interceptors[i];
				try {
					// 已完成处理
					interceptor.afterCompletion(request, response, this.handler, ex);
				} catch (Throwable ex2) {
					// 注意，如果执行失败，仅仅会打印错误日志，不会结束循环
					logger.error("HandlerInterceptor.afterCompletion threw exception", ex2);
				}
			}
		}
	}

	/**
	 * Apply afterConcurrentHandlerStarted callback on mapped AsyncHandlerInterceptors.
	 */
	void applyAfterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response) {
		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			for (int i = interceptors.length - 1; i >= 0; i--) {
				if (interceptors[i] instanceof AsyncHandlerInterceptor) {
					try {
						AsyncHandlerInterceptor asyncInterceptor = (AsyncHandlerInterceptor) interceptors[i];
						asyncInterceptor.afterConcurrentHandlingStarted(request, response, this.handler);
					} catch (Throwable ex) {
						logger.error("Interceptor [" + interceptors[i] + "] failed in afterConcurrentHandlingStarted", ex);
					}
				}
			}
		}
	}


	/**
	 * Delegates to the handler's {@code toString()}.
	 */
	@Override
	public String toString() {
		Object handler = getHandler();
		StringBuilder sb = new StringBuilder();
		sb.append("HandlerExecutionChain with handler [").append(handler).append("]");
		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			sb.append(" and ").append(interceptors.length).append(" interceptor");
			if (interceptors.length > 1) {
				sb.append("s");
			}
		}
		return sb.toString();
	}

}
