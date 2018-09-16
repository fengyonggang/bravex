trace-brave
============

此模块是用来集成非`spring-boot`项目。 

## Usage

1) 添加以下依赖：

```xml
<dependency>
    <groupId>com.fengyonggang</groupId>
    <artifactId>trace-brave</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

2) 在spring配置文件中添加以下代码:

```xml
<import resource="classpath*:applicationContext-trace.xml"/>
```

3) 在spring-mvc配置文件中添加以下代码来拦截http请求:

```xml
<import resource="classpath*:applicationContext-trace-mvc.xml"/>
```

4) 添加dubbo过滤器拦截dubbo请求:

```xml
<!--消费端过滤器-->
<dubbo:consumer filter="braveConsumerFilter"/>

<!--服务端过滤器-->
<dubbo:provider filter="braveProviderFilter"/>
```

5) 修改数据源配置，拦截数据库查询:

a. 配置Druid数据源

```xml
	<bean id="defaultDataSource" class="com.alibaba.druid.pool.DruidDataSource" destroy-method="close">
		......
		<property name="proxyFilters">
			<list>
				<ref bean="braveDruidFiler"/>
			</list>
		</property>  
	</bean>
```

b. 配置tomact dbcp数据源

```xml
	<bean id="dataSource" class="org.apache.tomcat.jdbc.pool.DataSource">
		......
		<property name="jdbc-interceptors">
			<value>com.fengyonggang.brave.tomcat.jdbc.BraveTomcatJdbcInterceptor</value>
		</property>
	</bean>
```

6) 添加trace.properties到classpath:

```
brave.sender.endpoint.url=http://localhost:9411/api/v1/spans
brave.service-name=your-service-name
brave.sample-rate=1.0
```
