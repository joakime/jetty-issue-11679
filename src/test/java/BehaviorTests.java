import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BehaviorTests
{
    static
    {
        org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
        org.slf4j.bridge.SLF4JBridgeHandler.install();
    }

    private static final Logger LOG = LoggerFactory.getLogger(BehaviorTests.class);
    Server server;
    URI serverURI;

    @BeforeEach
    public void startServer() throws Exception
    {
        JettyApp jettyApp = new JettyApp(0);
        server = jettyApp.getServer();
        server.start();
        serverURI = server.getURI().resolve("/");
    }

    @AfterEach
    public void stopServer()
    {
        LifeCycle.stop(server);
    }

    public enum Mode
    {
        FULL_WRITE,
        SLOW_WRITE,
        PARTIAL_WRITE_CLOSE_OUTPUT
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    public void normalSend(Mode mode) throws IOException, InterruptedException
    {
        final String body = """
            {
                "customerData":"1111",
                "punterId":"sb_punter1@unibet",
                "streamingAllowed":true,
                "ticket":"sb_punter1@unibet",
                "market":"GB",
                "channel": "WEB"
            }
            """;
        try (Socket socket = new Socket(serverURI.getHost(), serverURI.getPort());
             OutputStream outputStream = socket.getOutputStream())
        {
            byte[] rawbody = body.getBytes(StandardCharsets.UTF_8);
            String request = """
                POST /api/hello HTTP/1.1\r
                Content-Length: %d\r
                Content-Type: application/json\r
                Host: %s\r
                \r
                """.formatted(rawbody.length, serverURI.getAuthority());
            byte[] rawrequest = request.getBytes(StandardCharsets.UTF_8);

            outputStream.write(rawrequest);

            LOG.info("Mode: {}", mode);
            switch (mode)
            {
                case FULL_WRITE ->
                {
                    outputStream.write(rawbody); // works fine
                    outputStream.flush();
                }
                case SLOW_WRITE ->
                {
                    for (byte b : rawbody)
                    {
                        System.out.print(".");
                        outputStream.write(b);
                        outputStream.flush();
                        Thread.sleep(500);
                    }
                    System.out.println();
                }
                case PARTIAL_WRITE_CLOSE_OUTPUT ->
                {
                    LOG.info("Write half");
                    outputStream.write(rawbody, 0, rawbody.length / 2);
                    LOG.info("Write flush");
                    outputStream.flush();
                    LOG.info("Client shutdownOutput");
                    socket.shutdownOutput();
                    Thread.sleep(1000);
                }
            }

            HttpTester.Input input = HttpTester.from(socket.getInputStream());

            HttpTester.Response response = HttpTester.parseResponse(input);
            System.err.println(response);
            System.err.println(response.getContent());
        }
    }
}
