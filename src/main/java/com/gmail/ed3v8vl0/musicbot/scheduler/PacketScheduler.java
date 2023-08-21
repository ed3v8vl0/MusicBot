package com.gmail.ed3v8vl0.musicbot.scheduler;

import com.gmail.ed3v8vl0.musicbot.audio.AudioTrackScheduler;
import com.gmail.ed3v8vl0.musicbot.audio.GuildAudioManager;
import com.gmail.ed3v8vl0.musicbot.youtube.VideoData;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.object.Embed;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import reactor.core.publisher.Flux;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketScheduler {
    /**
     * Key: GuildId, Value: PacketScheduler
     */
    public static final Map<Snowflake, PacketScheduler> schedulerMap = new HashMap<>();
    private static final Thread scheduleThread;
    private static final Map<String, Snowflake> lastPacket = new ConcurrentHashMap<>();
    private static final Map<Snowflake, Long> rateLimitReset = new ConcurrentHashMap<>();
    static final Object lock = new Object();

    public final Snowflake guildId;
    private ArrayList<Message> messages = new ArrayList<>();

    static {
        scheduleThread = new Thread(() -> {
            long timeStamp = System.currentTimeMillis();
            while(true) {
                for (Map.Entry<Snowflake, PacketScheduler> entry : schedulerMap.entrySet()) {
                    Snowflake guildId = entry.getKey();
                    PacketScheduler packetScheduler = entry.getValue();
                    List<Message> copyList;

                    if(rateLimitReset.getOrDefault(guildId, 0L) > System.currentTimeMillis())
                        continue;

                    synchronized (lock) {
                        if (packetScheduler.messages.isEmpty())
                            continue;

                        copyList = (ArrayList<Message>) packetScheduler.messages.clone();
                        packetScheduler.messages.clear();
                    }

                    System.out.println(System.currentTimeMillis() - timeStamp);
                    timeStamp = System.currentTimeMillis();

                    Message message = copyList.get(0);
                    GuildAudioManager guildAudioManager = GuildAudioManager.of(guildId);
                    AudioTrackScheduler audioTrackScheduler = guildAudioManager.getScheduler();

                    if (copyList.size() == 1) {
                        message.delete().subscribe();
                    } else {
                        message.getChannel().ofType(TextChannel.class)
                                .flatMapMany(textChannel -> textChannel.bulkDeleteMessages(Flux.fromIterable(copyList)))
                                .subscribe();
                    }

                    /**
                     * PATCH /channels/{channel.id}/messages/{message.id}
                     * rateLimitReset 시간을 미리 1000ms 딜레이를 주고 Discord 서버에서 응답을 받으면 동기화 시켜줌.
                     * 미리 딜레이를 주지 않으면 Discord 서버에서 응답을 받기전에 미리 패킷을 보내어 RateLimit 제한이 걸림.
                     */
                    rateLimitReset.put(guildId, System.currentTimeMillis() + 1000);
                    message.getChannel().ofType(TextChannel.class)
                            .flatMap(channel -> channel.getMessagesAfter(Snowflake.of(0)).next())
                            .flatMap(editMessage -> {
                                lastPacket.put("api/v8/channels/" + message.getChannelId().asString() + "/messages/" + editMessage.getId().asString(), guildId);
                                MessageEditSpec.Builder messageEditSpec = MessageEditSpec.builder();
                                StringBuilder builder = new StringBuilder();
                                List<AbstractMap.SimpleEntry<VideoData, AudioTrack>> trackQueue = audioTrackScheduler.getTrackQueue();
                                boolean trackStarted = audioTrackScheduler.isTrackStarted();
                                VideoData video = audioTrackScheduler.getVideoData();

                                if (!trackQueue.isEmpty()) {
                                    builder.append("__**재생 목록: **__");
                                    for (int i = 1; i <= trackQueue.size(); i++) {
                                        AbstractMap.SimpleEntry<VideoData, AudioTrack> entry2 = trackQueue.get(i - 1);

                                        builder.append(i);
                                        builder.append(". ");
                                        builder.append(entry2.getKey().getTitle());

                                        if (i < trackQueue.size())
                                            builder.append('\n');
                                    }
                                } else {
                                    builder.append("노래 제목을 입력하거나 Youtube URL 주소를 입력해주세요 !");
                                }

                                List<Embed> embedList = editMessage.getEmbeds();

                                if (embedList != null) {
                                    Embed oldEmbed = embedList.get(0);

                                    messageEditSpec.contentOrNull(builder.toString()).addEmbed(EmbedCreateSpec.builder().title(trackStarted ? video.getTitle() : oldEmbed.getTitle().orElse("Data Error"))
                                            .image(trackStarted ? video.getThumbnailUrl() : (oldEmbed.getImage().isPresent() ? oldEmbed.getImage().get().getUrl() : ""))
                                            .url(trackStarted ? "https://youtu.be/" + video.getId() : "").build());
                                }

                                return editMessage.edit(messageEditSpec.build());
                            }).subscribe();
                }
            }
        });

        scheduleThread.start();
    }

    public PacketScheduler(Snowflake guildId) {
        this.guildId = guildId;
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    @Nullable
    public static Snowflake getLastPacket(String url) {
        return lastPacket.remove(url);
    }

    public static void RateLimitReset(Snowflake guildId, long time) {
        rateLimitReset.put(guildId, time);
    }

    public static PacketScheduler getScheduler(Snowflake guildId) {
        return schedulerMap.computeIfAbsent(guildId, key -> new PacketScheduler(key));
    }
}