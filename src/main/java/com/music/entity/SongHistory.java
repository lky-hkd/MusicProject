package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("song_history")
public class SongHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long songId;
    // 播放时间
    private Date playTime;
}
