package com.modelcore.provider;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 上游 AI 供应商客户端接口
 * <p>
 * 定义统一的上游调用契约，所有供应商实现此接口。
 * 基于 JDK 21 虚拟线程，全部采用同步阻塞调用，无需响应式编程。
 * </p>
 */
public interface ProviderClient {

    /**
     * 获取供应商名称
     */
    String getName();

    /**
     * 供应商优先级（数值越小优先级越高，用于路由排序）
     */
    int getPriority();

    /**
     * 每个输入 Token 的美元价格
     */
    BigDecimal getInputPricePerToken();

    /**
     * 每个输出 Token 的美元价格
     */
    BigDecimal getOutputPricePerToken();

    /**
     * 流式调用（SSE）
     * <p>
     * 在虚拟线程中同步读取上游 SSE 流，每收到一行数据通过 onData 回调推送给调用方。
     * 流结束时调用 onComplete，发生错误时调用 onError。
     * </p>
     *
     * @param requestBody 请求体（OpenAI 兼容格式）
     * @param onData      收到每行 SSE 数据时的回调
     * @param onComplete  流正常结束时的回调
     * @param onError     发生异常时的回调
     */
    void streamChat(Map<String, Object> requestBody,
                    Consumer<String> onData,
                    Runnable onComplete,
                    Consumer<Throwable> onError);

    /**
     * 非流式调用（同步阻塞）
     *
     * @param requestBody 请求体（OpenAI 兼容格式）
     * @return 完整的 JSON 响应字符串
     */
    String chat(Map<String, Object> requestBody);

    /**
     * 向量嵌入调用（同步阻塞）
     *
     * @param requestBody 请求体（OpenAI 兼容格式，含 input 和 model 字段）
     * @return 完整的 JSON 响应字符串
     */
    String embeddings(Map<String, Object> requestBody);

    /**
     * 健康检查（同步阻塞）
     *
     * @return true 表示供应商可用
     */
    boolean healthCheck();
}
