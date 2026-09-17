package com.akkc.tensor.web;

import com.akkc.tensor.core.download.task.DownloadTaskService;
import com.akkc.tensor.core.integrity.IntegrityCheckJson;
import com.akkc.tensor.core.integrity.IntegrityCheckService;
import com.akkc.tensor.web.dto.IntegrityCheckResponses.*;
import com.akkc.tensor.web.integrity.IntegrityCheckQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class IntegrityCheckController {
    private static final String CHECKS = "/api/v1/integrity-checks";

    private final IntegrityCheckService service;
    private final IntegrityCheckJson json;
    private final DownloadTaskService downloads;
    private final ObjectMapper mapper;

    public IntegrityCheckController(IntegrityCheckService service, IntegrityCheckJson json,
            DownloadTaskService downloads, ObjectMapper mapper) {
        this.service = Objects.requireNonNull(service);
        this.json = Objects.requireNonNull(json);
        this.downloads = Objects.requireNonNull(downloads);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @PostMapping(value = CHECKS, produces = "application/json", consumes = "application/json")
    public ResponseEntity<Receipt> submit(@RequestParam MultiValueMap<String, String> parameters,
            @RequestBody String body) {
        IntegrityCheckQuery.none(parameters);
        Map<String, Object> request;
        try {
            var node = json.readValue(body);
            if (!node.isObject()) throw IntegrityCheckQuery.invalid();
            request = mapper.convertValue(node, new TypeReference<>() {});
        } catch (IntegrityCheckQuery.BindingException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw IntegrityCheckQuery.invalid();
        }
        var result = service.submit(request);
        var task = result.task();
        String requestId = Objects.requireNonNull(MDC.get(RequestIdFilter.MDC_KEY), "Request ID is unavailable");
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .location(URI.create(CHECKS + "/" + task.checkId()))
                .body(Receipt.from(requestId, task));
    }

    @GetMapping(value = "/api/v1/data-sources/{pluginId}/integrity-capabilities", produces = "application/json")
    public Capability capability(@PathVariable("pluginId") String pluginId,
            @RequestParam MultiValueMap<String, String> parameters) {
        IntegrityCheckQuery.none(parameters);
        return Capability.from(service.capability(IntegrityCheckQuery.pluginId(pluginId)), downloads);
    }

    @GetMapping(value = CHECKS, produces = "application/json")
    public Page<TaskSummary> tasks(@RequestParam MultiValueMap<String, String> parameters) {
        var query = IntegrityCheckQuery.tasks(parameters);
        return Page.from(service.tasks(query.filter(), query.page(), query.pageSize()), TaskSummary::from);
    }

    @GetMapping(value = CHECKS + "/{checkId}", produces = "application/json")
    public Detail detail(@PathVariable("checkId") String checkId,
            @RequestParam MultiValueMap<String, String> parameters) {
        IntegrityCheckQuery.none(parameters);
        return Detail.from(service.progress(IntegrityCheckQuery.checkId(checkId)));
    }

    @GetMapping(value = CHECKS + "/{checkId}/results", produces = "application/json")
    public Page<Result> results(@PathVariable("checkId") String checkId,
            @RequestParam MultiValueMap<String, String> parameters) {
        var id = IntegrityCheckQuery.checkId(checkId);
        var query = IntegrityCheckQuery.results(parameters);
        return Page.from(service.results(id, query.filter(), query.page(), query.pageSize()), Result::from);
    }

    @GetMapping(value = CHECKS + "/{checkId}/issues", produces = "application/json")
    public Page<Issue> issues(@PathVariable("checkId") String checkId,
            @RequestParam MultiValueMap<String, String> parameters) {
        var id = IntegrityCheckQuery.checkId(checkId);
        var query = IntegrityCheckQuery.issues(parameters);
        return Page.from(service.issues(id, query.filter(), query.page(), query.pageSize()), Issue::from);
    }
}
