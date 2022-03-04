package com.zemanta.task.client;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class Client {
    private static final Logger logger = Logger.getLogger(Client.class.getName());

    private static final int MAX_CONNECTIONS = 100;
    private static final int MAX_DELAY_TIME = 6000;

    private final int id;
    private final int workers;
    private final int maxConnections;
    private final int maxDelayTime;
    private final String requestUrl;
    private volatile boolean working = false;
    private ExecutorService threadPool;
    private CloseableHttpClient httpClient;
    private HttpGet request;


    public Client(int id, int workers, String url) {
        this(id, workers, url, MAX_CONNECTIONS, MAX_DELAY_TIME);
    }

    public Client(int id, int workers, String url, int maxConnections, int maxDelayTime) {
        this.id = id;
        this.workers = workers;
        this.requestUrl = url+"?clientId="+id;
        this.maxConnections = maxConnections;
        this.maxDelayTime = maxDelayTime;
        init();
    }

    private void init() {
        createHttpClient();
        createHttpGet();
        createClientThreads();
    }

    private void createHttpClient() {
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(maxConnections);
        HttpClientBuilder clientBuilder = HttpClients.custom().setConnectionManager(connManager);
        httpClient = clientBuilder.build();
    }

    private void createHttpGet() {
        request = new HttpGet(requestUrl);
    }

    private void createClientThreads() {
        threadPool = Executors.newFixedThreadPool(workers);
        working = true;
        Stream.iterate(0, i -> i < workers, i -> i + 1)
                .forEach(i -> {
                    threadPool.execute(new Worker());
                });
    }

    private class Worker implements Runnable{
        @Override
        public void run() {
            CloseableHttpResponse httpResponse = null;
            Random random = new Random();

            try {
                while(working) {
                    int randomSleepTime = random.nextInt(maxDelayTime);
                    Thread.sleep(randomSleepTime);

                    httpResponse = sendRequest();
                    if (httpResponse != null) {
                        handleResponse(httpResponse);
                        closeResponse(httpResponse);
                    }
                }

                logger.info("Thread: " + Thread.currentThread().getId() +" of Client: "+id+ " has stopped looping");
            } catch (Exception  e) {
                logger.severe(e.getMessage());
            } finally {
                closeResponse(httpResponse);
            }
        }

        private void handleResponse(CloseableHttpResponse httpResponse) {
            logger.info("Client "+id+":  Response code is : " + httpResponse.getCode());
        }

        private void closeResponse(CloseableHttpResponse httpResponse) {
            if(httpResponse != null) {
                try {
                    httpResponse.close();
                } catch (IOException e) {
                    logger.severe("Fail to close response:" + e.getMessage());
                }
            }
        }

        private CloseableHttpResponse sendRequest() throws IOException {
            return httpClient.execute(request);
        }
    }

    public void shutdown() {
        //for tests
        //System.out.println("Client "+id+":" + " Shutting down all threads....");
        working = false;
        try {
            threadPool.awaitTermination(7, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //for tests
        //System.out.println("Client "+id+":" + " :All threads have been stopped");
    }


    public static void main(String[] args) {
        int numberOfClients = 0, numberOfThreads = 0;

        try {
            numberOfClients = Integer.parseInt(args[0]);
            numberOfThreads = Integer.parseInt(args[1]);
        } catch (Exception e){
            throw new IllegalArgumentException("Please provide two numbers, number of clients, and number of threads for each client.");
        }

        Client[] clients = new Client[numberOfClients];
        String serverUrl = "http://localhost:8080/";

        for(int i = 0; i < numberOfClients; i++){
            clients[i] = new Client(i+1, numberOfThreads, serverUrl);
        }

        var shutdownListener = new Thread(() -> {
            logger.info("Shutting down....");
            Arrays.stream(clients).forEach(Client::shutdown);
        });

        Runtime.getRuntime().addShutdownHook(shutdownListener);
    }
}
