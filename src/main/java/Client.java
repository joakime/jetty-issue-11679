import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.util.BufferUtil;

import java.net.Socket;

public class Client {

    public static void main(String[] args) throws Exception {
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
        try (Socket socket = new Socket("localhost", 8080)) {
            var eoln = System.lineSeparator();
            var header = BufferUtil.toBuffer(
                "POST /api/hello HTTP/1.1" + eoln +
                "Content-Length: " + body.length() + eoln +
                "Content-Type: application/json" + eoln +
                "Host: localhost" + eoln);
            var bdy = BufferUtil.toBuffer(eoln + body);

            socket.getOutputStream().write(header.array());
//            socket.getOutputStream().write(bdy.array()); // works fine

            // causes earlyEOF
            for (byte b : bdy.array()) {
                socket.getOutputStream().write(b);
                socket.shutdownOutput();
                sleep(1000);
                break;
            }

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
