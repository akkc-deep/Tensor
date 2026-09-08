package com.akkc.tensor.plugin.tushare.calendar;

import com.akkc.tensor.plugin.api.descriptor.ApiDescriptor;
import com.akkc.tensor.plugin.api.download.CalendarDecision;
import com.akkc.tensor.plugin.api.download.CalendarScope;
import com.akkc.tensor.plugin.api.download.DownloadContext;
import com.akkc.tensor.plugin.api.download.DownloadPolicy;
import com.akkc.tensor.plugin.api.error.CalendarUnconfirmedException;
import com.akkc.tensor.plugin.api.error.SourceException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class TushareCalendarProvider {
    private static final Pattern SOURCE_ID = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Map<String, Mapping> FIXED = Map.ofEntries(
            entry("moneyflow", DownloadPolicy.CalendarProfile.C_A,
                    "SSE", "SSE_CALENDAR", "SZSE", "SZSE_CALENDAR"),
            entry("stk_limit", DownloadPolicy.CalendarProfile.C_A,
                    "SSE", "SSE_CALENDAR", "SZSE", "SZSE_CALENDAR", "BSE", "BSE_CALENDAR"),
            entry("slb_len", DownloadPolicy.CalendarProfile.C_S,
                    "CSF_REFINANCING", "CSF_REFINANCING_CALENDAR"),
            entry("slb_sec", DownloadPolicy.CalendarProfile.C_S,
                    "CSF_SECURITIES_REFINANCING", "CSF_SECURITIES_CALENDAR"),
            entry("slb_sec_detail", DownloadPolicy.CalendarProfile.C_S,
                    "CSF_SECURITIES_REFINANCING", "CSF_SECURITIES_CALENDAR"),
            entry("hsgt_top10", DownloadPolicy.CalendarProfile.C_N,
                    "NORTHBOUND_SH", "HKEX_NORTHBOUND_CALENDAR",
                    "NORTHBOUND_SZ", "HKEX_NORTHBOUND_CALENDAR"));

    private final Map<String, CalendarSource> sources;

    public TushareCalendarProvider(Map<String, CalendarSource> sources) {
        Objects.requireNonNull(sources, "sources");
        for (var entry : sources.entrySet()) {
            if (entry.getKey() == null || !SOURCE_ID.matcher(entry.getKey()).matches() || entry.getValue() == null) {
                throw new IllegalArgumentException("Invalid calendar source registration");
            }
        }
        this.sources = Map.copyOf(sources);
    }

    public CalendarDecision confirm(ApiDescriptor api, CalendarScope scope, DownloadContext context) {
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(context, "context").checkServerState();
        var policy = api.downloadPolicy();
        if (policy.mode() != DownloadPolicy.Mode.TRADE_DATE_RANGE
                || policy.calendarEvidenceStatus() != DownloadPolicy.CalendarEvidenceStatus.DOCUMENTED) {
            throw new CalendarUnconfirmedException();
        }

        Mapping mapping = mapping(api, scope);
        var grouped = new TreeMap<String, Set<String>>();
        mapping.identitiesToSources().forEach((identity, source) -> grouped.merge(
                source, Set.of(identity), (left, right) -> {
                    var combined = new java.util.HashSet<>(left);
                    combined.addAll(right);
                    return Set.copyOf(combined);
                }));
        if (!sources.keySet().containsAll(grouped.keySet())) throw new CalendarUnconfirmedException();

        Set<LocalDate> dates = Set.copyOf(scope.dates());
        var calendars = new HashMap<String, Map<LocalDate, Boolean>>();
        for (var request : grouped.entrySet()) {
            Set<String> identities = Set.copyOf(request.getValue());
            context.checkServerState();
            CalendarSource.CalendarData data;
            try {
                data = sources.get(request.getKey()).fetch(identities, dates);
            } catch (CalendarUnconfirmedException exception) {
                throw exception;
            } catch (SourceException exception) {
                throw new CalendarUnconfirmedException();
            }
            context.checkServerState();
            validate(request.getKey(), identities, dates, data, calendars);
        }
        if (!calendars.keySet().equals(mapping.identitiesToSources().keySet())) {
            throw new CalendarUnconfirmedException();
        }
        var decision = new CalendarDecision(scope, calendars);
        context.checkServerState();
        return decision;
    }

    private static Mapping mapping(ApiDescriptor api, CalendarScope scope) {
        String name = api.apiName().value();
        if ("margin".equals(name)) {
            if (api.downloadPolicy().calendarProfile() != DownloadPolicy.CalendarProfile.C_M) {
                throw new CalendarUnconfirmedException();
            }
            Object exchange = scope.publicParams().get("exchange_id");
            if (!(exchange instanceof String value) || !Set.of("SSE", "SZSE", "BSE").contains(value)) {
                throw new CalendarUnconfirmedException();
            }
            return new Mapping(Map.of(value, value + "_CALENDAR"));
        }
        Mapping mapping = FIXED.get(name);
        if (mapping == null || api.downloadPolicy().calendarProfile() != mapping.profile()) {
            throw new CalendarUnconfirmedException();
        }
        return mapping;
    }

    private static void validate(String expectedSource, Set<String> identities, Set<LocalDate> dates,
            CalendarSource.CalendarData data, Map<String, Map<LocalDate, Boolean>> calendars) {
        if (data == null || !expectedSource.equals(data.sourceId()) || data.effectiveFrom() == null
                || data.effectiveThrough() == null || data.effectiveFrom().isAfter(data.effectiveThrough())
                || dates.stream().anyMatch(date -> date.isBefore(data.effectiveFrom()) || date.isAfter(data.effectiveThrough()))
                || !data.revisionsConfirmed() || data.rows() == null || data.rows().isEmpty()) {
            throw new CalendarUnconfirmedException();
        }
        var sourceCalendars = new HashMap<String, Map<LocalDate, Boolean>>();
        identities.forEach(identity -> sourceCalendars.put(identity, new LinkedHashMap<>()));
        for (var row : data.rows()) {
            if (row == null || row.identity() == null || !identities.contains(row.identity()) || row.date() == null
                    || !("0".equals(row.isOpen()) || "1".equals(row.isOpen()))) {
                throw new CalendarUnconfirmedException();
            }
            if (!dates.contains(row.date())) continue;
            var identityDates = sourceCalendars.get(row.identity());
            Boolean old = identityDates.putIfAbsent(row.date(), "1".equals(row.isOpen()));
            if (old != null && old != "1".equals(row.isOpen())) throw new CalendarUnconfirmedException();
        }
        if (sourceCalendars.values().stream().anyMatch(values -> !values.keySet().equals(dates))) {
            throw new CalendarUnconfirmedException();
        }
        calendars.putAll(sourceCalendars);
    }

    private static Map.Entry<String, Mapping> entry(String api, DownloadPolicy.CalendarProfile profile,
            String... identitySources) {
        var values = new HashMap<String, String>();
        for (int i = 0; i < identitySources.length; i += 2) values.put(identitySources[i], identitySources[i + 1]);
        return Map.entry(api, new Mapping(profile, Map.copyOf(values)));
    }

    private record Mapping(DownloadPolicy.CalendarProfile profile, Map<String, String> identitiesToSources) {
        private Mapping(Map<String, String> identitiesToSources) { this(null, identitiesToSources); }
    }
}
