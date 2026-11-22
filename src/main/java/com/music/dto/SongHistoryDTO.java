package com.music.dto;

import lombok.Data;
import java.util.Date;

@Data
public class SongHistoryDTO {
    private Long historyId;
    private Long songId;
    private String songName;
    private String singer;
    private String coverUrl;
    private String audioUrl;
    private Date playTime;
}
