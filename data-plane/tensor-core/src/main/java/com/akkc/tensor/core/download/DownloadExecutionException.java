package com.akkc.tensor.core.download;

import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import java.util.Objects;

public final class DownloadExecutionException extends TensorException {
    private final DownloadExecutionResult downloadResult;
    public DownloadExecutionException(ErrorCode code, DownloadExecutionResult downloadResult) {
        super(code, message(code));
        this.downloadResult = Objects.requireNonNull(downloadResult, "downloadResult");
        if (downloadResult.outcome() != DownloadExecutionResult.Outcome.UNCONFIRMED) {
            throw new IllegalArgumentException("Stopped execution requires an unconfirmed result");
        }
    }
    public DownloadExecutionResult downloadResult() { return downloadResult; }
    private static String message(ErrorCode code) {
        return switch (Objects.requireNonNull(code, "code")) {
            case PERSISTENCE_FAILED -> "存储执行异常，已停止";
            case TASK_RECORD_SAVE_UNCONFIRMED -> "失败记录保存未确认，已停止";
            case COMMIT_UNCONFIRMED -> "当前范围提交结果未确认，已停止";
            case INTERNAL_ERROR -> "执行发生内部异常，已停止";
            default -> throw new IllegalArgumentException("Unsupported execution stop code");
        };
    }
}
