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
    private final MusicBot musicBot;

    public MessageScheduler(MusicBot musicBot) {
        this.musicBot = musicBot;
        scheduler = Schedulers.newSingle("MessageScheduler", true);
    }

    public void createSchedule(TextChannel channel, Consumer<? super MessageCreateSpec> spec, Consumer<? super Message> task, long delay) {
        channel.createMessage(spec)
                .doOnNext(message -> this.createSchedule(channel.getId(), message.getId(), task, delay))
                .subscribe();
    }

    public void createSchedule(final Snowflake channelId, final Snowflake messageId, Consumer<? super Message> task, long delay) {
        GatewayDiscordClient gatewayDiscordClient = musicBot.getGatewayDiscordClient();

        scheduler.schedule(() -> {
            gatewayDiscordClient.getChannelById(channelId)
                    .cast(TextChannel.class)
                    .flatMap(channel -> channel.getMessageById(messageId).doOnNext(message -> {
                        try {
                            task.accept(message);
                        } catch (Exception e) {
                            logger.error("{}", channel, e);
                        }
                    })).subscribeOn(scheduler).block();
        }, delay, TimeUnit.MILLISECONDS);
    }
}