package com.gmail.ed3v8vl0.musicbot.scheduler;

import discord4j.common.operator.RateLimitOperator;
import discord4j.common.util.Snowflake;
import discord4j.rest.http.client.ClientResponse;
import discord4j.rest.request.GlobalRateLimiter;
import io.netty.handler.codec.http.HttpHeaders;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClientResponse;
import reactor.util.Logger;
import reactor.util.Loggers;

import java.time.Duration;

public class RateLimiter implements GlobalRateLimiter {

    private static final Logger log = Loggers.getLogger(discord4j.rest.request.BucketGlobalRateLimiter.class);

    private final RateLimitOperator<Integer> operator;

    private volatile long limitedUntil = 0;

    RateLimiter(int capacity, Duration refillPeriod, Scheduler delayScheduler) {
        this.operator = new RateLimitOperator<>(capacity, refillPeriod, delayScheduler);
    }

    public static RateLimiter create() {
        return new RateLimiter(50, Duration.ofSeconds(1), Schedulers.parallel());
    }

    public static RateLimiter create(int capacity, Duration refillPeriod, Scheduler delayScheduler) {
        return new RateLimiter(capacity, refillPeriod, delayScheduler);
    }

    @Override
    public Mono<Void> rateLimitFor(Duration duration) {
        return Mono.fromRunnable(() -> limitedUntil = System.nanoTime() + duration.toNanos());
    }

    @Override
    public Mono<Duration> getRemaining() {
        return Mono.fromCallable(() -> Duration.ofNanos(limitedUntil - System.nanoTime()));
    }

    @Override
    public <T> Flux<T> withLimiter(Publisher<T> stage) {
        Mono<ClientResponse> stageMono = ((Mono<ClientResponse>) stage).doOnNext(clientResponse -> {
            HttpClientResponse httpResponse = clientResponse.getHttpResponse();
            HttpHeaders httpHeaders = httpResponse.responseHeaders();
            String methodName = httpResponse.method().name();

            if (methodName.equals("PATCH")) {
                Snowflake guildId = PacketScheduler.getLastPacket(httpResponse.path());

                if (guildId != null) {
                    long limitReset = (long) (Double.parseDouble(httpHeaders.get("X-RateLimit-Reset")) * 1000);
                    PacketScheduler.RateLimitReset(guildId, limitReset);
                }
            }
        });

        return (Flux<T>) Mono.just(0)
                .transform(operator)
                .then(getRemaining())
                .filter(delay -> delay.getSeconds() > 0)
                .flatMapMany(delay -> {
                    log.trace("[{}] Delaying for {}", Integer.toHexString(hashCode()), delay);
                    return Mono.delay(delay).flatMapMany(tick -> Flux.from(stageMono));
                })
                .switchIfEmpty(stageMono);
    }
}