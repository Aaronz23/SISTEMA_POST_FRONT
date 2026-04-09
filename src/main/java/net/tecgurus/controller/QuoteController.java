package net.tecgurus.controller;


import jakarta.servlet.http.HttpServletResponse;
import net.tecgurus.controller.request.QuoteDetailRequest;
import net.tecgurus.controller.request.QuoteItem;
import net.tecgurus.model.Order;
import net.tecgurus.model.Product;
import net.tecgurus.model.Quote;
import net.tecgurus.repository.ProductRepository;
import net.tecgurus.service.OrderService;
import net.tecgurus.service.QuoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    @Autowired private QuoteService quoteService;
    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;



    @GetMapping("/{id}/detail")
    public ResponseEntity<QuoteDetailRequest> getQuoteDetail(@PathVariable String id) {
        QuoteDetailRequest request = quoteService.getDetail(id);
        return request != null ? ResponseEntity.ok(request) : ResponseEntity.notFound().build();
    }

    @GetMapping
    public List<Quote> getAllQuotes(
            @RequestParam(required = false) String number,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Integer itemsCount,
            @RequestParam(required = false) BigDecimal totalPrice,
            @RequestParam(required = false) BigDecimal profitMargin,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String expiredAt,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir
    ) {
        return quoteService.searchQuotes(number, username, userId, itemsCount,
                totalPrice, profitMargin, status, date, nombre, expiredAt, sort, dir);
    }


    @GetMapping("/user/{userId}")
    public List<Quote> getQuotesByUserId(@PathVariable String userId) {
        return quoteService.getQuotesByUserId(userId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Quote> getQuoteById(@PathVariable String id) {
        Quote quote = quoteService.getQuoteById(id);
        if (quote != null) {
            return ResponseEntity.ok(quote);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Quote> createQuote(@RequestBody Quote quote) {
        if (quote.getItems() == null || quote.getItems().isEmpty()) {
            return ResponseEntity.badRequest().build(); // debes enviar items
        }
        Quote createdQuote = quoteService.saveQuote(quote);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdQuote);
    }


    @PutMapping("/{id}")
    public ResponseEntity<Quote> updateQuote(@PathVariable String id, @RequestBody Quote quote) {
        Quote existingQuote = quoteService.getQuoteById(id);
        if (existingQuote != null) {
            quote.setId(id);
            Quote updatedQuote = quoteService.saveQuote(quote);
            return ResponseEntity.ok(updatedQuote);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuote(@PathVariable String id) {
        quoteService.deleteQuote(id);
        return ResponseEntity.noContent().build();
    }

    // --- Cambiar estado (opcional, recomendado) ---
    @PatchMapping("/{id}/status")
    public ResponseEntity<Quote> patchStatus(@PathVariable String id, @RequestBody Map<String,String> body) {
        Quote q = quoteService.getQuoteById(id);
        if (q == null) return ResponseEntity.notFound().build();
        q.setStatus(body.get("status"));
        return ResponseEntity.ok(quoteService.saveQuote(q));
    }

    // --- Convertir cotización a pedido (opcional, útil) ---
    // En QuoteController.java
    @PostMapping("/{id}/convert-to-order")
    public ResponseEntity<Order> convertToOrder(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        try {
            String deliveryOption = body != null ? body.get("deliveryOption") : null;
            Order order = orderService.convertToOrder(id, deliveryOption);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }



    // --- Export CSV ---
    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public void export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userId,
            HttpServletResponse response
    ) throws IOException {
        String filename = "quotes-" + LocalDate.now() + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        // BOM para Excel
        response.getOutputStream().write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});

        try (var writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write("id,userId,productCount,totalPrice,profitMargin,status");
            writer.newLine();

            List<Quote> all = quoteService.getAllQuotes();
            if (status != null && !status.isBlank()) {
                all = all.stream().filter(q -> status.equalsIgnoreCase(q.getStatus())).toList();
            }
            if (userId != null && !userId.isBlank()) {
                all = all.stream().filter(q -> userId.equals(q.getUserId())).toList();
            }

            for (Quote q : all) {
                int count = (q.getItems() == null)
                        ? 0
                        : q.getItems().stream().mapToInt(QuoteItem::getQuantity).sum();

                writeCsvRow(writer,
                        q.getId(),
                        q.getUserId(),
                        String.valueOf(count),
                        q.getTotalPrice() != null ? q.getTotalPrice().toPlainString() : "",
                        q.getProfitMargin() != null ? q.getProfitMargin().toPlainString() : "",
                        q.getStatus()
                );
            }
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

    @GetMapping(value="/{id}/pdf", produces="application/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String id) {
        try {
            byte[] pdf = quoteService.buildPdf(id);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"quote-" + id + ".pdf\"")
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // Ubicación: C:\GitLab\projects\Tecgurus\SAE(FERRETERIA)\Pro\backend-contactoelectrico\src\main\java\net\tecgurus\controller\QuoteController.java
// Añade este método al final de la clase QuoteController, antes del último '}'

    @PostMapping("/{id}/send-email")
    public ResponseEntity<?> sendQuoteEmail(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");

            // Llama al método orquestador que acabamos de crear
            quoteService.sendQuoteEmail(id, email);

            return ResponseEntity.ok(Map.of("message", "Cotización enviada con éxito a " + email));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            // Aquí podrías añadir un log si lo necesitas
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error interno al enviar el correo: " + e.getMessage()));
        }
    }
}
