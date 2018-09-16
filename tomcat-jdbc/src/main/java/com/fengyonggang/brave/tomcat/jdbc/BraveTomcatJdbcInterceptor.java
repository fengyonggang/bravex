/**
 * 
 */
package com.fengyonggang.brave.tomcat.jdbc;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PooledConnection;

import com.github.kristofa.brave.ClientTracer;
import com.twitter.zipkin.gen.Endpoint;

import zipkin.TraceKeys;

/**
 * @author fengyonggang
 *
 */
public class BraveTomcatJdbcInterceptor extends JdbcInterceptor {

	private final static String SERVICE_NAME_KEY = "zipkinServiceName";

	private static final String PREPARE_STATEMENT = "prepareStatement";
	private static final String PREPARE_CALL = "prepareCall";
	private static final String[] STATEMENT_TYPES = { PREPARE_STATEMENT, PREPARE_CALL };

	private static final String MYSQL_DRIVER = "com.mysql.jdbc.Driver";
	private static final String ORACLE_DRIVER = "oracle.jdbc.driver.OracleDriver";

	static volatile ClientTracer clientTracer;
	private static Map<String, Endpoint> endpointMap = new ConcurrentHashMap<>();

	public static void setClientTracer(final ClientTracer tracer) {
		clientTracer = tracer;
	}

	private boolean isStatement(Method method) {
		return process(STATEMENT_TYPES, method);
	}
	
	private boolean isClose(Method method) {
		return compare(CLOSE_VAL, method);
	}

	private boolean process(String[] names, Method method) {
		final String methodName = method.getName();
		for (String name : names) {
			if (compare(name, methodName)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (isStatement(method) && args != null && args.length > 0) {
			SqlHolder.addSql(args[0].toString());
		}
		try {
			return super.invoke(proxy, method, args);
		} finally {
			if(isClose(method)) {
				ClientTracer clientTracer = BraveTomcatJdbcInterceptor.clientTracer;
				if (clientTracer != null) {
					endTrace(clientTracer);
				}
				SqlHolder.clear();
			}
		}
	}
	
	@Override
	public void reset(ConnectionPool parent, PooledConnection con) {
		ClientTracer clientTracer = BraveTomcatJdbcInterceptor.clientTracer;
		if (clientTracer != null) {
			beginTrace(clientTracer, con);
		}
	}
	
	private void beginTrace(final ClientTracer tracer, PooledConnection con) {
		Endpoint endpoint = null;
		PoolConfiguration config = con.getPoolProperties();
		try {
			if (compare(config.getDriverClassName(), MYSQL_DRIVER)) {
				endpoint = createMysqlEndpoint(config);
			} else if (compare(config.getDriverClassName(), ORACLE_DRIVER)) {
				endpoint = createOracleEndpoint(config);
			} else {
				// not support
			}
		} catch (Exception ignore) {
		}
		
		tracer.startNewSpan("query");
		if(endpoint != null) {
			tracer.setClientSent(endpoint);
		} else {
			tracer.setClientSent();
		}
	}

	private void endTrace(final ClientTracer tracer) {
		List<String> sqls = SqlHolder.getSql();
		if(sqls != null && sqls.size() > 0) {
			for(String sql : sqls) {
				tracer.submitBinaryAnnotation(TraceKeys.SQL_QUERY, sql);
			}
		}
		tracer.setClientReceived();
	}

	private Endpoint createOracleEndpoint(PoolConfiguration config) throws Exception {
		String subUrl = config.getUrl().split("@")[1]; // strip "jdbc:oracle:thin:@"
		String[] arr = subUrl.split(":");
		InetAddress address = Inet4Address.getByName(arr[0]);
		int ipv4 = ByteBuffer.wrap(address.getAddress()).getInt();
		int port = Integer.parseInt(arr[1]);
		String schema = arr[2];

		Properties props = config.getDbProperties();
		String serviceName = props.getProperty(SERVICE_NAME_KEY);
		if (serviceName == null || "".equals(serviceName)) {
			serviceName = "oracle-" + schema;
		}
		return createEndpoint(ipv4, port, serviceName);
	}

	private Endpoint createMysqlEndpoint(PoolConfiguration config) throws Exception {
		URI url = URI.create(config.getUrl().substring(5)); // strip "jdbc:"
		InetAddress address = Inet4Address.getByName(url.getHost());
		int ipv4 = ByteBuffer.wrap(address.getAddress()).getInt();
		int port = url.getPort() == -1 ? 3306 : url.getPort();

		Properties props = config.getDbProperties();
		String serviceName = props.getProperty(SERVICE_NAME_KEY);
		if (serviceName == null || "".equals(serviceName)) {
			serviceName = "mysql";
			String databaseName = url.getPath();
			if (databaseName != null) {
				if(databaseName.startsWith("/")) {
					databaseName = databaseName.substring(1);
				}
				if(!"".equals(databaseName)) {
					serviceName += "-" + databaseName;
				}
			}
		}
		return createEndpoint(ipv4, port, serviceName);
	}

	private Endpoint createEndpoint(int ipv4, int port, String serviceName) {
		String key = serviceName + "-" + ipv4 + "-" + port;
		if (endpointMap.containsKey(key)) {
			return endpointMap.get(key);
		}
		Endpoint endpoint = Endpoint.builder().ipv4(ipv4).port(port).serviceName(serviceName).build();
		endpointMap.put(key, endpoint);
		return endpoint;
	}
	
	static class SqlHolder {
		private static ThreadLocal<List<String>> holder = new ThreadLocal<>();

		public static void addSql(String sql) {
			List<String> sqls = holder.get();
			if (sqls == null) {
				sqls = new ArrayList<>();
			}
			sqls.add(sql);
			holder.set(sqls);
		}

		public static List<String> getSql() {
			return holder.get();
		}

		public static void clear() {
			holder.remove();
		}
	}
}
