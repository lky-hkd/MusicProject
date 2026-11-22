package com.music.dto;

import lombok.Data;

import java.util.List;

@Data
public class PostCreateRequest {
    private String content;
    /** 已上传的图片 URL，最多 9 张 */
    private List<String> images;
}
