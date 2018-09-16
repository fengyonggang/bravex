/**
 * 
 */
package com.fengyonggang.brave.dubbo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcResult;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;

/**
 * @author fengyonggang
 *
 */
@Activate(group = Constants.CONSUMER, order = Integer.MIN_VALUE)
public class BraveDubboConsumerFilter implements Filter {

	private ClientRequestInterceptor clientRequestInterceptor;
	private ClientResponseInterceptor clientResponseInterceptor;

	public void setClientRequestInterceptor(ClientRequestInterceptor clientRequestInterceptor) {
		this.clientRequestInterceptor = clientRequestInterceptor;
	}

	public void setClientResponseInterceptor(ClientResponseInterceptor clientResponseInterceptor) {
		this.clientResponseInterceptor = clientResponseInterceptor;
	}

	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
		if (null == clientRequestInterceptor) {
			return new RpcResult();
		}
		clientRequestInterceptor.handle(new DubboClientRequestAdapter(invoker, invocation));
		try {
			Result result = invoker.invoke(invocation);
			clientResponseInterceptor.handle(new DubboClientResponseAdapter(result));
			return result;
		} catch (Exception e) {
			clientResponseInterceptor.handle(new DubboClientResponseAdapter(e));
			throw e;
		}
	}

}
