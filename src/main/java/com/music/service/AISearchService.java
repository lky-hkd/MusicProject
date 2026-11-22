package com.music.service;

import com.music.entity.Song;
import java.util.List;

public interface AISearchService {
    List<Song> aiSearch(String userQuery) throws Exception;
}
