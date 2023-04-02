package core;

import core.DirDigger;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;

import javax.net.ssl.SSLContext;
import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainTest {

    private static final String USER_AGENT = "Mozilla/5.0";
//    private static final String EXAMPLE_URL = "https://google.com";
    private static final String EXAMPLE_URL = "https://www.unity.com/education";

    public static void main(String[] args) {

        try {
//            example();
//            exampleTwo();
//            exampleWithTimeOut();
//            exampleIgnoreCertificateChecking();
//            exampleRedirect();
//            exampleAsyncRedirect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        JFrame frame = new JFrame();
        JPanel panel = new DirDigger(null).getFrame();
        frame.add(panel);
        frame.setSize(new Dimension(1700, 1200));
        frame.setVisible(true);
    }

    private static void example() {
        final var request = new HttpGet(EXAMPLE_URL);
        List<String> responses = new ArrayList<>();
        try {
            CloseableHttpClient client = HttpClientBuilder.create().build();

            CloseableHttpResponse response = (CloseableHttpResponse) client
                    .execute(request, new HttpClientResponseHandler<Object>() {

                        @Override
                        public Object handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
                            System.out.println(response.toString());
                            return null;
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void exampleTwo() throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(EXAMPLE_URL);
        httpGet.addHeader("User-Agent", USER_AGENT);
//        CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
        CloseableHttpResponse httpResponse = httpClient.execute(httpGet);

        System.out.println("GET Response Status:: "
                + httpResponse.getCode());

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                httpResponse.getEntity().getContent()));

        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = reader.readLine()) != null) {
            response.append(inputLine);
        }
        reader.close();

        // print result
        List<Header> headers = List.of(httpResponse.getHeaders());
        headers.forEach(
                System.out::println
        );
        httpClient.close();
    }

    private static void exampleWithTimeOut() throws IOException {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                // Connection Timeout – the time to establish the connection with the remote host
                .setConnectTimeout(10000, TimeUnit.MILLISECONDS)
                // Socket Timeout – the time waiting for data – after establishing the connection; maximum time of inactivity between two data packets
                .setSocketTimeout(10000, TimeUnit.MILLISECONDS)
                .build();

        BasicHttpClientConnectionManager cm = new BasicHttpClientConnectionManager();
        cm.setConnectionConfig(connectionConfig);

        final HttpGet request = new HttpGet(EXAMPLE_URL);

        RequestConfig requestConfig = RequestConfig.DEFAULT;
        CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(cm)
                .build();

        CloseableHttpResponse response = (CloseableHttpResponse) client
             .execute(request, new HttpClientResponseHandler<Object>() {
                 @Override
                 public Object handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
                     System.out.println(response.toString());
                     return null;
                 }
             });
    }

    private static void exampleIgnoreCertificateChecking() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {

        final HttpGet getMethod = new HttpGet(EXAMPLE_URL);

        final TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
        final SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build();
        final SSLConnectionSocketFactory sslsf =
                new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        final Registry<ConnectionSocketFactory> socketFactoryRegistry =
                RegistryBuilder.<ConnectionSocketFactory> create()
                        .register("https", sslsf)
                        .register("http", new PlainConnectionSocketFactory())
                        .build();

        final BasicHttpClientConnectionManager connectionManager =
                new BasicHttpClientConnectionManager(socketFactoryRegistry);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

             CloseableHttpResponse response = (CloseableHttpResponse) httpClient
                     .execute(getMethod, new HttpClientResponseHandler<Object>() {
                         @Override
                         public Object handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
                             System.out.println(response.toString());
                             return null;
                         }
                     });
    }

//    public static void exampleAsyncRequest() throws ExecutionException, InterruptedException {
//        CloseableHttpAsyncClient client = HttpAsyncClients.createDefault();
//        client.start();
//        HttpGet request = new HttpGet("http://www.google.com");
//
//        Future<HttpResponse> future = client.execute(request, null);
//        HttpResponse response = future.get();
//    }

    private static void exampleRedirect() throws IOException, InterruptedException {
        RequestConfig requestConfig = RequestConfig.custom()
                .setRedirectsEnabled(false)
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
//                .setRedirectStrategy(new DefaultRedirectStrategy())
                .build();

        HttpGet httpGet = new HttpGet(EXAMPLE_URL);
        httpGet.setConfig(requestConfig);
        httpGet.addHeader("User-Agent", USER_AGENT);
//        CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
        CloseableHttpResponse httpResponse = httpClient.execute(httpGet);

        System.out.println("GET Response Status:: "
                + httpResponse.getCode());

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                httpResponse.getEntity().getContent()));

        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = reader.readLine()) != null) {
            response.append(inputLine);
        }
        reader.close();

        // print result
        List<Header> headers = List.of(httpResponse.getHeaders());
        headers.forEach(
                System.out::println
        );
        httpClient.close();
    }
}
