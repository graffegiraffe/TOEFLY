package com.toefly.content.adapter.in.web;

import com.toefly.content.adapter.in.web.dto.AdminItemSetRow;
import com.toefly.content.adapter.in.web.dto.ExamFormat;
import com.toefly.content.adapter.in.web.dto.ItemSet;
import com.toefly.content.adapter.in.web.dto.ItemSetSummary;
import com.toefly.content.adapter.in.web.dto.ItemStatus;
import com.toefly.content.adapter.in.web.dto.Provenance;
import com.toefly.content.adapter.in.web.dto.Section;
import com.toefly.content.adapter.in.web.dto.SourceRef;
import com.toefly.content.adapter.in.web.dto.TaskType;
import com.toefly.content.application.port.out.CatalogQueryPort.Row;
import com.toefly.content.domain.catalog.Source;
import java.math.BigDecimal;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class CatalogMapper {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private CatalogMapper() {}

    static SourceRef source(Source s) {
        return new SourceRef()
                .code(s.code())
                .title(s.title())
                .publisher(s.publisher())
                .year(s.year())
                .url(s.url())
                .attribution(s.attributionText())
                .legacy(s.legacy());
    }

    static ItemSetSummary summary(Row row) {
        var s = row.itemSet();
        return new ItemSetSummary()
                .id(s.id())
                .version(s.version())
                .taskType(TaskType.fromValue(s.taskType().name()))
                .section(s.section() == null ? null : Section.fromValue(s.section().name()))
                .examFormat(ExamFormat.fromValue(s.examFormat().name()))
                .cefr(s.cefr())
                .topic(s.topic())
                .itemCount(s.itemCount())
                .estimatedMinutes(BigDecimal.valueOf(s.estimatedMinutes()))
                .source(source(row.source()));
    }

    static AdminItemSetRow adminRow(Row row) {
        var s = row.itemSet();
        return new AdminItemSetRow()
                .id(s.id())
                .version(s.version())
                .taskType(TaskType.fromValue(s.taskType().name()))
                .examFormat(ExamFormat.fromValue(s.examFormat().name()))
                .cefr(s.cefr())
                .topic(s.topic())
                .status(ItemStatus.fromValue(s.status().name()))
                .hiddenReason(s.hiddenReason() == null ? null : s.hiddenReason().name())
                .provenance(Provenance.fromValue(s.provenance().name()))
                .sourceCode(row.source().code())
                .itemCount(s.itemCount())
                .openReports(row.openReports());
    }

    static ItemSet full(com.toefly.content.domain.catalog.ItemSet s, Source source, boolean withKeys) {
        JsonNode payload = JSON.readTree(s.payload());
        Map<String, Object> body = JSON.convertValue(
                withKeys ? payload : PayloadRedactor.redact(payload), new TypeReference<Map<String, Object>>() {});
        return new ItemSet()
                .id(s.id())
                .version(s.version())
                .taskType(TaskType.fromValue(s.taskType().name()))
                .section(s.section() == null ? null : Section.fromValue(s.section().name()))
                .examFormat(ExamFormat.fromValue(s.examFormat().name()))
                .cefr(s.cefr())
                .topic(s.topic())
                .skills(s.skills())
                .itemCount(s.itemCount())
                .estimatedMinutes(BigDecimal.valueOf(s.estimatedMinutes()))
                .etsNormSeconds(s.etsNormSeconds())
                .status(ItemStatus.fromValue(s.status().name()))
                .source(source(source))
                .payload(body);
    }
}
