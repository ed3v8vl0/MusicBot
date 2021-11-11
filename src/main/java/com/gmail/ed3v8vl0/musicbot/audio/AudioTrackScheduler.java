package com.gmail.ed3v8vl0.musicbot.audio;

import com.gmail.ed3v8vl0.musicbot.MusicBot;
import com.gmail.ed3v8vl0.musicbot.youtube.YoutubeAPI;
import com.google.api.services.youtube.model.Video;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.Embed;
import discord4j.core.object.entity.channel.TextChannel;
import reactor.core.publisher.Mono;

import java.util.AbstractMap;
import java.util.LinkedList;
import java.util.List;

public final class AudioTrackScheduler extends AudioEventAdapter {
    //ConcurrentModificationException
    private final List<AbstractMap.SimpleEntry<Video, AudioTrack>> trackQueue;
    private final Snowflake guildId;
    private final AudioPlayer player;

    public AudioTrackScheduler(final Snowflake guildId, final AudioPlayer player) {
        trackQueue = new LinkedList<>();
        this.guildId = guildId;
        this.player = player;
    }

    public List<AbstractMap.SimpleEntry<Video, AudioTrack>> getTrackQueue() {
        return trackQueue;
    }

    public void trackPlay(Video video, AudioTrack audioTrack) {
        trackPlay(video, audioTrack, false);
    }

    /**
     * 굳이 여기서 hasRegisterChannel을 확인해야 하는지
     * Guild에서 추방을 당하면 trackScheduler도 작동하지 않는지 확인.
     */
    public synchronized void trackPlay(Video video, AudioTrack audioTrack, boolean force) {
        final boolean playing = player.startTrack(audioTrack, !force);

        if (!playing) {
            trackQueue.add(new AbstractMap.SimpleEntry<>(video, audioTrack));
        }
        MusicBot musicBot = MusicBot.getInstance();
        GatewayDiscordClient gatewayDiscordClient = musicBot.getGatewayDiscordClient();

        gatewayDiscordClient.getGuildById(guildId)
                .doOnNext(guild -> {
                    Snowflake channelId = musicBot.getRegisterChannel(guild.getId());

                    guild.getChannelById(channelId).cast(TextChannel.class)
                            .flatMap(channel -> channel.getMessagesAfter(Snowflake.of(0))
                                    .flatMap(afterMessage -> Mono.justOrEmpty(afterMessage.getAuthor())
                                            .filter(author -> author.getId().equals(gatewayDiscordClient.getSelfId()))
                                            .doOnNext(author -> afterMessage.edit(messageEditSpec -> {
                                                StringBuilder builder = new StringBuilder();

                                                if (trackQueue.isEmpty()) {
                                                    builder.append("노래 제목을 입력하거나 Youtube URL 주소를 입력해주세요.");
                                                } else {
                                                    //List가 동기방식으로 설정되어 있기 때문에 문제없음.?? 확인
                                                    for (int i = 1; i <= trackQueue.size(); i++) {
                                                        AbstractMap.SimpleEntry<Video, AudioTrack> entry = trackQueue.get(i - 1);

                                                        builder.append(i);
                                                        builder.append(". ");
                                                        builder.append(entry.getKey().getSnippet().getTitle());
                                                        builder.append('\n');
                                                    }
                                                    builder.deleteCharAt(builder.length() - 1);
                                                }

                                                Embed oldEmbed = afterMessage.getEmbeds().get(0);

                                                //oldEmbed로 setImage 설정하는 경우 기존에 생성된 자원을 재활용 할 수가 없음.
                                                messageEditSpec.setContent(builder.toString()).addEmbed(embedCreateSpec ->
                                                        embedCreateSpec.setTitle(playing ? video.getSnippet().getTitle() + " " + YoutubeAPI.getDurationFormat(video.getContentDetails().getDuration()) : oldEmbed.getTitle().orElse("Data Error"))
                                                                .setImage(playing ? YoutubeAPI.getMaxThumbnail(video.getSnippet().getThumbnails()).getUrl() : (oldEmbed.getImage().isPresent() ? oldEmbed.getImage().get().getUrl() : ""))
                                                                .setUrl(playing ? "https://youtu.be/" + video.getId() : ""));
                                            }).subscribe())).next())
                            .subscribe();
                }).subscribe();

    }

    public synchronized void trackStop() {
        MusicBot musicBot = MusicBot.getInstance();
        GatewayDiscordClient gatewayDiscordClient = musicBot.getGatewayDiscordClient();

        trackQueue.clear();
        player.stopTrack();
        gatewayDiscordClient.getGuildById(guildId)
                .doOnNext(guild -> {
                    Snowflake channelId = musicBot.getRegisterChannel(guild.getId());

                    guild.getChannelById(channelId).cast(TextChannel.class)
                            .flatMap(channel -> channel.getMessagesAfter(Snowflake.of(0))
                                    .flatMap(afterMessage -> Mono.justOrEmpty(afterMessage.getAuthor())
                                            .filter(author -> author.getId().equals(gatewayDiscordClient.getSelfId()))
                                            .doOnNext(author -> afterMessage.edit(messageEditSpec ->
                                                            messageEditSpec.setContent("").addEmbed(embedCreateSpec ->
                                                                    embedCreateSpec.setTitle("현재 재생중인 노래가 없습니다.")
                                                                            .setImage("https://thumbs.dreamstime.com/b/gramophone-vector-logo-design-template-music-retro-white-background-illustration-53237065.jpg")))
                                                    .subscribe())).next())
                            .subscribe();
                }).subscribe();
    }

    public synchronized void trackPause() {
        player.setPaused(true);
    }

    public synchronized void trackResume() {
        player.setPaused(false);
    }

    public synchronized void trackSkip() {
        if (trackQueue.isEmpty()) {
            trackStop();
        } else {
            AbstractMap.SimpleEntry<Video, AudioTrack> entry = trackQueue.remove(0);
            trackPlay(entry.getKey(), entry.getValue(), true);
        }
    }

    public synchronized boolean isPaused() {
        return player.isPaused();
    }

    @Override
    public void onTrackEnd(final AudioPlayer player, final AudioTrack track, final AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            trackSkip();
        }
    }
}