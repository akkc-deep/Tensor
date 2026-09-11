package com.akkc.tensor.plugin.tushare.batch;

import static com.akkc.tensor.plugin.api.error.ErrorCode.*;
import static com.akkc.tensor.plugin.tushare.batch.TushareBatchPolicies.*;

import com.akkc.tensor.plugin.api.download.DownloadEnvelope;
import com.akkc.tensor.plugin.api.download.batch.DateRange;
import com.akkc.tensor.plugin.api.model.ApiName;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;

/** Validates complete natural-day coverage before treating open dates as planning evidence. */
final class TushareTradeCalendar {
    private TushareTradeCalendar() {}

    static List<LocalDate> openDays(DateRange range, String exchange, DownloadEnvelope envelope) {
        validateEnvelope(new ApiName("trade_cal"), List.of("exchange", "cal_date", "is_open", "pretrade_date"), envelope);
        if (range == null || exchange == null || !envelope.params().equals(Map.of("exchange", exchange,
                "start_date", format(range.start()), "end_date", format(range.end())))) throw failure(SOURCE_RANGE_MISMATCH);
        var days = new TreeMap<LocalDate, Boolean>();
        boolean duplicate = false;
        for (List<Object> row : envelope.data()) {
            if (!exchange.equals(row.get(0))) throw failure(SOURCE_RANGE_MISMATCH);
            LocalDate date = date(row.get(1), SOURCE_PAYLOAD_INVALID);
            within(date, range);
            boolean open = isOpen(row.get(2));
            if (days.put(date, open) != null) duplicate = true;
        }
        if (duplicate) throw failure(BATCH_COMPLETENESS_UNCONFIRMED);
        for (LocalDate date = range.start();; date = date.plusDays(1)) {
            if (!days.containsKey(date)) throw failure(BATCH_COMPLETENESS_UNCONFIRMED);
            if (date.equals(range.end())) break;
        }
        return days.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
    }

    static boolean isOpen(Object value) {
        if ("0".equals(value)) return false;
        if ("1".equals(value)) return true;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof BigInteger || value instanceof BigDecimal) {
            try {
                int flag = new BigDecimal(value.toString()).intValueExact();
                if (flag == 0 || flag == 1) return flag == 1;
            } catch (ArithmeticException ignored) {
                // Nonintegral and overflowing numbers are invalid source flags.
            }
        }
        throw failure(SOURCE_PAYLOAD_INVALID);
    }
}
