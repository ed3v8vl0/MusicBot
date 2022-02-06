package com.gmail.ed3v8vl0.musicbot.command;

import com.gmail.ed3v8vl0.musicbot.MusicBot;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

public class SetupCommand implements ICommand {
    private final MusicBot musicBot;

    public SetupCommand(MusicBot musicBot) {
        this.musicBot = musicBot;
    }

    @Override
    public Mono<?> execute(Event event) {
        if (event instanceof MessageCreateEvent) {
            MessageCreateEvent messageCreateEvent = (MessageCreateEvent) event;

            return Mono.just(messageCreateEvent.getMessage())
                    .flatMap(message -> message.getGuild()
                            .flatMap(guild -> message.getChannel()
                                    .doOnNext(channel -> {
                                        if (!musicBot.hasRegisterChannel(guild)) {
                                            TextChannel createChannel = guild.createTextChannel(textChannelCreateSpec -> textChannelCreateSpec.setName("MusicBox")).block();
                                            Message createMessage = createChannel.createMessage(messageCreateSpec -> messageCreateSpec.setContent("노래 제목을 입력하거나 Youtube URL 주소를 입력해주세요.")
                                                    .addEmbed(embed -> embed.setColor(Color.WHITE)
                                                            .setTitle("현재 재생중인 노래가 없습니다.")
                                                            .setImage("https://thumbs.dreamstime.com/b/gramophone-vector-logo-design-template-music-retro-white-background-illustration-53237065.jpg"))).block();

                                            createMessage.addReaction(ReactionEmoji.unicode("\u23EF"))
                                                    .then(createMessage.addReaction(ReactionEmoji.unicode("\u23F9")))
                                                    .then(createMessage.addReaction(ReactionEmoji.unicode("\u23ED"))).block();

                                            if (musicBot.registerChannel(guild, createChannel)) {
                                                musicBot.getMessageScheduler().createSchedule(message.getChannelId(), messageCreateSpec -> messageCreateSpec.addEmbed(embed -> embed.setColor(Color.WHITE).setTitle("채널 설치를 완료했습니다")), message1 -> message1.delete(), 10000L);
                                            } else {
                                                musicBot.getMessageScheduler().createSchedule(message.getChannelId(), messageCreateSpec -> messageCreateSpec.addEmbed(embed -> embed.setColor(Color.WHITE).setTitle("채널 설치중 오류가 발생했습니다. 관리자에게 문의해주세요.")), message1 -> message1.delete(), 10000L);
                                            }
                                        } else {
                                            musicBot.getMessageScheduler().createSchedule(message.getChannelId(), messageCreateSpec -> messageCreateSpec.addEmbed(embed -> embed.setColor(Color.WHITE).setTitle("이미 설치된 채널이 있습니다.")), message1 -> message1.delete(), 10000L);
                                        }
                                    })
                            )
                    );
        } else {
            return Mono.empty();
        }
    }
}
