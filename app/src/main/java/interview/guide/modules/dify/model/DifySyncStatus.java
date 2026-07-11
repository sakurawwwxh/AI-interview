package interview.guide.modules.dify.model;

/**
 * Dify 同步状态枚举
 */
public enum DifySyncStatus {
    /** 待同步 */
    PENDING,
    /** 已同步 */
    SYNCED,
    /** 同步成功（用于日志记录） */
    SUCCESS,
    /** 同步失败 */
    FAILED
}
