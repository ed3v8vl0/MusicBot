package com.gmail.ed3v8vl0.musicbot.schedule;

import com.gmail.ed3v8vl0.musicbot.MusicBot;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.MessageCreateSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class MessageScheduler {
    private static final Logger logger = LoggerFactory.getLogger(MessageScheduler.class);
    private final Scheduler scheduler;
    private final GatewayDiscordClient gatewayDiscordClient;

    public MessageScheduler(MusicBot musicBot) {
        this.gatewayDiscordClient = musicBot.getGatewayDiscordClient();
        scheduler = Schedulers.newSingle("MessageScheduler", true);
    }

    public void createSchedule(Snowflake channelId, Consumer<? super MessageCreateSpec> spec, Consumer<? super Message> task, long delay) {
        gatewayDiscordClient.getChannelById(channelId)
                .cast(TextChannel.class)
                .flatMap(channel -> channel.createMessage(spec))
                .doOnNext(message -> {
                    scheduler.schedule(() -> {
                        try {
                            task.accept(message);
                        } catch (Exception e) {
                            logger.error("{}", e);
                        }
                    }, delay, TimeUnit.MILLISECONDS);
                })
                .doOnError(throwable -> System.out.println(throwable.toString()))
                .subscribeOn(scheduler).subscribe();
    }
}