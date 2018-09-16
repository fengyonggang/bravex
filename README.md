bravex
============

`Zipkin`是Twitter公司开源的一套分布式跟踪系统，官网： http://zipkin.io  
`Brave`是`zipkin`官方提供的一个Java实现。它本身提供了很多针对现在常见的框架或者协议的集成，包括：http, servlet, rest, spring, grpc, mysql等。  
`bravex`是对`Brave`的一个补充，目前提供了对dubbo、tomcat-jdbc、druid的集成。  

## Usage

非spring-boot项目请参考[这里](trace/README.md)

### Brave配置

```java
 @Configuration
 @Import(BraveApiConfig.class)
 public class BraveConfig {
 
 	@Value("${brave.sender.endpoint.url:http://localhost:9411/api/v1/spans}")
 	private String endpoint;
 	@Value("${brave.service-name:}")
 	private String serviceName;
 	@Value("${brave.sample-rate:1.0}")
 	private float sampleRate;  //采样率，1： 100%， 0：不采样（即不发送数据）
 	
 	@Bean
 	public Sender sender() {
 		return OkHttpSender.create(endpoint);
 	}
 
 	@Bean
 	public Reporter<Span> reporter() {
 		return AsyncReporter.builder(sender()).build();
 	}
 
 	@Bean
 	public Brave brave() {
 		return new Brave.Builder(serviceName)
 				.traceSampler(sampleRate < 0.01 ? BoundarySampler.create(sampleRate) : Sampler.create(sampleRate))
 				.reporter(reporter()).build();
 	}
 	
 }
```

### 拦截Http请求

1) 通过Http Filter的方式拦截

```java

    @Bean
 	public BraveServletFilter braveServletFilter() {
 		return new BraveServletFilter(requestInterceptor, responseInterceptor, httpSpanNameProvider());
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
```

2) 通过spring mvc 拦截器拦截

```java
@Configuration
 public class MvcConfig extends WebMvcConfigurerAdapter {
 
 	@Autowired
 	private ServerRequestInterceptor requestInterceptor;
 	@Autowired
 	private ServerResponseInterceptor responseInterceptor;
 	@Autowired
 	private ServerSpanThreadBinder serverThreadBinder;
 
 	@Override
 	public void addInterceptors(InterceptorRegistry registry) {
 		registry.addInterceptor(new ServletHandlerInterceptor(requestInterceptor, responseInterceptor, httpSpanNameProvider(),
 				serverThreadBinder));
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
 }
```

3) 基于RestTemplate拦截Rest请求

```java
@Configuration
public class RestConfig {

	@Autowired
	private ClientRequestInterceptor requestInterceptor;
	@Autowired
	private ClientResponseInterceptor responseInterceptor;
	
	@Bean
	public RestTemplate restTemplate() {
		RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory());
		List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
		interceptors.add(new BraveClientHttpRequestInterceptor(requestInterceptor, responseInterceptor, restTemplateSpanNameProvider()));
		restTemplate.setInterceptors(interceptors);
		return restTemplate;
	}
	
	@Bean
	public SpanNameProvider restTemplateSpanNameProvider() {
		return new SpanNameProvider() {
			@Override
			public String spanName(HttpRequest request) {
				return "rest-" + request.getHttpMethod();
			}
		};
	}

	private ClientHttpRequestFactory clientHttpRequestFactory() {
		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
		factory.setHttpClient(createHttpClient());
		factory.setConnectTimeout(5000);
		factory.setReadTimeout(5000);
		factory.setConnectionRequestTimeout(200);
//		factory.setBufferRequestBody(true);
		return factory;
	}

	private HttpClient createHttpClient() {
		// set timeToLive to 30 seconds
		PoolingHttpClientConnectionManager pollingConnectionManager = new PoolingHttpClientConnectionManager(30, TimeUnit.SECONDS);
		// max connection size in pool
		pollingConnectionManager.setMaxTotal(500);
		pollingConnectionManager.setDefaultMaxPerRoute(500);
		
		HttpClientBuilder httpClientBuilder = HttpClients.custom();	
		httpClientBuilder.setConnectionManager(pollingConnectionManager);
		// set retry count to 2 
		httpClientBuilder.setRetryHandler(new DefaultHttpRequestRetryHandler(2, true));
		// add Keep-Alive
		httpClientBuilder.setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE);
		return httpClientBuilder.build();	
	}
}
```

