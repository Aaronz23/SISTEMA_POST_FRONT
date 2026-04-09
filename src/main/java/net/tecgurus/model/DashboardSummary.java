package net.tecgurus.model;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DashboardSummary {
    private long totalProducts;
    private long totalQuotes;
    private long totalOrders;
    private long ordersPending;
    private long quotesPending;
    private BigDecimal totalRevenue;
    private List<Order> recentOrders;
    private List<Quote> recentQuotes;
    private List<Product> lowStock;
}
