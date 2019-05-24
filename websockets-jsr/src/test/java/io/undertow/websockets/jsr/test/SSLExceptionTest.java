package io.undertow.websockets.jsr.test;

import javax.net.ssl.SSLContext;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.Session;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.undertow.UndertowOptions;
import io.undertow.websockets.WebSocketExtension;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.client.WebSocketClientNegotiation;
import io.undertow.websockets.jsr.DefaultWebSocketClientSslProvider;
import io.undertow.websockets.jsr.ServerWebSocketContainer;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.ssl.XnioSsl;

public class SSLExceptionTest {

    public static void main(String[] args) throws Exception {
        // Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 8888));
        Proxy proxy = Proxy.NO_PROXY;
        Session session =
            connect(proxy, new URI("wss://expired.badssl.com"));
        System.err.println("Connected: " + session.getRequestURI());
        session.close();
    }

    private static  Session connect(Proxy proxy, URI serverURI)
        throws IOException, DeploymentException, NoSuchAlgorithmException
    {
        System.err.println("Destination: <" + serverURI + ">");
        URI proxyUri = null;
        if (proxy != null && proxy.type() != Proxy.Type.DIRECT) {
            InetSocketAddress sa = (InetSocketAddress) proxy.address();
            try {
                proxyUri = new URI("http", null, sa.getHostString(),
                    sa.getPort(), null, null, null);
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }
        }
        System.err.println("Proxy: <" + proxyUri + ">");
        OptionMap options = OptionMap.builder()
            .set(Options.WORKER_IO_THREADS, 2)
            .set(Options.TCP_NODELAY, true)
            .set(Options.CORK, true)
            .set(Options.USE_DIRECT_BUFFERS, true)
            .set(UndertowOptions.ENDPOINT_IDENTIFICATION_ALGORITHM, "HTTPS")
            .getMap();

        ClientEndpointConfig config =
            ClientEndpointConfig.Builder.create().build();
        config.getUserProperties().put(
            DefaultWebSocketClientSslProvider.SSL_CONTEXT,
            SSLContext.getDefault());

        WebSocketClientNegotiation clientNegotiation =
            new WebSocketClientNegotiation(config.getPreferredSubprotocols(),
                toExtensionList(config.getExtensions()));

        Endpoint endpoint = new TestEndpoint();

        ServerWebSocketContainer wsc =
            (ServerWebSocketContainer) ContainerProvider.getWebSocketContainer();

        XnioSsl xnioSsl = new DefaultWebSocketClientSslProvider().getSsl(
            wsc.getXnioWorker(),
            endpoint,
            config,
            serverURI);

        WebSocketClient.ConnectionBuilder builder =
            new WebSocketClient.ConnectionBuilder(wsc.getXnioWorker(),
                wsc.getBufferPool(), serverURI)
                .setProxyUri(proxyUri)
                .setClientNegotiation(clientNegotiation)
                .setOptionMap(options)
                .setSsl(xnioSsl);
        return wsc.connectToServer(endpoint, config, builder);
    }

    private static List<WebSocketExtension> toExtensionList(final List<Extension> extensions) {
        List<WebSocketExtension> ret = new ArrayList<>(extensions.size());
        for (Extension e : extensions) {
            final List<WebSocketExtension.Parameter> parameters =
                e.getParameters().stream()
                    .map(p -> new WebSocketExtension.Parameter(p.getName(), p.getValue()))
                    .collect(Collectors.toList());
            ret.add(new WebSocketExtension(e.getName(), parameters));
        }
        return ret;
    }

    private static class TestEndpoint extends Endpoint {

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            System.err.println("#onOpen");
        }

        @Override
        public void onError(Session session, Throwable t) {
            System.err.println("#onError");
            t.printStackTrace();
        }
        @Override
        public void onClose(Session session, CloseReason closeReason) {
            System.err.println("#onClose: " + closeReason);
        }

    }

}
