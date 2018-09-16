brave-dubbo
=================

`Zipkin` is a distributed tracing system. http://zipkin.io   
`Brave` is a Java distributed tracing implementation compatible with `Zipkin` back-end services. https://github.com/openzipkin/brave   
`Brave-dubbo` is a Brave module which used to integrate with `Dubbo`. 

The module contains 2 Dubbo filters: 
- `BraveDubboConsumerFilter` - Intercepts dubbo request at consumer end,  extracts trace information and sends `cs` and `cr` annotations. And forwards trace information over attachment of RpcInvocation.
- `BraveDubboProviderFilter` - Intercepts dubbo request at provider end, extracts trace information from attachment of RpcInvocation, sends `sr` and `ss` annotations. 

# Usage 

Please make sure your project have already been integrated with Brave, and had the appropriate configuration about Brave, then you need add the dependency: 

```
<dependency>
    <groupId>com.fengyonggang</groupId>
	<artifactId>brave-dubbo</artifactId>
	<version>1.0-SNAPSHOT</version>
</dependency>
```

To enable the dubbo filter, please add the code in your dubbo xml configuration.
For consumer: 

```
<dubbo:consumer filter="braveConsumerFilter"/>
```

For provider: 

```
<dubbo:provider filter="braveProviderFilter"/>
```