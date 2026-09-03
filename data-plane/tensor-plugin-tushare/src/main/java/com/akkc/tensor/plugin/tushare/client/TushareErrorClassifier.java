package com.akkc.tensor.plugin.tushare.client;

import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.SourceException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;

final class TushareErrorClassifier {
    private static final int MAX_CAUSE_DEPTH = 16;

    private TushareErrorClassifier() {}

    static SourceException classifyHttp(int statusCode) {
        ErrorCode code = switch (statusCode) {
            case 401 -> ErrorCode.SOURCE_AUTH_FAILED;
            case 403 -> ErrorCode.SOURCE_PERMISSION_DENIED;
            case 429 -> ErrorCode.SOURCE_RATE_LIMITED;
            default -> ErrorCode.SOURCE_UNAVAILABLE;
        };
        return failure(code);
    }

    static SourceException classifyBusiness(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains("token")
                || normalized.contains("认证")
                || normalized.contains("用户不存在")) {
            return failure(ErrorCode.SOURCE_AUTH_FAILED);
        }
        if (normalized.contains("每分钟")
                || normalized.contains("每小时")
                || normalized.contains("频率")
                || normalized.contains("限流")) {
            return failure(ErrorCode.SOURCE_RATE_LIMITED);
        }
        if (normalized.contains("权限") || normalized.contains("积分")) {
            return failure(ErrorCode.SOURCE_PERMISSION_DENIED);
        }
        return invalidPayload();
    }

    static SourceException classifyTransport(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    && !(current instanceof HttpConnectTimeoutException)) {
                return failure(ErrorCode.SOURCE_TIMEOUT);
            }
            current = current.getCause();
        }

        current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof NoRouteToHostException
                    || current instanceof HttpConnectTimeoutException) {
                return failure(ErrorCode.SOURCE_NETWORK_ERROR);
            }
            current = current.getCause();
        }
        return failure(ErrorCode.SOURCE_NETWORK_ERROR);
    }

    static SourceException invalidPayload() {
        return failure(ErrorCode.SOURCE_PAYLOAD_INVALID);
    }

    private static SourceException failure(ErrorCode code) {
        String message = switch (code) {
            case SOURCE_AUTH_FAILED -> "Tushare credentials were rejected";
            case SOURCE_PERMISSION_DENIED -> "Tushare API permission is unavailable";
            case SOURCE_RATE_LIMITED -> "Tushare rate limit was reached";
            case SOURCE_UNAVAILABLE -> "Tushare service is unavailable";
            case SOURCE_NETWORK_ERROR -> "Tushare could not be reached";
            case SOURCE_TIMEOUT -> "Tushare response timed out";
            case SOURCE_PAYLOAD_INVALID -> "Tushare returned an invalid payload";
            default -> throw new IllegalArgumentException("code must identify a source failure");
        };
        return new SourceException(code, message);
    }
}
