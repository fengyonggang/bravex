/**
 * 
 */
package com.fengyonggang.brave;

import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.util.NumberUtils;

import com.fengyonggang.brave.druid.BraveDruidFilter;
import com.fengyonggang.brave.tomcat.jdbc.BraveTomcatJdbcInterceptorManagementBean;
import com.github.kristofa.brave.AnnotationSubmitter;
import com.github.kristofa.brave.BoundarySampler;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.KeyValueAnnotation;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.http.HttpRequest;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.spring.ServletHandlerInterceptor;

import zipkin.Span;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Reporter;
import zipkin.reporter.Sender;
import zipkin.reporter.okhttp3.OkHttpSender;

/**
 * @author fengyonggang
 *
 */
@Configuration
@PropertySource("classpath:trace.properties")
public class BraveConfig {


	@Autowired
	private Environment env;

	@Bean
	public Sender sender() {
		String endpoint = env.getProperty("brave.sender.endpoint.url");
		return OkHttpSender.create(endpoint);
	}

	@Bean
	public Reporter<Span> reporter() {
		return AsyncReporter.builder(sender()).build();
	}

	@Bean
	public Brave brave() throws UnknownHostException {
		String serviceName = env.getProperty("brave.service-name");
		float sampleRate = NumberUtils.parseNumber(env.getProperty("brave.sample-rate", "1.0"), Float.class);
		return new Brave.Builder(serviceName)
				.traceSampler(sampleRate < 0.01 ? BoundarySampler.create(sampleRate) : Sampler.create(sampleRate))
				.reporter(reporter()).build();
	}
	
	
	@Bean
    public ClientTracer clientTracer(Brave brave) {
        return brave.clientTracer();
    }

    @Bean
    public ServerTracer serverTracer(Brave brave) {
        return brave.serverTracer();
    }

    @Bean
    public ClientRequestInterceptor clientRequestInterceptor(Brave brave) {
        return brave.clientRequestInterceptor();
    }

    @Bean
    public ClientResponseInterceptor clientResponseInterceptor(Brave brave) {
        return brave.clientResponseInterceptor();
    }

    @Bean
    public ServerRequestInterceptor serverRequestInterceptor(Brave brave) {
        return brave.serverRequestInterceptor();
    }

    @Bean
    public ServerResponseInterceptor serverResponseInterceptor(Brave brave) {
        return brave.serverResponseInterceptor();
    }

    @Bean(name = "serverSpanAnnotationSubmitter")
    public AnnotationSubmitter serverSpanAnnotationSubmitter(Brave brave) {
       return brave.serverSpanAnnotationSubmitter();
    }

    @Bean
    public ServerSpanThreadBinder serverSpanThreadBinder(Brave brave) {
        return brave.serverSpanThreadBinder();
    }

	/**
	 * http拦截器，需要添加下面的配置
	 *  <mvc:interceptors>
	 * 		<ref bean="braveHttpInterceptor"/>
	 *	</mvc:interceptors>
	 * @param brave
	 * @return
	 */
	@Bean
	public ServletHandlerInterceptor braveHttpInterceptor(Brave brave) {
		System.out.println("==========ServletHandlerInterceptor==========");
		return new ServletHandlerInterceptor(brave.serverRequestInterceptor(), brave.serverResponseInterceptor(),
				httpSpanNameProvider(), brave.serverSpanThreadBinder());
	}

	@Bean
	public SpanNameProvider httpSpanNameProvider() {
		return new SpanNameProvider() {
			@Override
			public String spanName(HttpRequest request) {
				return "http-" + request.getHttpMethod();
			}
		};
	}
	
	/**
	 * druid数据源拦截器
	 * <bean id="defaultDataSource" class="com.alibaba.druid.pool.DruidDataSource" destroy-method="close">
	 * 		......
	 * 		<property name="proxyFilters">
	 *			<list>
	 *				<ref bean="braveDruidFiler"/>
	 *			</list>
	 *		</property>  
	 * </bean>
	 * @param brave
	 * @return
	 */
	@Bean
    public BraveDruidFilter braveDruidFiler(Brave brave) {
        return new BraveDruidFilter(brave.clientTracer());
    }
	
	/**
	 * tomcat dbcp数据源拦截器
	 * 	<bean id="dataSource" class="org.apache.tomcat.jdbc.pool.DataSource">
	 * 		......
	 * 		<property name="jdbc-interceptors">
	 * 			<value>com.fengyonggang.brave.tomcat.jdbc.BraveTomcatJdbcInterceptor</value>
	 * 		</property>
	 * 	</bean>
	 * @param brave
	 * @return
	 */
	@Bean
    public BraveTomcatJdbcInterceptorManagementBean braveTomcatJdbcInterceptorManagementBean(Brave brave) {
        return new BraveTomcatJdbcInterceptorManagementBean(brave.clientTracer());
    }

	/**
	 * servlet 2.5中,  HttpServletResponse没有getStatus()方法, 在此覆盖ServletHandlerInterceptor。
	 * servlet 3.0版本，建议直接使用ServletHandlerInterceptor
	 * @author fengyonggang
	 *
	 */
	static class Servlet25HandlerInterceptor extends ServletHandlerInterceptor {

		static final String HTTP_SERVER_SPAN_ATTRIBUTE = ServletHandlerInterceptor.class.getName() + ".server-span";
		
		private final ServerResponseInterceptor responseInterceptor;
		private final ServerSpanThreadBinder serverThreadBinder;

		public Servlet25HandlerInterceptor(ServerRequestInterceptor requestInterceptor,
				ServerResponseInterceptor responseInterceptor, SpanNameProvider spanNameProvider,
				ServerSpanThreadBinder serverThreadBinder) {
			super(requestInterceptor, responseInterceptor, spanNameProvider, serverThreadBinder);
			this.responseInterceptor = responseInterceptor;
			this.serverThreadBinder = serverThreadBinder;
		}
		
		@Override
		public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
				Exception ex) {

			final ServerSpan span = (ServerSpan) request.getAttribute(HTTP_SERVER_SPAN_ATTRIBUTE);

			if (span != null) {
				serverThreadBinder.setCurrentSpan(span);
			}

			responseInterceptor.handle(new ServerResponseAdapter() {
				@Override
				public Collection<KeyValueAnnotation> responseAnnotations() {
					return Collections.emptyList();
				}
			});
		}
	}
}
