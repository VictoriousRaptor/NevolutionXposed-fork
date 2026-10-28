package com.oasisfeng.nevo.decorators.wechat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** Chooses prior notification generations needed to rebuild a conversation transcript. */
final class NotificationHistoryWindow {
    static <T> List<T> prior(List<T> archive, T current, Predicate<T> normalized, Predicate<T> eligible) {
        if (normalized.test(current)) return Collections.emptyList();
        int end = archive.size();
        for (int i = end - 1; i >= 0; i--) if (archive.get(i) == current) {
            end = i;
            break;
        }
        int start = 0;
        for (int i = end - 1; i >= 0; i--) {
            T older = archive.get(i);
            if (older != current && eligible.test(older) && normalized.test(older)) {
                start = i;
                break;
            }
        }
        List<T> prior = new ArrayList<>();
        for (int i = start; i < end; i++) {
            T older = archive.get(i);
            if (older != current && eligible.test(older)) prior.add(older);
        }
        return prior;
    }

    private NotificationHistoryWindow() {}
}
