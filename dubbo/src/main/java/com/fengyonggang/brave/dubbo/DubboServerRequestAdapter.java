/**
 * 
 */
package com.fengyonggang.brave.dubbo;

import static com.github.kristofa.brave.IdConversion.convertToLong;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.support.RpcUtils;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import com.github.kristofa.brave.http.BraveHttpHeaders;

/**
 * @author fengyonggang
 *
 */
public class DubboServerRequestAdapter implements ServerRequestAdapter {

	private static final TraceData EMPTY_UNSAMPLED_TRACE = TraceData.builder().sample(false).build();
	private static final TraceData EMPTY_MAYBE_TRACE = TraceData.builder().build();
	private static final Random random = new Random();

	private final String application;
	private final String service;
	private final String method;
	private final Invocation invocation;

	public DubboServerRequestAdapter(Invoker<?> invoker, Invocation invocation) {
		this.application = invoker.getUrl().getParameter(Constants.APPLICATION_KEY);
		this.service = invoker.getInterface().getName(); // 获取服务名称
		this.method = RpcUtils.getMethodName(invocation); // 获取方法名
		this.invocation = invocation;
	}

	@Override
	public TraceData getTraceData() {
		final String sampled = invocation.getAttachment(BraveHttpHeaders.Sampled.getName());
		if (sampled != null) {
			if (sampled.equals("0") || sampled.equalsIgnoreCase("false")) {
				return EMPTY_UNSAMPLED_TRACE;
			} else {
				final String traceId = invocation.getAttachment(BraveHttpHeaders.TraceId.getName());
				final String spanId = invocation.getAttachment(BraveHttpHeaders.SpanId.getName());

				if (traceId != null && spanId != null) {
					SpanId span = getSpanId(traceId, spanId);
					return TraceData.builder().sample(true).spanId(span).build();
				}
			}
		}
		return EMPTY_MAYBE_TRACE;
	}

	@Override
	public String getSpanName() {
		return "dubbo-provider";
	}

	@Override
	public Collection<KeyValueAnnotation> requestAnnotations() {
		return Arrays.asList(KeyValueAnnotation.create("application", application),
				KeyValueAnnotation.create("service", service),
				KeyValueAnnotation.create("method", method));
	}

	private SpanId getSpanId(String traceId, String spanId) {
		return SpanId.builder().traceIdHigh(traceId.length() == 32 ? convertToLong(traceId, 0) : 0)
				.traceId(convertToLong(traceId)).spanId(random.nextLong())
				.parentId(convertToLong(spanId)).build();
	}
	
	
}
