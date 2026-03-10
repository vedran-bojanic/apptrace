package io.apptrace.server.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * A single page from a cursor-based paginated query.
 *
 * @param items      items on this page
 * @param nextCursor cursor for the next page; empty if this is the last page
 * @param pageSize   the requested page size
 */
public record Page<T>(
        List<T> items,
        Optional<Cursor> nextCursor,
        int pageSize
) {
    public Page {
        items = List.copyOf(items);
    }

    public boolean hasNextPage()  { return nextCursor.isPresent(); }
    public boolean isEmpty()      { return items.isEmpty(); }
    public int size()             { return items.size(); }

    /**
     * Builds a page from a raw result list.
     *
     * The repository should fetch {@code pageSize + 1} rows. If it gets
     * {@code pageSize + 1} back, there is a next page and the extra row
     * is trimmed. The caller supplies the cursor factory so this stays
     * generic across different entity types.
     */
    public static <T> Page<T> from(
            List<T> rawResults,
            int pageSize,
            java.util.function.Function<T, Cursor> cursorExtractor
    ) {
        boolean hasMore = rawResults.size() > pageSize;
        List<T> pageItems = hasMore ? rawResults.subList(0, pageSize) : rawResults;
        Optional<Cursor> next = hasMore
                ? Optional.of(cursorExtractor.apply(pageItems.get(pageItems.size() - 1)))
                : Optional.empty();
        return new Page<>(pageItems, next, pageSize);
    }
}

