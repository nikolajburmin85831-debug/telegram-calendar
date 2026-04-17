package io.github.nadya.assistant.app.ec2.config.http;

import io.github.nadya.assistant.app.ec2.config.properties.AssistantHttpClientProperties;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class OutboundHttpClientFactory {

    private final AssistantHttpClientProperties properties;

    public OutboundHttpClientFactory(AssistantHttpClientProperties properties) {
        this.properties = properties;
    }

    public HttpClient create() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()));

        if (!properties.proxy().enabled() || properties.proxy().url().isBlank()) {
            return builder.build();
        }

        ParsedProxy parsedProxy = parseProxy(properties.proxy());
        builder.proxy(ProxySelector.of(new InetSocketAddress(parsedProxy.host(), parsedProxy.port())));
        if (!parsedProxy.username().isBlank()) {
            builder.authenticator(new ProxyAuthenticator(parsedProxy.username(), parsedProxy.password()));
        }
        return builder.build();
    }

    private ParsedProxy parseProxy(AssistantHttpClientProperties.Proxy proxy) {
        String rawUrl = proxy.url().contains("://") ? proxy.url() : "http://" + proxy.url();
        URI uri = URI.create(rawUrl);
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("HTTP proxy URL must include a host");
        }

        int port = uri.getPort();
        if (port <= 0) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }

        String username = proxy.username();
        String password = proxy.password();
        if (username.isBlank() && uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            String[] userInfoParts = uri.getUserInfo().split(":", 2);
            username = decode(userInfoParts[0]);
            password = userInfoParts.length > 1 ? decode(userInfoParts[1]) : "";
        }

        return new ParsedProxy(host, port, username, password);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record ParsedProxy(String host, int port, String username, String password) {
    }

    private static final class ProxyAuthenticator extends Authenticator {

        private final String username;
        private final char[] password;

        private ProxyAuthenticator(String username, String password) {
            this.username = username;
            this.password = password == null ? new char[0] : password.toCharArray();
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            if (getRequestorType() != RequestorType.PROXY) {
                return null;
            }
            return new PasswordAuthentication(username, password);
        }
    }
}
