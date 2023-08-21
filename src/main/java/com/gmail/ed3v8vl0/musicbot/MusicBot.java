package com.gmail.ed3v8vl0.musicbot;

import com.gmail.ed3v8vl0.musicbot.command.*;
import com.gmail.ed3v8vl0.musicbot.scheduler.RateLimiter;
import com.gmail.ed3v8vl0.musicbot.youtube.YoutubeAPI;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class MusicBot {
    public static final Snowflake EMPTY_SNOWFLAKE = Snowflake.of(-1);

    private static final Logger logger = LoggerFactory.getLogger(MusicBot.class);
    private static MusicBot INSTANCE;

    // Key: GuildId | Value: ChannelId
    private final Map<Snowflake, Snowflake> channelMap = new HashMap<>();
    // Key: Command Name | Value: Command
    private final Map<String, ICommand> commandMap = new HashMap<>();

    @Getter
    private final DiscordClient discordClient;
    @Getter
    private final GatewayDiscordClient gatewayDiscordClient;
    @Getter
    private Connection connection;

    @Getter
    private final EventListener eventListener;
    @Getter
    private final YoutubeAPI youtubeAPI;
    @Getter
    private final AudioPlayerManager audioPlayerManager;

    public static void main(final String[] args) {
        new MusicBot();
        INSTANCE.getGatewayDiscordClient().onDisconnect().block();
    }

    private MusicBot() {
        MusicBot.INSTANCE = this;
        Properties properties = new Properties();

        try {
            properties.load(getClass().getClassLoader().getResourceAsStream("server.properties"));
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(properties.getProperty("url"), properties.getProperty("username"), properties.getProperty("password"));
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM channels");
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next())
                channelMap.put(Snowflake.of(resultSet.getLong(1)), Snowflake.of(resultSet.getLong(2)));
        } catch (ClassNotFoundException | SQLException | IOException e) {
            logger.error("", e);
            System.exit(1);
        }

        //PreInit
        youtubeAPI = new YoutubeAPI(properties.getProperty("GOOGLE_KEY"));
        //discordClient = DiscordClient.create(properties.getProperty("DISCORD_TOKEN"));
        discordClient = DiscordClient.builder(properties.getProperty("DISCORD_TOKEN")).setGlobalRateLimiter(RateLimiter.create()).build();
        gatewayDiscordClient = discordClient.login().block();
        eventListener = new EventListener(this);

        //Init
        gatewayDiscordClient.on(eventListener).subscribe();
        audioPlayerManager = new DefaultAudioPlayerManager();
        audioPlayerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        AudioSourceManagers.registerRemoteSources(audioPlayerManager);
        AudioSourceManagers.registerLocalSource(audioPlayerManager);


        //PostInit
        /**
         * 추후 com.gmail.ed3v8vl0.musicbot.command 패키지에 @Command 어노테이션이 등록된 클래스들을 불러와서
         * 자동으로 커맨드를 등록
         */
        commandMap.put("setup", new SetupCommand(this));
//        commandMap.put("play", new PlayCommand(this, audioPlayerManager));
//        commandMap.put("stop", new StopCommand());
//        commandMap.put("pause", new PauseCommand());
//        commandMap.put("resume", new ResumeCommand());
//        commandMap.put("skip", new SkipCommand());

        gatewayDiscordClient.getRestClient().getApplicationId()
                .doOnNext(applicationId -> Flux.fromIterable(commandMap.values())
                        .flatMap(command -> gatewayDiscordClient.getRestClient().getApplicationService().createGlobalApplicationCommand(applicationId, command.commandRequest))
                        .subscribe())
                .subscribe();
    }

    public void registerChannel(Snowflake guildId, Snowflake chanelId) {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO channels(guildId, channelId) VALUES(?,?)\n" +
                "ON DUPLICATE KEY UPDATE channelId=VALUES(channelId)")) {
            statement.setLong(1, guildId.asLong());
            statement.setLong(2, chanelId.asLong());

            statement.execute();
            channelMap.put(guildId, chanelId);
        } catch (SQLException e) {
            logger.error("", e);
        }
    }

    public Mono<Boolean> isUnregisterChannel(Guild guild) {
        Snowflake channelId = channelMap.get(guild.getId());

        if (channelId != null) {
            return guild.getChannels()
                    .filter(channel -> channel.getId().equals(channelId))
                    .count()
                    .map(count -> 0 == count);
        } else {
            return Mono.just(true);
        }
    }

    public Snowflake getRegisterChannel(Snowflake guildId) {
        return Optional.ofNullable(channelMap.get(guildId)).orElse(EMPTY_SNOWFLAKE);
    }

    public ICommand getCommand(String command) {
        return commandMap.get(command);
    }

    public static MusicBot getInstance() {
        return MusicBot.INSTANCE;
    }
}