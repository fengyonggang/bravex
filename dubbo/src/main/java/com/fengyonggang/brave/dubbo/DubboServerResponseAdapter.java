/**
 * 
 */
package com.fengyonggang.brave.dubbo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.ServerResponseAdapter;

/**
 * @author fengyonggang
 *
 */
public class DubboServerResponseAdapter implements ServerResponseAdapter {

	private Result result;
	private Throwable exception;

	public DubboServerResponseAdapter(Result result) {
		this.result = result;
	}

	public DubboServerResponseAdapter(Throwable e) {
		this.exception = e;
	}

	@Override
	public Collection<KeyValueAnnotation> responseAnnotations() {
		List<KeyValueAnnotation> annotations = new ArrayList<>();
		if (result != null) {
			if (result.getAttachment(Constants.OUTPUT_KEY) != null) {
				annotations.add(KeyValueAnnotation.create("output", result.getAttachment(Constants.OUTPUT_KEY)));
			}
			if (result.hasException()) {
				exception = result.getException();
			}
		}

		if (exception != null) {
			annotations.add(KeyValueAnnotation.create("error.message", exception.getMessage()));
			if (exception instanceof RpcException) {
				RpcException rpcException = (RpcException) exception;
				List<String> errorTypes = new ArrayList<>();
				if (rpcException.isBiz())
					errorTypes.add("biz");
				if (rpcException.isForbidded())
					errorTypes.add("forbidded");
				if (rpcException.isNetwork())
					errorTypes.add("network");
				if (rpcException.isSerialization())
					errorTypes.add("serialization");
				if (rpcException.isTimeout())
					errorTypes.add("timeout");
				annotations.add(KeyValueAnnotation.create("error.type", StringUtils.join(errorTypes, ",")));
			}
		}
		return annotations;
	}

}
