package com.gmail.ed3v8vl0.musicbot.youtube;

import com.google.api.services.youtube.model.SearchResult;
import lombok.Getter;

@Getter
public class VideoData {
    private final String id;
    private final String title;
    private final String thumbnailUrl;

    public VideoData(com.google.api.services.youtube.model.Video video) {
        this.id = video.getId();
        this.title = video.getSnippet().getTitle();
        this.thumbnailUrl = YoutubeAPI.getMaxThumbnail(video.getSnippet().getThumbnails()).getUrl();
    }

    public VideoData(SearchResult searchResult) {
        this.id = searchResult.getId().getVideoId();
        this.title = searchResult.getSnippet().getTitle();
        this.thumbnailUrl = YoutubeAPI.getMaxThumbnail(searchResult.getSnippet().getThumbnails()).getUrl();
    }
}
