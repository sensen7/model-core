package com.modelcore.controller;

import com.modelcore.exception.ChatProxyException;
import com.modelcore.security.ApiKeyPrincipal;
import com.modelcore.service.ChatProxyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI 代理控制器（纯接口层）
 * <p>
 * 对外暴露 OpenAI 兼容的 /v1/chat/completions 和 /v1/embeddings 接口。
 * 仅负责接收请求、解析参数、调用 {@link ChatProxyService}、返回响应。
 * </p>
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "AI 接口", description = "OpenAI 兼容的聊天补全和向量嵌入接口")
public class ChatProxyController {

    private final ChatProxyService chatProxyService;

    /**
     * 聊天补全接口（OpenAI 兼容格式）
     */
    @PostMapping("/chat/completions")
    public Object chatCompletions(@RequestBody Map<String, Object> requestBody,
                                  @AuthenticationPrincipal ApiKeyPrincipal principal) {
        Boolean stream = (Boolean) requestBody.getOrDefault("stream", false);
        if (Boolean.TRUE.equals(stream)) {
            return streamResponse(requestBody, principal);
        } else {
            return nonStreamResponse(requestBody, principal);
        }
    }

    /**
     * 向量嵌入接口（OpenAI 兼容格式）
     */
    @PostMapping("/embeddings")
    public ResponseEntity<String> embeddings(@RequestBody Map<String, Object> requestBody,
                                             @AuthenticationPrincipal ApiKeyPrincipal principal) {
        try {
            chatProxyService.preCheck(principal);
            String responseJson = chatProxyService.embeddings(requestBody, principal);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(responseJson);
        } catch (ChatProxyException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(chatProxyService.errorJson(e.getMessage()));
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 非流式响应：前置检查 → 转发 → 返回结果
     */
    private ResponseEntity<String> nonStreamResponse(Map<String, Object> requestBody,
                                                     ApiKeyPrincipal principal) {
        try {
            chatProxyService.preCheck(principal);
            String responseJson = chatProxyService.chat(requestBody, principal);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(responseJson);
        } catch (ChatProxyException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(chatProxyService.errorJson(e.getMessage()));
        }
    }

    /**
     * 流式响应：主线程执行前置检查，失败直接返回 HTTP 错误码；
     * 通过后创建 SseEmitter 交给 Service 在虚拟线程中推送数据。
     */
    private Object streamResponse(Map<String, Object> requestBody,
                                  ApiKeyPrincipal principal) {
        try {
            chatProxyService.preCheck(principal);
        } catch (ChatProxyException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(chatProxyService.errorJson(e.getMessage()));
        }

        SseEmitter emitter = new SseEmitter(300_000L);
        chatProxyService.streamChat(requestBody, principal, emitter);
        return emitter;
    }
}
