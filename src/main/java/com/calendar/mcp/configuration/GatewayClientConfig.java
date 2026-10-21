package com.calendar.mcp.configuration;

import com.calendar.mcp.infrastructure.http.BearerTokenRelay;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/** Client for the gateway. It carries no credentials of its own: every call relays the caller's token. */
@Configuration
public class GatewayClientConfig {

    /** Shorter than the tool's own budget, so a slow calendar surfaces as a gateway failure, not a timeout. */
    static final Duration TIMEOUT = Duration.ofSeconds(8);

    @Bean
    public WebClient gatewayWebClient(@Value("${gateway.url}") String url) {
        return webClient(url, TIMEOUT);
    }

    /** Takes the timeout, so a test need not wait eight seconds. */
    static WebClient webClient(String url, Duration timeout) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
                .responseTimeout(timeout);
        return WebClient.builder()
                .baseUrl(url)
                .filter(BearerTokenRelay.relayCallerToken())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
