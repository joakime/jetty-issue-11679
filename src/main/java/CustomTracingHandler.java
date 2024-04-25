import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomTracingHandler extends Handler.Wrapper {
    private final static Logger log = LoggerFactory.getLogger(CustomTracingHandler.class);

    public CustomTracingHandler(Handler handler) {
        super(handler);
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) {
        try {
            long startTime = System.currentTimeMillis();
            Request.addCompletionListener(request, e -> {
                log.info("Request completed in {}ms", System.currentTimeMillis() - startTime);
            });
            return super.handle(request, response, callback);
        } catch (Exception e) {
            log.error("Opps", e);
            Response.writeError(request, response, callback, HttpStatus.INTERNAL_SERVER_ERROR_500);
            return true;
        }
    }
}
