package com.akkc.tensor.plugin.tushare.batch;

import static com.akkc.tensor.plugin.tushare.batch.TushareBatchPoliciesTest.*;
import static org.assertj.core.api.Assertions.*;

import com.akkc.tensor.plugin.api.download.*;
import com.akkc.tensor.plugin.api.download.batch.BatchAssessment;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.model.PluginId;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class TushareTradeCalendarTest {
    static List<Object> day(String date,Object open) { return Arrays.asList("SZSE",date,open,"20200101"); }
    static DownloadEnvelope calendar(List<List<Object>> rows) { return envelope("trade_cal",params("trade_cal"),rows); }

    @Test void sortsOnlyAfterFullCalendarCoverageAndHonorsSourceIncludingFridayClosures() {
        var rows=List.of(day("20240301",0),day("20240229",new BigDecimal("1.0")),day("20240228","1"));
        var days=TushareTradeCalendar.openDays(RANGE,"SZSE",calendar(rows));
        assertThat(days).containsExactly(LocalDate.of(2024,2,28),LocalDate.of(2024,2,29));
        assertThatThrownBy(() -> days.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(TushareTradeCalendar.openDays(RANGE,"SZSE",calendar(List.of(day("20240228",0),day("20240229",0),day("20240301",0))))).isEmpty();
        assertThat(verified("trade_cal").assess(api("trade_cal"),RANGE,calendar(rows))).isEqualTo(BatchAssessment.COMPLETE);
    }

    @Test void missingDuplicateAndOpenOnlyCalendarsNeverBecomePartialPlans() {
        for(var rows:List.<List<List<Object>>>of(List.of(),List.of(day("20240228",1)),List.of(day("20240228",1),day("20240228",0),day("20240301",1)),List.of(day("20240228",1),day("20240229",1),day("20240301",1),day("20240301",1)))) {
            code(() -> TushareTradeCalendar.openDays(RANGE,"SZSE",calendar(rows)),ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
            assertThat(production().assess(api("trade_cal"),RANGE,calendar(rows))).isEqualTo(BatchAssessment.UNKNOWN);
            code(() -> verified("trade_cal").assess(api("trade_cal"),RANGE,calendar(rows)),ErrorCode.BATCH_COMPLETENESS_UNCONFIRMED);
        }
    }

    @Test void flagsAcceptOnlyExactBinaryStringsAndLosslessJsonIntegers() {
        for(Object open:List.of("0","1",0,1,0L,1L,BigInteger.ONE,new BigDecimal("0.0"),new BigDecimal("1.000"))) {
            var result=TushareTradeCalendar.openDays(range("2024-02-28","2024-02-28"),"SZSE",envelope("trade_cal",Map.of("exchange","SZSE","start_date","20240228","end_date","20240228"),List.of(day("20240228",open))));
            assertThat(result.size()).isEqualTo(new BigDecimal(open.toString()).intValue());
        }
        for(Object bad:Arrays.asList(null,true,false," 1","1 ","01","1.0",2,-1,new BigDecimal("0.1"),1.0d)) {
            code(() -> TushareTradeCalendar.openDays(RANGE,"SZSE",calendar(List.of(day("20240228",bad)))),ErrorCode.SOURCE_PAYLOAD_INVALID);
            code(() -> production().assess(api("trade_cal"),RANGE,calendar(List.of(day("20240228",bad)))),ErrorCode.SOURCE_PAYLOAD_INVALID);
        }
    }

    @Test void validatesIdentityFieldsSnapshotDatesAndExchangeBeforeCoverage() {
        for(Object bad:Arrays.asList(null,20240228,"20240230","20240228suffix")) {
            var row=new ArrayList<>(day("20240228",1));row.set(1,bad);
            code(() -> TushareTradeCalendar.openDays(RANGE,"SZSE",calendar(List.of(row))),ErrorCode.SOURCE_PAYLOAD_INVALID);
        }
        code(() -> TushareTradeCalendar.openDays(RANGE,"SZSE",calendar(List.of(day("20240302",1)))),ErrorCode.SOURCE_RANGE_MISMATCH);
        var wrong=new ArrayList<>(day("20240228",1));wrong.set(0,"SSE");
        code(() -> TushareTradeCalendar.openDays(RANGE,"SZSE",calendar(List.of(wrong))),ErrorCode.SOURCE_RANGE_MISMATCH);
        code(() -> production().assess(api("trade_cal"),RANGE,calendar(List.of(wrong))),ErrorCode.SOURCE_RANGE_MISMATCH);
        code(() -> TushareTradeCalendar.openDays(RANGE,"SSE",calendar(List.of())),ErrorCode.SOURCE_RANGE_MISMATCH);
        code(() -> TushareTradeCalendar.openDays(RANGE,"SZSE",new DownloadEnvelope(new PluginId("wrong_plugin"),api("trade_cal"),params("trade_cal"),List.of("exchange","cal_date","is_open","pretrade_date"),0,List.of(),DownloadStatus.SUCCESS,null)),ErrorCode.SOURCE_RANGE_MISMATCH);
        code(() -> TushareTradeCalendar.openDays(RANGE,"SZSE",envelope("daily",params("trade_cal"),List.of())),ErrorCode.SOURCE_RANGE_MISMATCH);
        code(() -> TushareTradeCalendar.openDays(RANGE,"SZSE",new DownloadEnvelope(new PluginId("tushare_pro"),api("trade_cal"),params("trade_cal"),List.of("exchange","cal_date","is_open"),0,List.of(),DownloadStatus.SUCCESS,null)),ErrorCode.SOURCE_PAYLOAD_INVALID);
        code(() -> TushareTradeCalendar.openDays(RANGE,"SZSE",new DownloadEnvelope(new PluginId("tushare_pro"),api("trade_cal"),params("trade_cal"),List.of(),0,List.of(),DownloadStatus.FAILURE,"secret")),ErrorCode.SOURCE_PAYLOAD_INVALID);
    }
}
