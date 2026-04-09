package net.tecgurus.service;

import net.tecgurus.model.DashboardSummary;
import net.tecgurus.model.Order;
import net.tecgurus.model.Product;
import net.tecgurus.model.Quote;
import net.tecgurus.repository.OrderRepository;
import net.tecgurus.repository.ProductRepository;
import net.tecgurus.repository.QuoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class DashboardService {
    @Autowired private OrderRepository orderRepository;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private ProductRepository productRepository;

    @Transactional
    public DashboardSummary getSummary(LocalDateTime startDate, LocalDateTime endDate, Integer lowStockThreshold) {
        DashboardSummary summary = new DashboardSummary();
        List<Order> orders;
        List<Quote> quotes;

        if (startDate != null && endDate != null) {
            LocalDateTime adjustedEndDate = endDate.toLocalDate().atTime(23, 59, 59);
            orders = orderRepository.findByCreatedAtBetween(startDate, adjustedEndDate);
            quotes = quoteRepository.findByCreatedAtBetween(startDate, adjustedEndDate);
        } else {
            orders = orderRepository.findAll();
            quotes = quoteRepository.findAll();
        }

        long totalProducts = productRepository.count();
        summary.setTotalProducts(totalProducts);

        // KPIs
        summary.setTotalOrders(orders.size());
        summary.setOrdersPending(
                (int) orders.stream().filter(o -> "PENDIENTE".equalsIgnoreCase(o.getStatus())).count()
        );

        summary.setTotalQuotes(quotes.size());
        summary.setQuotesPending(
                (int) quotes.stream().filter(q -> "PENDING".equalsIgnoreCase(q.getStatus())).count()
        );

        // Ganancias (suma de totalPrice de pedidos completados)
        summary.setTotalRevenue(
                orders.stream()
                        .filter(o -> "COMPLETADO".equalsIgnoreCase(o.getStatus()))
                        .map(Order::getTotalPrice)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        // Últimos registros
        summary.setRecentOrders(
                orders.stream()
                        .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                        .limit(10)
                        .toList()
        );
        summary.setRecentQuotes(
                quotes.stream()
                        .sorted(Comparator.comparing(Quote::getCreatedAt).reversed())
                        .limit(10)
                        .toList()
        );
        return summary;
    }
}

