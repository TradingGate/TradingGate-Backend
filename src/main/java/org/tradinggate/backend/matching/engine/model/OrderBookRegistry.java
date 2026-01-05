package org.tradinggate.backend.matching.engine.model;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tradinggate.backend.matching.engine.kafka.PartitionCountProvider;
import org.tradinggate.backend.matching.snapshot.util.SnapshotCryptoUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderBookRegistry {

    private final PartitionCountProvider partitionCountProvider;
    private final ConcurrentMap<String, OrderBook> books = new ConcurrentHashMap<>();

    public Optional<OrderBook> find(String symbol) {
        if (symbol == null || symbol.isBlank()) return Optional.empty();
        return Optional.ofNullable(books.get(symbol));
    }

    public OrderBook getOrCreate(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        return books.computeIfAbsent(symbol, OrderBook::create);
    }

    public void remove(String symbol) {
        if (symbol == null || symbol.isBlank()) return;
        books.remove(symbol);
    }

    public void put(OrderBook orderBook) {
        if (orderBook == null) throw new IllegalArgumentException("orderBook must not be null");
        books.put(orderBook.getSymbol(), orderBook);
    }

    public Map<String, OrderBook> snapshotView() {
        return Map.copyOf(books);
    }

    public Map<String, OrderBook> findAllByPartition(String topic, int partition) {
        int partitionCount = partitionCountProvider.partitionCount(topic);

        return books.entrySet().stream()
                .filter(e -> SnapshotCryptoUtils.resolve(e.getKey(), partitionCount) == partition)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<String> removeAllByPartition(String topic, int partition) {
        int partitionCount = partitionCountProvider.partitionCount(topic);

        List<String> removedSymbols = new ArrayList<>();
        List<String> symbols = new ArrayList<>(books.keySet());

        for (String symbol : symbols) {
            int p = SnapshotCryptoUtils.resolve(symbol, partitionCount);
            if (p != partition) continue;

            OrderBook removedBook = books.remove(symbol);
            if (removedBook != null) {
                removedSymbols.add(symbol);
            }
        }
        return removedSymbols;
    }

}
