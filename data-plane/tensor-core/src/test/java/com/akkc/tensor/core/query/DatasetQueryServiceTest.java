package com.akkc.tensor.core.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.akkc.tensor.core.catalog.DatasetCatalog;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.FilterDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.error.ErrorCode;
import com.akkc.tensor.plugin.api.error.TensorException;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataRetrievalFailureException;

class DatasetQueryServiceTest {
    private static final DatasetKey KEY = DatasetKey.of(PluginId.of("query_test"), ApiName.of("daily"));
    private static final QueryCriteria UNFILTERED = new QueryCriteria(null, null, null, null, null, 1, 20);

    @Test
    void reportsMissingAndUnsupportedDatasetsAsMisconfiguredBeforeRepositoryAccess() {
        DatasetCatalog catalog = mock(DatasetCatalog.class);
        GenericQueryRepository repository = mock(GenericQueryRepository.class);
        DatasetQueryService service = new DatasetQueryService(catalog, repository);

        when(catalog.find(KEY)).thenReturn(Optional.empty());
        assertCode(() -> service.query(KEY, UNFILTERED), ErrorCode.DATASET_MISCONFIGURED);

        when(catalog.find(KEY)).thenReturn(Optional.of(definition(List.of("close"))));
        assertCode(() -> service.query(KEY, UNFILTERED), ErrorCode.DATASET_MISCONFIGURED);
        verifyNoInteractions(repository);
    }

    @Test
    void reportsARequestedUndeclaredFilterAsAnInvalidParameter() {
        DatasetCatalog catalog = mock(DatasetCatalog.class);
        GenericQueryRepository repository = mock(GenericQueryRepository.class);
        when(catalog.find(KEY)).thenReturn(Optional.of(definition(List.of())));
        DatasetQueryService service = new DatasetQueryService(catalog, repository);
        QueryCriteria filtered = new QueryCriteria("000001.SZ", null, null, null, null, 1, 20);

        assertCode(() -> service.query(KEY, filtered), ErrorCode.PARAM_INVALID);
        verifyNoInteractions(repository);
    }

    @Test
    void mapsCountAndPageRepositoryFailuresToQueryFailedWithTheirCause() {
        DatasetDefinition definition = definition(List.of());
        DatasetCatalog catalog = mock(DatasetCatalog.class);
        GenericQueryRepository repository = mock(GenericQueryRepository.class);
        when(catalog.find(KEY)).thenReturn(Optional.of(definition));
        DatasetQueryService service = new DatasetQueryService(catalog, repository);

        DataAccessResourceFailureException countFailure = new DataAccessResourceFailureException("count failed");
        when(repository.count(org.mockito.ArgumentMatchers.any())).thenThrow(countFailure);
        assertFailure(service, countFailure);

        org.mockito.Mockito.reset(repository);
        when(repository.count(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        DataRetrievalFailureException pageFailure = new DataRetrievalFailureException("page failed");
        when(repository.query(org.mockito.ArgumentMatchers.eq(definition), org.mockito.ArgumentMatchers.any()))
                .thenThrow(pageFailure);
        assertFailure(service, pageFailure);
    }

    private static void assertFailure(DatasetQueryService service, RuntimeException cause) {
        assertThatThrownBy(() -> service.query(KEY, UNFILTERED))
                .isInstanceOfSatisfying(TensorException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.QUERY_FAILED);
                    assertThat(exception).hasMessage("Dataset query failed").hasCause(cause);
                });
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(
                TensorException.class, exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private static DatasetDefinition definition(List<String> filters) {
        List<String> names = new ArrayList<>(List.of("ts_code"));
        filters.stream().filter(name -> !names.contains(name)).forEach(names::add);
        return new DatasetDefinition(
                KEY, "Daily", "market", QueryMode.trade_date, List.of(), TableName.from(KEY),
                names.stream().map(name -> new ColumnDefinition(
                        name, name, LogicalType.STRING, false, 0, 64, null, null, List.of(), false)).toList(),
                new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("ts_code")),
                filters.stream().map(FilterDefinition::new).toList(), null, 500);
    }
}
