package com.akkc.tensor.core.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.akkc.tensor.plugin.api.dataset.BusinessKeyDefinition;
import com.akkc.tensor.plugin.api.dataset.BusinessKeyMode;
import com.akkc.tensor.plugin.api.dataset.ColumnDefinition;
import com.akkc.tensor.plugin.api.dataset.DatasetDefinition;
import com.akkc.tensor.plugin.api.dataset.LogicalType;
import com.akkc.tensor.plugin.api.descriptor.QueryMode;
import com.akkc.tensor.plugin.api.model.ApiName;
import com.akkc.tensor.plugin.api.model.DatasetKey;
import com.akkc.tensor.plugin.api.model.PluginId;
import com.akkc.tensor.plugin.api.model.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BusinessContentCodecTest {
    private final BusinessContentCodec codec = new BusinessContentCodec();

    @Test
    void encodesVersionTypesNullsUtf8LengthsAndCanonicalValuesInDefinitionOrder() {
        DatasetDefinition definition = definition();
        Map<String, Object> row = row();

        byte[] encoded = codec.encode(definition, row);

        assertThat(HexFormat.of().formatHex(encoded)).isEqualTo(
                "0000000100000006"
                + "000000026964010100000007e4b8adf09f9982"
                + "000000046d656d6f0201000000022020"
                + "0000000a74726164655f6461746504010000000a323032362d30392d3033"
                + "000000056d6f6e7468050100000007323032362d3039"
                + "00000005636f756e740601000000142d39323233333732303336383534373735383038"
                + "00000006616d6f756e74070000000c00000002010000000c3132333435363738392e3130");
        assertThat(codec.sha256(encoded))
                .isEqualTo("b6fda30e68353acc2f917479bf31a077f107d2f6eef5e08f4e7954aa19a63a4a");
        assertThat(codec.sha256("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void ignoresMetadataAndMapOrderButDistinguishesNullEmptyTextAndBusinessChanges() {
        DatasetDefinition definition = definition();
        Map<String, Object> first = row();
        first.put("ingested_at", "different");
        first.put("source_plugin", "different");
        first.put("source_api", "different");
        first.put("business_key", "different");
        Map<String, Object> reordered = new LinkedHashMap<>();
        List.copyOf(first.entrySet()).reversed().forEach(entry -> reordered.put(entry.getKey(), entry.getValue()));

        assertThat(codec.encode(definition, reordered)).isEqualTo(codec.encode(definition, first));
        Map<String, Object> changedMetadata = new LinkedHashMap<>(first);
        changedMetadata.put("ingested_at", "another-time");
        changedMetadata.put("source_plugin", "another-plugin");
        changedMetadata.put("source_api", "another-api");
        changedMetadata.put("business_key", "another-key");
        assertThat(codec.encode(definition, changedMetadata)).isEqualTo(codec.encode(definition, first));

        Map<String, Object> changed = new LinkedHashMap<>(first);
        changed.put("amount", new BigDecimal("123456789.11"));
        assertThat(codec.encode(definition, changed)).isNotEqualTo(codec.encode(definition, first));

        DatasetDefinition textDefinition = smallDefinition();
        Map<String, Object> nullText = new LinkedHashMap<>();
        nullText.put("id", "x");
        nullText.put("memo", null);
        assertThat(codec.encode(textDefinition, nullText))
                .isNotEqualTo(codec.encode(textDefinition, Map.of("id", "x", "memo", "")));
    }

    @Test
    void rejectsMissingColumnsWrongAdaptedTypesFloatingPointAndNonCanonicalDecimals() {
        DatasetDefinition definition = definition();
        Map<String, Object> missing = row();
        missing.remove("memo");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.encode(definition, missing))
                .withMessage("Missing adapted business column");

        for (Object badAmount : List.of(0.1d, new BigDecimal("1.234"), new BigDecimal("12345678901.20"))) {
            Map<String, Object> invalid = row();
            invalid.put("amount", badAmount);
            assertThatIllegalArgumentException().isThrownBy(() -> codec.encode(definition, invalid))
                    .withMessage("Invalid adapted business value");
        }
        Map<String, Object> badMonth = row();
        badMonth.put("month", "2026-09");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.encode(definition, badMonth))
                .withMessage("Invalid adapted business value");
        assertThatNullPointerException().isThrownBy(() -> codec.encode(null, row()));
        assertThatNullPointerException().isThrownBy(() -> codec.encode(definition, null));
        assertThatNullPointerException().isThrownBy(() -> codec.sha256(null));

        Map<String, Object> requiredNull = row();
        requiredNull.put("id", null);
        assertThatIllegalArgumentException().isThrownBy(() -> codec.encode(definition, requiredNull))
                .withMessage("Invalid adapted business value");
        Map<String, Object> tooLong = row();
        tooLong.put("id", "x".repeat(17));
        assertThatIllegalArgumentException().isThrownBy(() -> codec.encode(definition, tooLong))
                .withMessage("Invalid adapted business value");
    }

    @Test
    void usesTypeTagsAndLengthsToSeparateOtherwiseAmbiguousBusinessContent() {
        ColumnDefinition stringValue = column("value", LogicalType.STRING, false, 8, null, null);
        ColumnDefinition enumValue = new ColumnDefinition(
                "value", "value", LogicalType.ENUM, false, 0, 8, null, null,
                List.of("A"), false);
        DatasetDefinition stringDefinition = definition("string_value", List.of(
                column("id", LogicalType.STRING, false, 16, null, null), stringValue));
        DatasetDefinition enumDefinition = definition("enum_value", List.of(
                column("id", LogicalType.STRING, false, 16, null, null), enumValue));
        Map<String, Object> sameText = Map.of("id", "x", "value", "A");

        assertThat(codec.encode(stringDefinition, sameText))
                .isNotEqualTo(codec.encode(enumDefinition, sameText));

        DatasetDefinition boundaries = definition("boundaries", List.of(
                column("id", LogicalType.STRING, false, 16, null, null),
                column("left_value", LogicalType.TEXT, false, null, null, null),
                column("right_value", LogicalType.TEXT, false, null, null, null)));
        assertThat(codec.encode(boundaries,
                Map.of("id", "x", "left_value", "a", "right_value", "bc")))
                .isNotEqualTo(codec.encode(boundaries,
                        Map.of("id", "x", "left_value", "ab", "right_value", "c")));
    }

    private static Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "中🙂");
        row.put("memo", "  ");
        row.put("trade_date", LocalDate.of(2026, 9, 3));
        row.put("month", "202609");
        row.put("count", Long.MIN_VALUE);
        row.put("amount", new BigDecimal("123456789.10"));
        return row;
    }

    private static DatasetDefinition definition() {
        return definition("codec", List.of(
                column("id", LogicalType.STRING, false, 16, null, null),
                column("memo", LogicalType.TEXT, true, null, null, null),
                column("trade_date", LogicalType.DATE, false, null, null, null),
                column("month", LogicalType.MONTH, false, null, null, null),
                column("count", LogicalType.LONG, false, null, null, null),
                column("amount", LogicalType.DECIMAL, false, null, 12, 2)));
    }

    private static DatasetDefinition smallDefinition() {
        return definition("small", List.of(
                column("id", LogicalType.STRING, false, 16, null, null),
                column("memo", LogicalType.TEXT, true, null, null, null)));
    }

    private static DatasetDefinition definition(String api, List<ColumnDefinition> columns) {
        DatasetKey key = new DatasetKey(new PluginId("fixture"), new ApiName(api));
        return new DatasetDefinition(key, api, "test", QueryMode.snapshot, List.of(), TableName.from(key),
                columns, new BusinessKeyDefinition(BusinessKeyMode.COMPOSITE, List.of("id")),
                List.of(), null, 100);
    }

    private static ColumnDefinition column(String name, LogicalType type, boolean nullable,
            Integer length, Integer precision, Integer scale) {
        return new ColumnDefinition(name, name, type, nullable, 0, length, precision, scale,
                List.of(), false);
    }
}
