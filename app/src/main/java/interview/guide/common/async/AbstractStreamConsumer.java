package interview.guide.common.async;

import interview.guide.common.constant.AsyncTaskStreamConstants;
import interview.guide.infrastructure.redis.RedisService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.AutoClaimResult;
import org.redisson.api.stream.StreamMessageId;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Stream 消费者模板基类。
 * <p>
 * 将消费循环、ACK、重试与生命周期管理收敛到统一模板，子类仅关注业务处理逻辑。
 * <p>
 * 并发消费模型（Java 21 虚拟线程，适合 AI 调用/DB 等 I/O 密集型场景）：
 * <ul>
 *     <li>单个平台线程负责阻塞读取 Stream（消费循环保持不变），保证读取与背压可控</li>
 *     <li>每条消息提交到独立的虚拟线程并发处理，处理全程（含 ACK、重试入队、状态落库）都在自己的虚拟线程任务内完成</li>
 *     <li>通过 {@link Semaphore} 限制同时处理的任务数，控制对 AI 接口/数据库的压力</li>
 *     <li>处理失败自动重试（XADD 带 retryCount+1 重新入队并 ACK 原消息），超过 {@link AsyncTaskStreamConstants#MAX_RETRY_COUNT} 次落库标记 FAILED</li>
 *     <li>支持通过 {@link #reclaimPendingMessages()} 回收消费者宕机后滞留在 PEL 中的消息</li>
 * </ul>
 * 消息处理语义为 at-least-once：崩溃前已处理但未 ACK 的消息在回收后可能被重复处理，业务侧需保证幂等。
 */
@Slf4j
public abstract class AbstractStreamConsumer<T> {

    private final RedisService redisService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    /**
     * 同时处理任务的最大并发数，控制对 AI 接口/DB 的压力
     */
    private final int maxConcurrency;
    /**
     * 正在处理中的消息 ID（PEL 回收去重：避免回收任务与正常消费/上一轮回收对同一消息重复分发）
     */
    private final Set<StreamMessageId> inFlightMessages = ConcurrentHashMap.newKeySet();
    /**
     * 消费循环线程池（单平台守护线程，阻塞读取 Stream）
     */
    private ExecutorService consumerLoopExecutor;
    /**
     * 消息处理线程池（虚拟线程，每条消息一个任务）
     */
    private ExecutorService processExecutor;
    /**
     * 并发处理信号量（许可数 = maxConcurrency）
     */
    private Semaphore processSemaphore;
    private String consumerName;

    protected AbstractStreamConsumer(RedisService redisService) {
        this(redisService, AsyncTaskStreamConstants.DEFAULT_MAX_CONCURRENCY);
    }

    protected AbstractStreamConsumer(RedisService redisService, int maxConcurrency) {
        this.redisService = redisService;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }

    @PostConstruct
    public void init() {
        this.consumerName = consumerPrefix() + UUID.randomUUID().toString().substring(0, 8);

        try {
            redisService.createStreamGroup(streamKey(), groupName());
            log.info("Redis Stream 消费者组已创建或已存在: {}", groupName());
        } catch (Exception e) {
            log.warn("创建消费者组时发生异常（可能已存在）: {}", e.getMessage());
        }

        // 消费循环线程：单条平台守护线程负责阻塞读取并分发
        this.consumerLoopExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName());
            t.setDaemon(true);
            return t;
        });
        // 每条消息一个虚拟线程，适合 AI 调用/DB 等 I/O 密集型处理
        this.processExecutor = Executors.newVirtualThreadPerTaskExecutor();
        // 信号量限制同时处理的任务数；许可不足时消费线程阻塞等待，形成对读取速率的背压
        this.processSemaphore = new Semaphore(maxConcurrency);

        running.set(true);
        consumerLoopExecutor.submit(this::consumeLoop);
        log.info("{}消费者已启动: consumerName={}, maxConcurrency={}",
            taskDisplayName(), consumerName, maxConcurrency);
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (consumerLoopExecutor != null) {
            // 中断消费循环线程，使其尽快从阻塞读取中退出
            consumerLoopExecutor.shutdownNow();
        }
        if (processExecutor != null) {
            processExecutor.shutdown();
        }
        log.info("{}消费者已关闭: consumerName={}", taskDisplayName(), consumerName);
    }

    private void consumeLoop() {
        while (running.get()) {
            try {
                redisService.streamConsumeMessages(
                    streamKey(),
                    groupName(),
                    consumerName,
                    AsyncTaskStreamConstants.BATCH_SIZE,
                    AsyncTaskStreamConstants.POLL_INTERVAL_MS,
                    this::dispatchMessage
                );
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted() || !running.get()) {
                    log.info("消费者线程被中断");
                    break;
                }
                log.error("消费消息时发生错误: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 分发一条消息到虚拟线程处理（阻塞式获取并发许可，用于消费循环，形成背压）
     */
    private void dispatchMessage(StreamMessageId messageId, Map<String, String> data) {
        submitProcessTask(messageId, data, true, false);
    }

    /**
     * 分发一条回收消息到虚拟线程处理（非阻塞，许可不足时跳过，消息留在 PEL 等待下一轮回收）
     */
    private boolean tryDispatchMessage(StreamMessageId messageId, Map<String, String> data) {
        return submitProcessTask(messageId, data, false, true);
    }

    /**
     * 提交消息处理任务。
     * <p>
     * 通过 in-flight 去重避免同一消息被并发重复处理，通过信号量限制并发数；
     * 处理任务（含 ACK、重试 XADD、markCompleted/markFailed）整体在虚拟线程内执行。
     *
     * @param blockOnPermit true 表示阻塞等待并发许可（消费循环背压）；false 表示许可不足时直接跳过（回收场景）
     * @param claimed       是否为 PEL 回收的消息（回收消息需按回收语义处理）
     * @return 是否成功提交处理任务
     */
    private boolean submitProcessTask(StreamMessageId messageId, Map<String, String> data,
                                      boolean blockOnPermit, boolean claimed) {
        // PEL 回收可能与正常消费/上一轮回收撞车，同一条消息同时只允许一个处理任务
        if (!inFlightMessages.add(messageId)) {
            log.debug("消息正在处理中，跳过重复分发: messageId={}", messageId);
            return false;
        }

        boolean acquired = false;
        if (blockOnPermit) {
            try {
                processSemaphore.acquire();
                acquired = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            acquired = processSemaphore.tryAcquire();
            if (!acquired) {
                log.debug("并发已满，本轮跳过消息处理: messageId={}", messageId);
            }
        }
        if (!acquired) {
            inFlightMessages.remove(messageId);
            return false;
        }

        try {
            processExecutor.submit(() -> {
                try {
                    if (claimed) {
                        processClaimedMessage(messageId, data);
                    } else {
                        processMessage(messageId, data);
                    }
                } catch (Exception e) {
                    log.error("处理{}消息任务异常: messageId={}, error={}",
                        taskDisplayName(), messageId, e.getMessage(), e);
                } finally {
                    processSemaphore.release();
                    inFlightMessages.remove(messageId);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            // 消费者已关闭：释放许可，消息留在 PEL，由下次启动后的 Pending 回收任务兜底
            processSemaphore.release();
            inFlightMessages.remove(messageId);
            log.warn("消息处理任务提交被拒绝（消费者关闭中）: messageId={}", messageId);
            return false;
        }
    }

    /**
     * 处理一条消息（正常消费路径）：成功则标记完成并 ACK；失败按重试语义处理（retryCount&lt;3 时
     * XADD 带 retryCount+1 的新消息并 ACK 原消息，超限则落库 FAILED）后 ACK。
     */
    private void processMessage(StreamMessageId messageId, Map<String, String> data) {
        T payload = parsePayloadSafely(messageId, data);
        if (payload == null) {
            ackMessage(messageId);
            return;
        }

        int retryCount = parseRetryCount(data);
        log.info("开始处理{}任务: {}, messageId={}, retryCount={}",
            taskDisplayName(), payloadIdentifier(payload), messageId, retryCount);

        try {
            markProcessing(payload);
            processBusiness(payload);
            markCompleted(payload);
            ackMessage(messageId);
            log.info("{}任务完成: {}", taskDisplayName(), payloadIdentifier(payload));
        } catch (Exception e) {
            log.error("{}任务失败: {}, error={}", taskDisplayName(), payloadIdentifier(payload), e.getMessage(), e);
            if (retryCount < AsyncTaskStreamConstants.MAX_RETRY_COUNT && shouldRetry(e)) {
                retryMessage(payload, retryCount + 1);
            } else {
                markFailed(payload, truncateError(
                    taskDisplayName() + "失败(已重试" + retryCount + "次): " + e.getMessage()
                ));
            }
            ackMessage(messageId);
        }
    }

    /**
     * 处理一条 PEL 回收的消息：retryCount 未超限时走正常 {@link #processMessage} 重试；
     * 已超限（上次处理中宕机导致未 ACK）则直接落库 FAILED 并 ACK，避免无限回收。
     */
    private void processClaimedMessage(StreamMessageId messageId, Map<String, String> data) {
        T payload = parsePayloadSafely(messageId, data);
        if (payload == null) {
            ackMessage(messageId);
            return;
        }

        int retryCount = parseRetryCount(data);
        if (retryCount >= AsyncTaskStreamConstants.MAX_RETRY_COUNT) {
            log.error("{}回收消息已超过最大重试次数，标记失败: {}, messageId={}, retryCount={}",
                taskDisplayName(), payloadIdentifier(payload), messageId, retryCount);
            markFailed(payload, truncateError(
                taskDisplayName() + "失败(已重试" + retryCount + "次): 消息回收后超过最大重试次数"
            ));
            ackMessage(messageId);
            return;
        }
        processMessage(messageId, data);
    }

    /**
     * 解析消息体。解析异常视为毒消息（重试也不会成功），记录日志后 ACK 丢弃，避免永久滞留 PEL。
     */
    private T parsePayloadSafely(StreamMessageId messageId, Map<String, String> data) {
        try {
            return parsePayload(messageId, data);
        } catch (Exception e) {
            log.error("{}消息解析失败，丢弃消息: messageId={}, error={}",
                taskDisplayName(), messageId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 回收 PEL 中滞留的消息（供定时任务调用）。
     * <p>
     * 消费者宕机/重启后，已投递但未 ACK 的消息会永久滞留在 PEL 中（正常消费只读 neverDelivered 消息）。
     * 这里通过 XAUTOCLAIM 把 idle 超过 {@link AsyncTaskStreamConstants#PENDING_IDLE_THRESHOLD_MS} 的消息
     * 认领到当前消费者名下重新处理；并发许可不足或消息处理中时跳过，剩余消息留给下一轮回收。
     */
    protected void reclaimPendingMessages() {
        if (!running.get()) {
            return;
        }
        try {
            StreamMessageId startId = StreamMessageId.MIN;
            for (int round = 0; round < AsyncTaskStreamConstants.PENDING_RECLAIM_MAX_ROUNDS && running.get(); round++) {
                AutoClaimResult<String, String> result = redisService.streamAutoClaim(
                    streamKey(),
                    groupName(),
                    consumerName,
                    AsyncTaskStreamConstants.PENDING_IDLE_THRESHOLD_MS,
                    startId,
                    AsyncTaskStreamConstants.BATCH_SIZE
                );

                Map<StreamMessageId, Map<String, String>> messages = result.getMessages();
                if (messages == null || messages.isEmpty()) {
                    return;
                }

                for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                    if (!tryDispatchMessage(entry.getKey(), entry.getValue())) {
                        // 并发已满或消息正在处理中，本轮结束，剩余消息留给下一轮回收
                        return;
                    }
                }

                StreamMessageId nextId = result.getNextId();
                if (nextId == null || StreamMessageId.MIN.equals(nextId)) {
                    // 游标已扫描到末尾
                    return;
                }
                startId = nextId;
            }
        } catch (Exception e) {
            log.warn("{}回收 PEL 滞留消息失败: {}", taskDisplayName(), e.getMessage(), e);
        }
    }

    protected int parseRetryCount(Map<String, String> data) {
        try {
            return Integer.parseInt(data.getOrDefault(AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Determines whether a failed task may become successful on a later delivery.
     * Provider-side authentication and malformed-request failures are permanent;
     * retrying them only consumes quota and keeps the user waiting. 429 remains retryable.
     */
    protected boolean shouldRetry(Exception error) {
        String message = error.getMessage();
        if (message == null) {
            return true;
        }
        String normalized = message.toLowerCase();
        return !(normalized.contains("http 400")
            || normalized.contains("http 401")
            || normalized.contains("http 403")
            || normalized.contains("http 404")
            || normalized.contains("invalid_api_key"));
    }

    protected String truncateError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    private void ackMessage(StreamMessageId messageId) {
        try {
            redisService.streamAck(streamKey(), groupName(), messageId);
        } catch (Exception e) {
            log.error("确认消息失败: messageId={}, error={}", messageId, e.getMessage(), e);
        }
    }

    protected RedisService redisService() {
        return redisService;
    }

    protected abstract String taskDisplayName();

    protected abstract String streamKey();

    protected abstract String groupName();

    protected abstract String consumerPrefix();

    protected abstract String threadName();

    protected abstract T parsePayload(StreamMessageId messageId, Map<String, String> data);

    protected abstract String payloadIdentifier(T payload);

    protected abstract void markProcessing(T payload);

    protected abstract void processBusiness(T payload);

    protected abstract void markCompleted(T payload);

    protected abstract void markFailed(T payload, String error);

    protected abstract void retryMessage(T payload, int retryCount);
}
