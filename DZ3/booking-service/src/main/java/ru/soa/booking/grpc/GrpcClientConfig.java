package ru.soa.booking.grpc;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {
    @Bean(destroyMethod = "shutdown")
    ManagedChannel flightChannel(@Value("${flight.grpc.host}") String host,
                                 @Value("${flight.grpc.port}") int port,
                                 @Value("${flight.grpc.api-key}") String apiKey,
                                 CircuitBreakerInterceptor circuitBreaker) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER), apiKey);
        ClientInterceptor authentication = MetadataUtils.newAttachHeadersInterceptor(metadata);
        return NettyChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .intercept(authentication, circuitBreaker)
                .build();
    }
}

