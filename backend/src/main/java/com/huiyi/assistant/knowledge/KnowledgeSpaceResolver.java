package com.huiyi.assistant.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class KnowledgeSpaceResolver {

    public List<Long> resolve(List<Long> httpSpaceIds, List<Long> toolSpaceIds, List<Long> activeIds) {
        List<Long> active = activeIds == null ? List.of() : List.copyOf(activeIds);
        List<Long> http = expand(httpSpaceIds, active);
        List<Long> tool = expand(toolSpaceIds, active);
        Set<Long> allowed = new HashSet<>(http);
        allowed.retainAll(new HashSet<>(tool));
        List<Long> resolved = new ArrayList<>();
        for (Long id : active) {
            if (allowed.contains(id)) {
                resolved.add(id);
            }
        }
        return List.copyOf(resolved);
    }

    private static List<Long> expand(List<Long> requested, List<Long> active) {
        if (requested == null) {
            return active;
        }
        return requested;
    }
}
