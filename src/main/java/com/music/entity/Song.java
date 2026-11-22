package com.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("song")
public class Song {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String songName;
    private String singer;
    private String lyric;
    private String audioUrl;
    private String coverUrl;
    private Integer duration;
    private Date createTime;
    // 播放量
    private Integer playCount;
    // 歌曲风格：流行、古风、说唱等
    private String type;

    // 0=免费 1=VIP专属
    private Integer isVip;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Integer getPlayCount() {
        return playCount;
    }

    public void setPlayCount(Integer playCount) {
        this.playCount = playCount;
    }
}
