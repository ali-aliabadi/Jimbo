package ir.jimbo.crawler.parse;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class RobotsTxtParserTest {
    private HttpServer server;

    @Before
    public void startServer() throws IOException {
        String data = "";

        // Starting http server
        server = HttpServer.create(new InetSocketAddress(9898), 0);
        server.createContext("/robots.txt", httpExchange -> {
            httpExchange.sendResponseHeaders(200, data.length());
            OutputStream os = httpExchange.getResponseBody();
            os.write(data.toString().getBytes());
            os.close();
        });
        server.start();
    }

    @After
    public void stopServer() {
        server.stop(0);
    }
}
