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
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseInterceptor;

/**
 * @author fengyonggang
 *
 */
@Activate(group = Constants.PROVIDER, order = Integer.MIN_VALUE)
public class BraveDubboProviderFilter implements Filter {
	
	private ServerRequestInterceptor serverRequestInterceptor;
    private ServerResponseInterceptor serverResponseInterceptor;
    
	public void setServerRequestInterceptor(ServerRequestInterceptor serverRequestInterceptor) {
		this.serverRequestInterceptor = serverRequestInterceptor;
	}

	public void setServerResponseInterceptor(ServerResponseInterceptor serverResponseInterceptor) {
		this.serverResponseInterceptor = serverResponseInterceptor;
	}

	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
		if (null == serverRequestInterceptor) {
			return new RpcResult();
		}
		serverRequestInterceptor.handle(new DubboServerRequestAdapter(invoker, invocation));
		try {
			Result result = invoker.invoke(invocation);
			serverResponseInterceptor.handle(new DubboServerResponseAdapter(result));
			return result;
		} catch(Exception e) {
			serverResponseInterceptor.handle(new DubboServerResponseAdapter(e));
			throw e;
		}
	}
}
