package net.tecgurus.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import net.tecgurus.controller.request.QuoteItem;
import net.tecgurus.model.Order;
import net.tecgurus.model.Product;
import net.tecgurus.model.Quote;
import net.tecgurus.model.User;
import net.tecgurus.repository.OrderRepository;
import net.tecgurus.repository.ProductRepository;
import net.tecgurus.repository.QuoteRepository;
import net.tecgurus.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private UserService userService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Transactional(readOnly = true)
    public Page<Order> searchOrders(
            String number,
            String quoteNumber,
            String username,
            Integer itemsCount,
            BigDecimal totalPrice,
            String status,
            String date,
            String nombre,
            String userId,
            int page,
            int size,
            String sort,
            String dir
    ) {
        // Validar y ajustar el campo de ordenamiento
        if (sort != null) {
            sort = sort.trim();
            if (!Arrays.asList("number", "quoteNumber", "username", "totalPrice", "status", "createdAt", "nombre").contains(sort)) {
                sort = "createdAt"; // Valor por defecto si el campo no es válido
            }
        }

        if (dir != null && !Arrays.asList("asc", "desc").contains(dir.toLowerCase())) {
            dir = "desc"; // Valor por defecto si la dirección no es válida
        }

        // 1. Crear consulta con filtros
        Query query = buildFilterQuery(number, quoteNumber, username, itemsCount, totalPrice, status, date, nombre, userId);

        // 2. Contar total de registros
        long total = mongoTemplate.count(query, Order.class);

        // 3. Aplicar ordenamiento
        applySorting(query, sort, dir);

        // 4. Aplicar paginación
        applyPagination(query, page, size);

        // 5. Obtener resultados
        List<Order> orders = mongoTemplate.find(query, Order.class)
                .stream()
                .map(this::enrichOrder)
                .toList();

        // 6. Retornar página
        return new PageImpl<>(orders, PageRequest.of(page, size), total);
    }

      private Query buildFilterQuery(
        String number,
        String quoteNumber,
        String username,
        Integer itemsCount,
        BigDecimal totalPrice,
        String status,
        String date,
        String nombre,
        String userId
      ) {
        Query query = new Query();

        if (StringUtils.hasText(number)) {
          query.addCriteria(Criteria.where("number").regex(number, "i"));
        }

        if (StringUtils.hasText(quoteNumber)) {
          if (quoteNumber.matches("\\d+")) {
            String padded = "Q-" + String.format("%06d", Integer.parseInt(quoteNumber));
            query.addCriteria(Criteria.where("quoteNumber").is(padded));
          } else {
            query.addCriteria(Criteria.where("quoteNumber").is(quoteNumber));
          }
        }

        if (StringUtils.hasText(username)) {
          query.addCriteria(Criteria.where("username").regex(username, "i"));
        }

        if (itemsCount != null) {
          query.addCriteria(Criteria.where("items").size(itemsCount));
        }

        if (totalPrice != null) {
          BigDecimal min = totalPrice.subtract(new BigDecimal("0.01"));
          BigDecimal max = totalPrice.add(new BigDecimal("0.01"));
          query.addCriteria(Criteria.where("totalPrice").gte(min).lte(max));
        }

        if (StringUtils.hasText(status)) {
          query.addCriteria(Criteria.where("status").is(status));
        }

        if (StringUtils.hasText(nombre)) {
           query.addCriteria(Criteria.where("nombre").regex(nombre.trim(), "i"));
        }

        if (StringUtils.hasText(userId)) {
           query.addCriteria(Criteria.where("userId").is(userId));
        }

        if (StringUtils.hasText(date)) {
          LocalDate filterDate = LocalDate.parse(date);
          LocalDateTime start = filterDate.atStartOfDay();
          LocalDateTime end = filterDate.plusDays(1).atStartOfDay();
          query.addCriteria(Criteria.where("createdAt").gte(start).lt(end));
        }

        return query;
      }

    private void applySorting(Query query, String sort, String dir) {
        if (!StringUtils.hasText(sort)) {
            sort = "createdAt"; // Orden por defecto
        }

        // ✅ validar campo permitido, incluyendo "nombre"
        List<String> allowed = Arrays.asList(
                "number", "quoteNumber", "username", "totalPrice", "status", "createdAt", "nombre" // 👈 aquí
        );

        Sort.Direction direction = StringUtils.hasText(dir) && dir.equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        // Aplicar ordenamiento
        query.with(Sort.by(direction, sort));
    }

    private void applyPagination(Query query, int page, int size) {
    query.skip((long) page * size).limit(size);
  }


    public List<Order> getAllOrders() {
        List<Order> orders = orderRepository.findAll();
        return orders.stream().map(this::enrichOrder).toList();
    }

    public List<Order> getOrdersByUserId(String userId) {
        List<Order> orders = orderRepository.findByUserId(userId);
        return orders.stream().map(this::enrichOrder).toList();
    }

    public Order getOrderById(String id) {
        Order order = orderRepository.findById(id).orElse(null);
        return enrichOrder(order);
    }

    public Order saveOrder(Order order) {
        // Fecha por defecto
        if (order.getCreatedAt() == null) {
            order.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        }

        // Enriquecer datos del usuario
        if (order.getUserId() != null) {
            userRepository.findById(order.getUserId()).ifPresent(u -> {
                // ya lo hacías:
                order.setUsername(u.getUsername());
                // ✅ nuevo: guardar el nombre del cliente en el pedido si no viene
                if (!StringUtils.hasText(order.getNombre()) && StringUtils.hasText(u.getNombre())) {
                    order.setNombre(u.getNombre());
                }
            });
        }

        // Folio de pedido (mantener tu lógica)
        if (order.getNumber() == null || order.getNumber().isBlank()) {
            long count = orderRepository.count();
            order.setNumber("P-" + String.format("%02d", count + 1));
        }

        // Folio de cotización (mantener tu lógica)
        if (order.getQuoteId() != null) {
            quoteRepository.findById(order.getQuoteId()).ifPresent(q -> {
                order.setQuoteNumber("Q-" + String.format("%02d", q.getNumber()));
            });
        }

        return orderRepository.save(order);
    }


    public void deleteOrder(String id) {
        orderRepository.deleteById(id);
    }

    private Order enrichOrder(Order o) {
        if (o == null) return null;
        if (o.getQuoteId() != null) {
            quoteRepository.findById(o.getQuoteId()).ifPresent(q -> {
                o.setQuoteNumber("Q-" + String.format("%02d", q.getNumber()));
            });
        }
        if (o.getUserId() != null) {
            userRepository.findById(o.getUserId()).ifPresent(u -> {
                o.setUsername(u.getUsername());
                // ✅ nuevo: si el pedido no tiene nombre, rellénalo con el del usuario
                if (!StringUtils.hasText(o.getNombre()) && StringUtils.hasText(u.getNombre())) {
                    o.setNombre(u.getNombre());
                }
            });
        }
        return o;
    }


    @Transactional
    public void sendOrderEmail(String orderId, String overrideEmail) throws Exception {
    try {
      // 1. Obtener el pedido
      Order order = getOrderById(orderId);
      if (order == null) {
        throw new IllegalArgumentException("No se encontró el pedido con ID: " + orderId);
      }

      // 2. Obtener la cotización asociada
      Quote quote = quoteService.getQuoteById(order.getQuoteId());
      if (quote == null) {
        throw new IllegalStateException("No se encontró la cotización asociada al pedido: " + order.getQuoteId());
      }

      // 3. Obtener información del cliente
      User client = userService.getUserById(order.getUserId());
      if (client == null) {
        throw new IllegalStateException("No se encontró el cliente con ID: " + order.getUserId());
      }

      // 4. Determinar el correo electrónico (priorizar el que viene de frontend si existe)
      String email = (overrideEmail != null && !overrideEmail.isBlank())
        ? overrideEmail.trim()
        : (client.getEmail() != null ? client.getEmail().trim() : null);

      if (email == null || email.isBlank()) {
        throw new IllegalStateException("No se proporcionó un correo electrónico válido");
      }

      // 5. Validar formato de correo
      if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
        throw new IllegalArgumentException("El formato del correo electrónico no es válido: " + email);
      }

      // 6. Generar PDFs
      byte[] orderPdf = buildPdf(orderId);
      byte[] quotePdf = quoteService.buildPdf(quote.getId());

      if (orderPdf == null || orderPdf.length == 0) {
        throw new IllegalStateException("No se pudo generar el PDF del pedido");
      }
      if (quotePdf == null || quotePdf.length == 0) {
        throw new IllegalStateException("No se pudo generar el PDF de la cotización");
      }

      // 7. Crear adjuntos
      EmailService.Attachment[] attachments = {
        new EmailService.Attachment(
          "pedido-" + order.getNumber() + ".pdf",
          orderPdf,
          "application/pdf"
        ),
        new EmailService.Attachment(
          "cotizacion-" + quote.getNumber() + ".pdf",
          quotePdf,
          "application/pdf"
        )
      };

      // 8. Preparar asunto y cuerpo
      String subject = "Confirmación de Pedido #" + order.getNumber() + " y Cotización #" + quote.getNumber();
      String body = "Estimado " + (client.getNombre() != null ? client.getNombre() : "cliente") + ",\n\n" +
        "Le adjuntamos la confirmación de su pedido y la cotización correspondiente.\n\n" +
        "Si tiene alguna pregunta, no dude en contactarnos.\n\n" +
        "Saludos cordiales,\n" +
        "El equipo de ventas";

      // 9. Enviar correo
      emailService.sendEmailWithAttachments(email, subject, body, attachments);

    } catch (Exception e) {
      String errorMsg = String.format("Error al enviar el correo para el pedido %s: %s", orderId, e.getMessage());
      throw new RuntimeException(errorMsg, e);
    }
  }

    @Transactional
    public void sendQuoteEmail(String orderId, String overrideEmail) throws Exception {
        Order order = getOrderById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("No se encontró el pedido con ID: " + orderId);
        }

        Quote quote = quoteService.getQuoteById(order.getQuoteId());
        if (quote == null) {
            throw new IllegalStateException("No se encontró la cotización asociada al pedido: " + order.getQuoteId());
        }

        User client = userService.getUserById(order.getUserId());
        if (client == null) {
            throw new IllegalStateException("No se encontró el cliente con ID: " + order.getUserId());
        }

        String email = (overrideEmail != null && !overrideEmail.isBlank())
                ? overrideEmail.trim()
                : (client.getEmail() != null ? client.getEmail().trim() : null);

        if (email == null || email.isBlank()) {
            throw new IllegalStateException("No se proporcionó un correo electrónico válido");
        }

        byte[] quotePdf = quoteService.buildPdf(quote.getId());
        if (quotePdf == null || quotePdf.length == 0) {
            throw new IllegalStateException("No se pudo generar el PDF de la cotización");
        }

        EmailService.Attachment[] attachments = {
                new EmailService.Attachment(
                        "cotizacion-" + quote.getNumber() + ".pdf",
                        quotePdf,
                        "application/pdf"
                )
        };

        // 7. Preparar correo
        String subject = "Confirmación de Cotización #" + quote.getNumber();
        String body = "Estimado " + (client.getNombre() != null ? client.getNombre() : "cliente") + ",\n\n" +
                "Le adjuntamos la cotización correspondiente.\n\n" +
                "Saludos cordiales,\n" +
                "El equipo de ventas";

        // 8. Enviar
        emailService.sendEmailWithAttachments(email, subject, body, attachments);
    }

    @Transactional
    public byte[] buildPdf(String orderId) throws Exception {
        Order order = getOrderById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("No se encontró el pedido: " +
                    orderId);
        }
        Quote quote = quoteService.getQuoteById(order.getQuoteId());
        if (quote == null) {
            throw new IllegalStateException("No se encontró la cotización relacionada al pedido: " + orderId);
        }
        User client = userService.getUserById(order.getUserId());
        List<QuoteItem> items = order.getItems();
        List<String> productIds = items.stream()
                .map(QuoteItem::getProductId)
                .toList();
        List<Product> products = productRepository.findByKeyIn(productIds);
        Map<String, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getKey, p -> p));
        String html = buildOrderPdfHtml(order, quote, client, items, productMap);
        try (var out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        }
    }

    @Transactional
    public Order convertToOrder(String quoteId, String deliveryOption) {
        Quote q = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new IllegalArgumentException("Cotización no encontrada"));

        Order o = new Order();
        o.setQuoteId(q.getId());
        o.setUserId(q.getClientId());
        o.setItems(q.getItems());
        o.setTotalPrice(q.getTotalPrice());
        o.setStatus("CREADO");
        // Nuevos campos (opcionales)
        o.setDeliveryOption(deliveryOption);

        // Guardar pedido
        Order saved = saveOrder(o);
        q.setStatus("CONFIRMADO");
        quoteRepository.save(q);
        try {
            if (adminEmail != null && !adminEmail.isBlank()) {
                sendOrderEmail(saved.getId(), adminEmail);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al notificar al admin para el pedido " + saved.getId(), e);
        }
        return saved;
    }




    private String buildOrderPdfHtml(Order order, Quote quote, User client,
                                   List<QuoteItem> items, Map<String, Product> productMap) {
    // 1. Formatear fecha
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
    ZoneId mx = ZoneId.of("America/Mexico_City");
    String formattedDate = order.getCreatedAt() != null
       ? order.getCreatedAt()
       .atOffset(ZoneOffset.UTC)
       .atZoneSameInstant(mx)
       .format(fmt)
       : "Fecha no disponible";

    // 2. Construir la tabla de productos
    StringBuilder itemsHtml = new StringBuilder();
    NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"));
    BigDecimal subtotal = BigDecimal.ZERO;

    for (QuoteItem item : items) {
      Product p = productMap.get(item.getProductId());
      if (p == null) continue;

      int qty = item.getQuantity();
      BigDecimal unitPrice = p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO;
      BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
      subtotal = subtotal.add(lineTotal);

      itemsHtml.append("<tr>")
        .append("<td class='right'>").append(qty).append("</td>")
        .append("<td>").append(esc(p.getKey())).append("</td>")
        .append("<td>").append(esc(p.getName())).append("</td>")
        .append("<td class='right'>").append(esc(nf.format(unitPrice))).append("</td>")
        .append("<td class='right'>").append(esc(nf.format(lineTotal))).append("</td>")
        .append("</tr>");
    }

    // 3. Calcular totales
    BigDecimal total = subtotal;

    // 4. Preparar texto de entrega
    String deliveryText = "No especificada";
    if (order.getDeliveryOption() != null) {
      if ("RECOLECCION".equalsIgnoreCase(order.getDeliveryOption())) {
        deliveryText = "Cliente recolecta";
      } else if ("ENVIO".equalsIgnoreCase(order.getDeliveryOption())) {
        deliveryText = "Envío a domicilio";
      }
    }

    // 5. Construir HTML válido (XHTML)
    return """
      <!DOCTYPE html>
      <html xmlns="http://www.w3.org/1999/xhtml">
      <head>
          <meta charset="UTF-8"/>
          <title>Pedido #%s</title>
          <style>
              body { font-family: Arial, sans-serif; margin: 20px; }
              h1 { color: #333; }
              .header { margin-bottom: 20px; }
              .client-info { margin-bottom: 20px; }
              table { width: 100%%; border-collapse: collapse; margin-top: 10px; }
              th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
              th { background-color: #f2f2f2; }
              .right { text-align: right; }
              .totals { margin-top: 20px; float: right; }
              .totals table { width: 300px; }
          </style>
      </head>
      <body>
          <div class="header">
              <h1>Pedido #%s</h1>
              <div><strong>Fecha:</strong> %s</div>
              <div><strong>Estado:</strong> %s</div>
          </div>

          <div class="client-info">
              <h3>Datos del Cliente</h3>
              <div><strong>Cliente:</strong> %s %s</div>
              <div><strong>Correo:</strong> %s</div>
              <div><strong>Teléfono:</strong> %s</div>
              <div><strong>Forma de entrega:</strong> %s</div>
          </div>

          <h3>Productos</h3>
          <table>
              <thead>
                  <tr>
                      <th class="right">Cantidad</th>
                      <th>Código</th>
                      <th>Descripción</th>
                      <th class="right">Precio Unitario</th>
                      <th class="right">Total</th>
                  </tr>
              </thead>
              <tbody>
                  %s
              </tbody>
          </table>

          <div class="totals">
              <table>
                  <tr>
                      <td><strong>Subtotal:</strong></td>
                      <td class="right">%s</td>
                  </tr>
                  <tr>
                      <td><strong>Total:</strong></td>
                      <td class="right"><strong>%s</strong></td>
                  </tr>
              </table>
          </div>
      </body>
      </html>
      """.formatted(
      order.getNumber() != null ? order.getNumber() : "Sin número",
      order.getNumber() != null ? order.getNumber() : "Sin número",
      formattedDate,
      order.getStatus(),
            client != null ? esc(client.getClave() != null ? client.getClave() : "") : "",
            client != null ? esc(client.getNombre() != null ? client.getNombre() : "Cliente no especificado") : "Cliente no especificado",
      client != null ? esc(client.getEmail() != null ? client.getEmail() : "No proporcionado") : "No proporcionado",
      client != null ? esc(client.getTelefono() != null ? client.getTelefono() : "No proporcionado") : "No proporcionado",
      deliveryText,
      itemsHtml.toString(),
      nf.format(subtotal),
      nf.format(total)
    );
  }


    private String esc(String s) {
        return s == null ? "" : s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }


}
