package net.tecgurus.controller;

import jakarta.servlet.http.HttpServletResponse;
import net.tecgurus.model.Order;
import net.tecgurus.service.OrderService;
import net.tecgurus.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    @Value("${app.admin.email}")
    private String adminEmail;

  @GetMapping
  public Page<Order> getAllOrders(
    @RequestParam(required = false) String number,
    @RequestParam(required = false) String quoteNumber,
    @RequestParam(required = false) String username,
    @RequestParam(required = false) Integer itemsCount,
    @RequestParam(required = false) BigDecimal totalPrice,
    @RequestParam(required = false) String status,
    @RequestParam(required = false) String date,
    @RequestParam(required = false) String nombre,
    @RequestParam(required = false) String userId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "100") int size,
    @RequestParam(required = false) String sort,
    @RequestParam(required = false) String dir,
    Authentication auth
  ) {
      boolean isClient = auth != null && auth.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .anyMatch("ROLE_CLIENT"::equals);
      String effectiveUserId = userId;
      if (isClient) {
          var me = userService.findByUsername(auth.getName()).orElse(null);
          effectiveUserId = (me != null) ? me.getId() : null;
      }
      return orderService.searchOrders(
              number, quoteNumber, username, itemsCount,
              totalPrice, status, date, nombre, effectiveUserId,
              page, size, sort, dir
      );
  }

    @GetMapping("/user/{userId}")
    public List<Order> getOrdersByUserId(@PathVariable String userId) {
        return orderService.getOrdersByUserId(userId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable String id) {
        Order order = orderService.getOrderById(id);
        if (order != null) {
            return ResponseEntity.ok(order);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        Order createdOrder = orderService.saveOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(@PathVariable String id, @RequestBody Order order) {
        Order existingOrder = orderService.getOrderById(id);
        if (existingOrder != null) {
            order.setId(id);
            Order updatedOrder = orderService.saveOrder(order);
            return ResponseEntity.ok(updatedOrder);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable String id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public void exportOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userId,
            HttpServletResponse response
    ) throws IOException {
        String filename = "orders-" + LocalDate.now() + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            writer.write("id,quoteId,userId,productCount,totalPrice,status");
            writer.newLine();

            List<Order> all = orderService.getAllOrders();

            if (status != null && !status.isBlank()) {
                all = all.stream().filter(o -> status.equalsIgnoreCase(o.getStatus())).toList();
            }
            if (userId != null && !userId.isBlank()) {
                all = all.stream().filter(o -> userId.equals(o.getUserId())).toList();
            }

            for (Order o : all) {
                int count = (o.getProductIds() == null) ? 0 : o.getProductIds().size();
                writeCsvRow(writer,
                        o.getId(),
                        o.getQuoteId(),
                        o.getUserId(),
                        String.valueOf(count),
                        (o.getTotalPrice() != null ? o.getTotalPrice().toPlainString() : ""),
                        o.getStatus()
                );
            }
        }
    }

    @PostMapping("/{id}/send-email")
    public ResponseEntity<?> sendOrderEmail(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String email = (body != null) ? body.get("email") : null;
            orderService.sendOrderEmail(id, email);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // Cliente → reenvía SOLO la cotización
    @PostMapping("/{id}/resend-quote-email")
    public ResponseEntity<?> resendQuoteEmail(@PathVariable String id) {
        try {
            orderService.sendQuoteEmail(id, null);
            return ResponseEntity.ok(Map.of("message", "Correo de pedido reenviado"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error al reenviar pedido: " + e.getMessage()));
        }
    }

    // Admin → reenvía pedido + cotización
    @PostMapping("/{id}/resend-order-email")
    public ResponseEntity<?> resendOrderEmail(@PathVariable String id) {
        try {
            orderService.sendOrderEmail(id, null);
            return ResponseEntity.ok(Map.of("message", "Correo de pedido y cotización reenviado"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error al reenviar pedido: " + e.getMessage()));
        }
    }

    private static void writeCsvRow(BufferedWriter w, String... cols) throws IOException {
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) w.write(',');
            w.write(csv(cols[i]));
        }
        w.newLine();
    }

    private static String csv(String s) {
        if (s == null) return "\"\"";
        String esc = s.replace("\"", "\"\"");
        return "\"" + esc + "\"";
    }
}
