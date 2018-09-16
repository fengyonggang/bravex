/**
 * 
 */
package com.fengyonggang.brave.tomcat.jdbc;

import java.io.Closeable;
import java.io.IOException;

import com.github.kristofa.brave.ClientTracer;

/**
 * @author fengyonggang
 *
 */
public class BraveTomcatJdbcInterceptorManagementBean implements Closeable {

	public BraveTomcatJdbcInterceptorManagementBean(final ClientTracer clientTracer) {
		BraveTomcatJdbcInterceptor.setClientTracer(clientTracer);
	}
	
	@Override
	public void close() throws IOException {
		BraveTomcatJdbcInterceptor.setClientTracer(null);
	}
}
