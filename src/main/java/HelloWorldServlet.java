import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.stream.Collectors;

public class HelloWorldServlet extends HttpServlet {
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            var body = request.getReader().lines().collect(Collectors.joining());
            response.setContentType("application/json");
            response.getWriter().append(body);
            response.setStatus(200);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
