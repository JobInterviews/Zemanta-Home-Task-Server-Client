package com.zemanta.task.server;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.core5.http.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());

    public static final int ALLOWED_NUMBER_OF_REQUESTS_PER_FRAME = 5;
    private static final long TIME_FRAME_IN_MILLISECONDS = TimeUnit.SECONDS.toMillis(5);
    private static final String CLIENT_ID_PARAM = "clientId";
    private static final ConcurrentHashMap<Integer, RequestStats> requests = new ConcurrentHashMap<>();
    private final int port;

    private HttpServer server = null;

    public Server(int port) {
        this.port = port;
        initServer();
        logger.log(Level.INFO, "Server is up, listening on port: " + port);
    }

    private void initServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            HttpContext context = server.createContext("/");
            context.setHandler(new RequestHandler());
            server.start();
        } catch (Exception e) {
            throw new RuntimeException("Cannot open port " + port, e);
        }
    }

    private class RequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                int id = getId(exchange);
                int responseCode = isServiceAvailable(id) ? HttpStatus.SC_OK : HttpStatus.SC_SERVICE_UNAVAILABLE;
                logger.info("For client id: " + id + " response code is: " + responseCode);
                sendResponse(exchange, responseCode);
            } catch (BadRequestException e) {
                handleError(exchange, e, HttpStatus.SC_BAD_REQUEST);
            } catch (Exception e) {
                handleError(exchange, e, HttpStatus.SC_SERVER_ERROR);
            }
        }

        private void sendResponse(HttpExchange exchange, int responseCode) throws IOException {
            exchange.sendResponseHeaders(responseCode, 0);
            OutputStream os = exchange.getResponseBody();
            os.write("".getBytes());
            os.close();
        }

        private void handleError(HttpExchange exchange, Exception e, int responseCode) throws IOException {
            sendResponse(exchange, responseCode);
            logger.log(Level.SEVERE, e.getMessage());
        }

        private int getId(HttpExchange exchange) {
            try {
                String[] parameters = extractQueryParameters(exchange);

                if(parameters.length == 2 && parameters[0].equals(CLIENT_ID_PARAM))
                    return Integer.parseInt(URLDecoder.decode(parameters[1], StandardCharsets.UTF_8));
                else
                    throw new BadRequestException("No or wrong parameter name");
            }catch (Exception e){
                throw new BadRequestException(e.getMessage());
            }
        }

        private String[] extractQueryParameters(HttpExchange exchange) {
            URI requestURI = exchange.getRequestURI();
            String query = requestURI.getQuery();
            return query.split("=");
        }

    }


    private boolean isServiceAvailable(int id) {
        RequestStats requestStats = requests.computeIfAbsent(id, (v) -> new RequestStats(id));

        return requestStats.isValid();
    }

    private static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    public static class RequestStats {
        private final Object lock = new Object();

        long lastArrival = System.currentTimeMillis();
        int counter = 1;
        int id;

        public RequestStats(int id) {
            this.id = id;
        }

        public RequestStats(int id, long time, int counter) {
            this.id = id;
            this.lastArrival = time;
            this.counter = counter;
        }

        public boolean isValid() {
            long now = System.currentTimeMillis();

            if (isOutOfTimeFrame(now)) {
                reset(now);
                return true;
            } else {
                countArrival();
                logger.info("For client id: " + id + " counter: " + counter);
                return isValidRequestArrival();
            }
        }

        private void reset(long time) {
            synchronized(lock) {
                lastArrival = time;
                counter = 1;
            }
        }

        private void countArrival() {
            synchronized(lock) {
                counter++;
            }
        }

        private boolean isValidRequestArrival() {
            return this.counter <= ALLOWED_NUMBER_OF_REQUESTS_PER_FRAME;
        }

        private boolean isOutOfTimeFrame(long now) {
            return now - this.lastArrival > TIME_FRAME_IN_MILLISECONDS;
        }
    }

    public void shutdown() {
        this.server.stop(10);
    }

    public static void main(String[] args){
        Server server = new Server(8080);

        var shutdownListener = new Thread(() -> {
            logger.info("Shutting down....");
            server.shutdown();
        });
        Runtime.getRuntime().addShutdownHook(shutdownListener);
    }
}
