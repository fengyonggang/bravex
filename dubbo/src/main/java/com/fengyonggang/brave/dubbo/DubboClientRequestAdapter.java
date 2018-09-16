/**
 * 
 */
package com.fengyonggang.brave.dubbo;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import com.twitter.zipkin.gen.Endpoint;

/**
 * @author fengyonggang
 *
 */
public class DubboClientRequestAdapter implements ClientRequestAdapter{

	private Invocation invocation;
	
	public DubboClientRequestAdapter(Invoker<?> invoker, Invocation invocation) {
		this.invocation = invocation;
	}
	
	@Override
	public String getSpanName() {
		return "dubbo-consumer";
	}

	@Override
	public void addSpanIdToRequest(SpanId spanId) {
		if(this.invocation instanceof RpcInvocation) {
			RpcInvocation rpcInvocation = (RpcInvocation) this.invocation;
			Map<String, String> attachments = new HashMap<>();
			if (spanId == null) {
				attachments.put(BraveHttpHeaders.Sampled.getName(), "0");
	        } else {
	        	attachments.put(BraveHttpHeaders.Sampled.getName(), "1");
	        	attachments.put(BraveHttpHeaders.TraceId.getName(), spanId.traceIdString());
	        	attachments.put(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(spanId.spanId));
	            if (spanId.nullableParentId() != null) {
	            	attachments.put(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(spanId.parentId));
	            }
	        }
			rpcInvocation.addAttachmentsIfAbsent(attachments);
		}
    }

	@Override
	public Collection<KeyValueAnnotation> requestAnnotations() {
		return Collections.emptyList();
	}

	@Override
	public Endpoint serverAddress() {
		return null;
	}
	
}
