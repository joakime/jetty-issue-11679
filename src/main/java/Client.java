import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpTester;

public class Client {
    public static void main(String[] args) throws Exception {
        org.slf4j.bridge.SLF4JBridgeHandler.removeHandlersForRootLogger();
        org.slf4j.bridge.SLF4JBridgeHandler.install();

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
        try (Socket socket = new Socket("localhost", 8080);
             OutputStream outputStream = socket.getOutputStream()) {
            byte[] rawbody = body.getBytes(StandardCharsets.UTF_8);
            String request = """
                POST /api/hello HTTP/1.1\r
                Content-Length: %d\r
                Content-Type: application/json\r
                Host: localhost\r
                \r
                """.formatted(rawbody.length);
            byte[] rawrequest = request.getBytes(StandardCharsets.UTF_8);

            outputStream.write(rawrequest);
            // -- Works fine
            // outputStream.write(rawbody); // works fine
            // outputStream.flush();

            // -- causes earlyEOF
            outputStream.write(rawbody, 0, rawbody.length / 2);
            outputStream.flush();
            socket.shutdownOutput();
            sleep(1000);

            // -- slow write
            /*
            for (byte b : rawbody) {
                outputStream.write(b);
                outputStream.flush();
                sleep(1000);
            }
            */

            HttpTester.Input input = HttpTester.from(socket.getInputStream());

            HttpTester.Response response = HttpTester.parseResponse(input);
            System.err.printf("%s %s %s%n", response.getVersion(), response.getStatus(), response.getReason());
            for (HttpField field : response) {
                System.err.printf("%s: %s%n", field.getName(), field.getValue());
            }
            System.err.printf("%n%s%n", response.getContent());
        }
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
