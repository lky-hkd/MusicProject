package com.music.controller;

import com.music.common.Result;
import com.music.common.UserContext;
import com.music.dto.ChatOpenRequest;
import com.music.dto.ChatReadRequest;
import com.music.dto.ChatSendRequest;
import com.music.service.ChatService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 站内消息
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 会话列表 */
    @GetMapping("/list")
    public Result<?> list(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(chatService.listConversations(userId));
    }

    /** 未读总数 */
    @GetMapping("/unread")
    public Result<?> unread(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(java.util.Map.of("total", chatService.unreadTotal(userId)));
    }

    /** 打开与某个用户的会话 */
    @PostMapping("/open")
    public Result<?> open(@RequestBody ChatOpenRequest req,
                          @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(chatService.openConversation(userId, req.getTargetId()));
    }

    /** 打开平台客服会话 */
    @PostMapping("/service")
    public Result<?> service(@RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(chatService.openService(userId));
    }

    /** 通过商家ID打开会话 */
    @PostMapping("/open-merchant")
    public Result<?> openMerchant(@RequestParam("merchantId") Long merchantId,
                                  @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(chatService.openMerchant(userId, merchantId));
    }

    /** 历史消息 */
    @GetMapping("/messages")
    public Result<?> messages(@RequestParam("conversationId") Long conversationId,
                              @RequestParam(value = "beforeId", required = false) Long beforeId,
                              @RequestParam(value = "afterId", required = false) Long afterId,
                              @RequestParam(value = "size", defaultValue = "20") Integer size,
                              @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(chatService.listMessages(userId, conversationId, beforeId, afterId, size));
    }

    /** 发送消息 */
    @PostMapping("/send")
    public Result<?> send(@RequestBody ChatSendRequest req,
                          @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(chatService.send(userId, req));
    }

    /** 标记已读 */
    @PostMapping("/read")
    public Result<?> read(@RequestBody ChatReadRequest req,
                          @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        chatService.markRead(userId, req.getConversationId());
        return Result.ok((Object) null, "已读");
    }

    /** 搜索可聊天用户（顾客之间发起会话） */
    @GetMapping("/search-user")
    public Result<?> searchUser(@RequestParam("keyword") String keyword,
                                @RequestAttribute(UserContext.USER_ID_ATTR) Long userId) {
        return Result.ok(chatService.searchUsers(userId, keyword));
    }

    /** 聊天图片上传 */
    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new RuntimeException("请选择图片");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("只支持图片文件");
        }
        if (file.getSize() > 5 * 1024 * 1024) throw new RuntimeException("图片不能超过5MB");

        String ext = ".png";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        Path dir = Paths.get(uploadDir, "chat").toAbsolutePath();
        Files.createDirectories(dir);
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        file.transferTo(dir.resolve(filename).toFile());
        return Result.ok("/uploads/chat/" + filename);
    }
}
