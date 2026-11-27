package com.toefly.planning.application.service;

import com.toefly.planning.application.port.in.AddAgentTaskUseCase;
import com.toefly.planning.application.port.out.ItemSetCatalogPort;
import com.toefly.planning.application.port.out.ItemSetCatalogPort.CatalogEntry;
import com.toefly.planning.application.port.out.StudyPlanRepository;
import com.toefly.planning.application.port.out.WeekRepository;
import com.toefly.planning.domain.plan.LearnerCalendar;
import com.toefly.planning.domain.week.ExtraTaskDay;
import com.toefly.planning.domain.week.TaskOrigin;
import com.toefly.planning.domain.week.Week;
import com.toefly.planning.domain.week.WeekRuleException;
import com.toefly.planning.domain.week.WeekTask;
import com.toefly.planning.domain.week.WeekTaskKind;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Extra tasks of Toefly Tutor: the set comes from the local catalog, so the endpoint stays a leaf; the limits are the
 * rules of {@link Week#addAgentTask}. A set the learner did before is added as a repeat.
 */
@Service
@Transactional
public class AgentTaskService implements AddAgentTaskUseCase {

    static final int REASON_LENGTH = 200;

    private final WeekRepository weeks;
    private final StudyPlanRepository plans;
    private final ItemSetCatalogPort catalog;
    private final Clock clock;

    public AgentTaskService(WeekRepository weeks, StudyPlanRepository plans, ItemSetCatalogPort catalog, Clock clock) {
        this.weeks = weeks;
        this.plans = plans;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    public WeekTask add(UUID weekId, UUID itemSetId, ExtraTaskDay day, String reason) {
        Week week = weeks.findById(weekId)
                .filter(found -> found.status().visibleToStudent())
                .orElseThrow(() -> new WeekRuleException("not-found", "No week " + weekId));
        CatalogEntry set = catalog.find(itemSetId)
                .orElseThrow(() -> new WeekRuleException("not-found", "No approved item set " + itemSetId));
        boolean repeat = weeks.doneItemSets(week.userId())
                .getOrDefault(set.taskType(), List.of())
                .contains(itemSetId);
        String why = reason.strip();
        WeekTask task = WeekTask.pending(
                WeekTaskKind.ITEM_SET,
                set.taskType().section(),
                set.taskType(),
                itemSetId.toString(),
                CatalogSetSupply.title(set),
                set.estimatedMinutes(),
                TaskOrigin.TUTOR_AGENT,
                why.length() > REASON_LENGTH ? why.substring(0, REASON_LENGTH) : why,
                repeat);
        Instant now = clock.instant();
        var plan = plans.findById(week.planId()).orElseThrow();
        week.addAgentTask(task, day, LearnerCalendar.today(now, plan.timezone()));
        weeks.update(week);
        return task;
    }
}
