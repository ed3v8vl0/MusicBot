package com.gmail.ed3v8vl0.musicbot;

import com.gmail.ed3v8vl0.musicbot.audio.AudioTrackScheduler;
import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.util.EntityUtil;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Slf4j
public class EventListener extends ReactiveEventAdapter {
    private final MusicBot musicBot;

    public EventListener(MusicBot musicBot) {
        this.musicBot = musicBot;
    }

    @Override
    public Publisher<?> onMessageCreate(MessageCreateEvent event) {
        return Mono.justOrEmpty(event.getMember())
                .filter(member -> !member.getId().equals(musicBot.getGatewayDiscordClient().getSelfId()))
                .doOnNext(member -> {
                    Message message = event.getMessage();
                    String content = message.getContent();

                    if (content.equals("!setup")) {
                        musicBot.getCommand(content).execute(event).then(message.delete()).doOnError(throwable -> log.error("An unhandled exception occurred in the '{}' command.\n{}", "!setup", throwable.getMessage())).subscribe();
                    } else {
                        if (musicBot.getRegisterChannel(event.getGuildId().orElse(null)).equals(message.getChannelId())) {
                            if (content.startsWith("!")) {
                                musicBot.getCommand(content).execute(event).then(message.delete()).doOnError(throwable -> log.error("An unhandled exception occurred in the '{}' command.\n{}", content, throwable.getMessage())).subscribe();
                            } else {
                                musicBot.getCommand("!play").execute(event).then(message.delete()).doOnError(throwable -> log.error("An unhandled exception occurred in the '{}' command.\n{}", "!play", throwable.getMessage())).subscribe();
                            }
                        }
                    }
                });
    }

    @Override
    public Publisher<?> onReactionAdd(ReactionAddEvent event) {
        return Mono.justOrEmpty(event.getMember())
                .filter(member -> !member.getId().equals(musicBot.getGatewayDiscordClient().getSelfId()))
                .flatMap(member -> Mono.justOrEmpty(musicBot.getRegisterChannel(member.getGuildId()))
                        .filter(channelId -> channelId.equals(event.getChannelId()))
                        .flatMap(channelId -> member.getVoiceState())
                        .flatMap(VoiceState::getChannel)
                        .doOnNext(voiceChannel -> {
                            GuildAudioManager manager = GuildAudioManager.of(voiceChannel.getGuildId());
                            AudioTrackScheduler scheduler = manager.getScheduler();
                            String emojiString = EntityUtil.getEmojiString(event.getEmoji());

                            switch (emojiString) {
                                case "\u23EF": //:play_pause:
                                    if (scheduler.isPaused())
                                        scheduler.trackResume();
                                    else
                                        scheduler.trackPause();
                                    break;
                                case "\u23F9": //:stop_button:
                                    scheduler.trackStop();
                                    break;
                                case "\u23ED": //track_next:
                                    scheduler.trackSkip();
                                    break;
                            }
                        })
                        .then(event.getMessage().flatMap(message -> message.removeReaction(event.getEmoji(), event.getUserId())))
                );
    }
}
