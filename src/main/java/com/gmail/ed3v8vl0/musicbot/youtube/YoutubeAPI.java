package com.gmail.ed3v8vl0.musicbot.youtube;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Prints a list of videos based on a search term.
 *
 * @author Jeremy Walker
 */
public class YoutubeAPI {
    private static final Logger logger = LoggerFactory.getLogger(YoutubeAPI.class);

    private final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final YouTube youtube;
    private YouTube.Videos.List videos;
    private YouTube.Search.List search;

    public YoutubeAPI(String GOOGLE_KEY) {
        youtube = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, request -> {
        }).setApplicationName("musicbox").build();

        try {
            videos = youtube.videos().list(Arrays.asList("id", "snippet"));
            videos.setKey(GOOGLE_KEY).setFields("items(id,snippet(thumbnails,title))");
            search = youtube.search().list(Arrays.asList("id, snippet"));
            search.setKey(GOOGLE_KEY).setType(Arrays.asList("video")).setFields("items/id/videoId,items/snippet(thumbnails,title)").setMaxResults(1L);
        } catch (IOException e) {
            logger.error("", e);
        }
    }

    public VideoData searchVideo(String query) {
        try {
            search.setQ(query);
            SearchListResponse searchResponse = search.execute();
            List<SearchResult> searchResults = searchResponse.getItems();

            if (!searchResults.isEmpty()) {
                return new VideoData(searchResults.get(0));
            }
        } catch (IOException e) {
            logger.error("", e);
        }

        return null;
    }

    public VideoData getVideo(String query) {
        try {
            VideoListResponse response = videos.setId(Arrays.asList(query)).execute();
            List<Video> videoList = response.getItems();

            if (!videoList.isEmpty()) {
                return new VideoData(videoList.get(0));
            }
        } catch (IOException e) {
            logger.error("", e);
        }

        return null;
    }

    public static Thumbnail getMaxThumbnail(ThumbnailDetails thumbnailDetails) {
        Thumbnail maxres = thumbnailDetails.getMaxres();

        if (maxres != null) {
            return maxres;
        } else {
            Thumbnail standard = thumbnailDetails.getStandard();

            if (standard != null) {
                return standard;
            } else {
                Thumbnail high = thumbnailDetails.getHigh();

                if (high != null) {
                    return high;
                } else {
                    Thumbnail medium = thumbnailDetails.getMedium();

                    if (medium != null) {
                        return medium;
                    } else {
                        return thumbnailDetails.getDefault();
                    }
                }
            }
        }
    }

    @Deprecated
    public static String getDurationFormat(String duration) {
        StringBuilder builder = new StringBuilder();

        builder.append('[');
        if (duration.startsWith("PT")) {
            char[] charArr = duration.toCharArray();
            int offset = 2;

            for (int i = 2; i < charArr.length; i++) {
                if (charArr[i] == 'H') {
                    builder.append(new String(charArr, offset, i - offset));
                    builder.append(':');
                    offset = i + 1;
                } else if (charArr[i] == 'M' || charArr[i] == 'S') {
                    builder.append(String.format("%2s", new String(charArr, offset, i - offset)).replace(' ', '0'));
                    offset = i + 1;

                    if (charArr[i] == 'M')
                        builder.append(':');
                }
            }
        }
        builder.append(']');

        return builder.toString();
    }

    /**
     * Query String을 파싱해 videoId를 반환합니다.
     *
     * @param query
     * @return Youtube videoId
     */
    public static String parseQuery(String query) {
        for (int i = 0; i < query.length(); i++) {
            String key = query.substring(i, i = query.indexOf('=', i + 1));
            String value = query.substring(i + 1, (i = query.indexOf('&', i + 1)) == -1 ? query.length() : i);

            if (key.equals("v"))
                return value;
        }

        return "";
    }
}