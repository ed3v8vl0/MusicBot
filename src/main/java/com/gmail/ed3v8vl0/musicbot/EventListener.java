package com.gmail.ed3v8vl0.musicbot;

import com.gmail.ed3v8vl0.musicbot.audio.AudioTrackScheduler;
import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import com.gmail.ed3v8vl0.musicbot.schedule.MessageScheduler;
import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.util.EntityUtil;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.json.response.ErrorResponse;
import discord4j.rest.util.Color;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.text.MessageFormat;
import java.util.Map;

@Slf4j
public class EventListener extends ReactiveEventAdapter {
    private final MusicBot musicBot;
    private final MessageScheduler messageScheduler;

    public EventListener(MusicBot musicBot, MessageScheduler messageScheduler) {
        this.musicBot = musicBot;
        this.messageScheduler = messageScheduler;
    }

    @Override
    public Publisher<?> onMessageCreate(MessageCreateEvent event) {
        return Mono.justOrEmpty(event.getMember()).filter(member -> !member.getId().equals(musicBot.getGatewayDiscordClient().getSelfId())).doOnNext(member -> {
            Message message = event.getMessage();
            String content = message.getContent();

            if (content.equals("!setup") || musicBot.getRegisterChannel(event.getGuildId().orElse(MusicBot.EMPTY_SNOWFLAKE)).equals(message.getChannelId())) {
                musicBot.getCommand(content.startsWith("!") ? content : "!play").execute(event).then(message.delete()).doOnError(throwable -> {
                    if (throwable instanceof ClientException) {
                        ClientException exception = (ClientException) throwable;
                        ErrorResponse errorResponse = exception.getErrorResponse().orElse(null);

                        if (errorResponse != null) {
                            Map<String, Object> responseFields = errorResponse.getFields();

                            messageScheduler.createSchedule(message.getChannelId(), messageCreateSpec -> messageCreateSpec.addEmbed(embed -> embed.setColor(Color.WHITE).setTitle(MessageFormat.format("명령어 입력중 오류가 발생했습니다. 관리자에게 문의해주세요. (Message: {0}, Code: {1})", responseFields.get("message"), responseFields.get("code").toString()))), message1 -> message1.delete().subscribe(), 10000L);
                            return;
                        }
                    }
                    log.error("An unhandled exception occurred in the '{}' command.\n{}", "!setup", throwable.getMessage());
                }).subscribe();
            }
        });
    }

    @Override
    public Publisher<?> onReactionAdd(ReactionAddEvent event) {
        return Mono.justOrEmpty(event.getMember()).filter(member -> !member.getId().equals(musicBot.getGatewayDiscordClient().getSelfId())).flatMap(member -> Mono.justOrEmpty(musicBot.getRegisterChannel(member.getGuildId())).filter(channelId -> channelId.equals(event.getChannelId())).flatMap(channelId -> member.getVoiceState()).flatMap(VoiceState::getChannel).doOnNext(voiceChannel -> {
            GuildAudioManager manager = GuildAudioManager.of(voiceChannel.getGuildId());
            AudioTrackScheduler scheduler = manager.getScheduler();
            String emojiString = EntityUtil.getEmojiString(event.getEmoji());

            switch (emojiString) {
                case "\u23EF": //:play_pause:
                    if (scheduler.isPaused()) scheduler.trackResume();
                    else scheduler.trackPause();
                    break;
                case "\u23F9": //:stop_button:
                    scheduler.trackStop();
                    break;
                case "\u23ED": //track_next:
                    scheduler.trackSkip();
                    break;
            }
        }).then(event.getMessage().flatMap(message -> message.removeReaction(event.getEmoji(), event.getUserId()))));
    }
}
