package com.toefly.planning.domain.composition;

import com.toefly.planning.domain.week.WeekSection;
import java.util.Set;

/** A page of toeflresources.com for the sections it helps with. */
public record ResourceLink(String url, String title, Set<WeekSection> sections) {

    public ResourceLink {
        sections = Set.copyOf(sections);
    }
}