### 集成dubbo，拦截dubbo调用
1) 添加以下依赖：

```xml
<dependency>
    <groupId>com.fengyonggang</groupId>
    <artifactId>brave-dubbo</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

2) 开启dubbo过滤器。 服务消费者：

```xml
<dubbo:consumer filter="braveConsumerFilter"/>
```

服务提供者： 

```xml
<dubbo:provider filter="braveProviderFilter"/>
```

### 集成tomcat-jdbc，拦截tomcat dbcp数据源

1) 添加以下依赖：

```xml
<dependency>
    <groupId>com.fengyonggang</groupId>
    <artifactId>brave-tomcat-jdbc</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

2) tomcat-jdbc链接池配置，并添加jdbc拦截器：

```yml
spring: 
  profiles:
    active: ${PROFILE:develop}
  datasource:
    driver-class-name: com.mysql.jdbc.Driver    
    url: ${MYSQL_URL}
    username: ${MYSQL_USER} 
    password: ${MYSQL_PASS}
    type: org.apache.tomcat.jdbc.pool.DataSource
    tomcat: 
      test-while-idle: true
      time-between-eviction-runs-millis: 30000 
      min-evictable-idle-time-millis: 30000
      validation-query: SELECT 1
      remove-abandoned: true
      jdbc-interceptors: com.fengyonggang.brave.tomcat.jdbc.BraveTomcatJdbcInterceptor
```

3) 配置BraveTomcatJdbcInterceptorManagementBean

```java
    @Bean
	public BraveTomcatJdbcInterceptorManagementBean braveTomcatJdbcInterceptorManagementBean() {
		return new BraveTomcatJdbcInterceptorManagementBean(clientTracer);
	}
```

### 集成druid，拦截druid数据源

1) 添加以下依赖：

```xml
<dependency>
    <groupId>com.fengyonggang</groupId>
    <artifactId>brave-druid</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

2) druid链接池配置：

```yml
spring: 
  profiles:
    active: ${PROFILE:develop}
  datasource:
    driver-class-name: oracle.jdbc.driver.OracleDriver
    url: ${ORACLE_URL}
    username: ${ORACLE_USER} 
    password: ${ORACLE_PASS}
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      initial-size: 1
      min-idle: 1
      max-active: 20
      max-wait: 60000
      time-between-eviction-runs-millis:  60000
      min-evictable-idle-time-millis: 300000
      validation-query: SELECT 1 from dual
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false
      pool-prepared-statements: true
      max-pool-prepared-statement-per-connection-size: 20
```

3) 配置DruidDataSource

```java
@Configuration
public class DruidDataSourceConfig {

	@Autowired
	private ClientTracer clientTracer;
	
	@Bean
	@ConfigurationProperties("spring.datasource.druid")
	public DruidDataSource dataSource(DataSourceProperties properties) {
		DruidDataSource dataSource = new DruidDataSource();
		dataSource.setDriverClassName(properties.getDriverClassName());
		dataSource.setUrl(properties.getUrl());
		dataSource.setUsername(properties.getUsername());
		dataSource.setPassword(properties.getPassword());
		dataSource.setProxyFilters(Arrays.asList(braveDruidFiler()));
		return dataSource;
	}
	
	@Bean
	public BraveDruidFilter braveDruidFiler() {
		return new BraveDruidFilter(clientTracer);
	}
}
```
