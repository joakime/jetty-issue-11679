import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Function;

import jakarta.inject.Singleton;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.security.ConstraintMapping;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.CrossOriginHandler;
import org.eclipse.jetty.server.handler.QoSHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;

import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

public class JettyApp {

    static
    {
        org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
        org.slf4j.bridge.SLF4JBridgeHandler.install();
    }

    private final Server server;

    JettyApp(int port) {
        Server server = createServer(port);

        Handler handler = createJerseyHandler();
        handler = createQosHandler(handler);
        handler = createCorsHandler(handler);
        handler = new StatisticsHandler(handler);
        handler = new CustomTracingHandler(handler);
        handler = createGzipHandler(handler);
        server.setHandler(handler);

        this.server = server;
    }

    private Server createServer(int port) {
        HttpConfiguration config = createHttpConfiguration();
        HttpConnectionFactory http1 = new HttpConnectionFactory(config);
        HTTP2CServerConnectionFactory http2c = new HTTP2CServerConnectionFactory(config);
        http2c.setMaxConcurrentStreams(1024);

        var pool = new QueuedThreadPool();
        pool.setDetailedDump(true);
        pool.setVirtualThreadsExecutor(Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("jetty-", 0).factory()));

        Server server = new Server(pool);
        int acceptors = 1;
        int selectors = 1;
        ServerConnector connector = new ServerConnector(server, null, null, null, acceptors, selectors, http1, http2c);
        connector.setIdleTimeout(3000);
        connector.setPort(port);
        server.addConnector(connector);

        return server;
    }

    private GzipHandler createGzipHandler(Handler handler) {
        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setIncludedPaths("/*");
        gzipHandler.setHandler(handler);
        return gzipHandler;
    }

    private CrossOriginHandler createCorsHandler(Handler handler) {
        CrossOriginHandler corsHandler = new CrossOriginHandler();
        corsHandler.setAllowCredentials(true);
        corsHandler.setAllowedOriginPatterns(Set.of("*"));
        corsHandler.setAllowedMethods(Set.of("POST,PUT,GET,OPTIONS,DELETE"));
        corsHandler.setAllowedHeaders(Set.of("origin, content-type, accept, authorization"));
        corsHandler.setHandler(handler);
        return corsHandler;
    }

    private QoSHandler createQosHandler(Handler handler) {
        QoSHandler qoSHandler = new QoSHandler(handler);
        qoSHandler.setMaxRequestCount(Runtime.getRuntime().availableProcessors() * 1_000);
        qoSHandler.setMaxSuspend(Duration.ofSeconds(10));
        return qoSHandler;
    }

    private ServletContextHandler createJerseyHandler() {
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        ResourceConfig resourceConfig = new ResourceConfig()
            .register(new JacksonProvider())
            .register(new TestResource())
            .register(new CatchAllExceptionMapper())
            .addProperties(Map.of(
                ServerProperties.MEDIA_TYPE_MAPPINGS, "json:application/json,html:text/html",
                ServerProperties.TRACING, "OFF",
                ServerProperties.WADL_FEATURE_DISABLE, "true"
            ));

        servletContextHandler.addServlet(new ServletHolder(new ServletContainer(resourceConfig)), "/*");
        servletContextHandler.setContextPath("/api");

        disableTraceMethodForHandler(servletContextHandler);

        return servletContextHandler;
    }

    private static HttpConfiguration createHttpConfiguration() {
        HttpConfiguration config = new HttpConfiguration();
        config.setSendXPoweredBy(false);
        config.setSendServerVersion(false);
        return config;
    }

    private static void disableTraceMethodForHandler(ServletContextHandler servletContextHandler) {
        SecurityHandler securityHandler = servletContextHandler.getSecurityHandler();
        if (securityHandler == null) {
            securityHandler = new ConstraintSecurityHandler();
            servletContextHandler.setSecurityHandler(securityHandler);
        }
        if (securityHandler instanceof ConstraintSecurityHandler constraintSecurityHandler) {
            Function<String, ConstraintMapping> disableMappingForMethod = (String method) -> {
                ConstraintMapping mapping = new ConstraintMapping();
                Constraint constraint = new Constraint.Builder()
                    .name("Disable " + method)
                    .authorization(Constraint.Authorization.FORBIDDEN)
                    .build();
                mapping.setConstraint(constraint);
                mapping.setPathSpec("/");
                mapping.setMethod(method);

                return mapping;
            };
            constraintSecurityHandler.addConstraintMapping(disableMappingForMethod.apply("TRACE"));
            constraintSecurityHandler.addConstraintMapping(disableMappingForMethod.apply("TRACK"));

            ConstraintMapping enableEverythingButTraceAndTrackMapping = new ConstraintMapping();
            Constraint enableEverythingButTraceConstraint = new Constraint.Builder()
                .name("Enable everything but TRACE and TRACK")
                .build();
            enableEverythingButTraceAndTrackMapping.setConstraint(enableEverythingButTraceConstraint);
            enableEverythingButTraceAndTrackMapping.setMethodOmissions(new String[]{"TRACE", "TRACK"});
            enableEverythingButTraceAndTrackMapping.setPathSpec("/");

            constraintSecurityHandler.addConstraintMapping(enableEverythingButTraceAndTrackMapping);
        }
    }

    public static class CatchAllExceptionMapper implements ExceptionMapper<Exception> {
        @Override
        public Response toResponse(Exception ex) {
            return Response.status(SERVICE_UNAVAILABLE)
                .entity("service unavailable")
                .type(MediaType.TEXT_PLAIN).build();

        }
    }

    Server getServer() {
        return server;
    }

    @Singleton
    @Path("/hello")
    @Produces({MediaType.APPLICATION_JSON})
    public static class TestResource {

        @POST
        public void world(BodyData bodyData, @Suspended AsyncResponse asyncResponse) {
            asyncResponse.resume(bodyData);
        }

    }

    public record BodyData(String body) {
    }


    public static void main(String[] args) throws Exception {
        var jetty = new JettyApp(8080);
        jetty.getServer().start();
        jetty.getServer().join(); // wait till server ends, or CTRL-C
    }
}