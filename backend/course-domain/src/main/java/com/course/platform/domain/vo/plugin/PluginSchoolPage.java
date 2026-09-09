package com.course.platform.domain.vo.plugin;

import java.util.List;

/** The supplied protocol proves data.list, not a reliable total-count contract. */
public record PluginSchoolPage(List<PluginSchool> items, int page, int pageSize, boolean hasMore) {
    public PluginSchoolPage {
        items = List.copyOf(items);
    }
}
