package net.tecgurus.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import net.tecgurus.controller.request.QuoteDetailRequest;
import net.tecgurus.controller.request.QuoteItem;
import net.tecgurus.model.Order;
import org.apache.commons.text.StringEscapeUtils;
import net.tecgurus.model.Quote;
import net.tecgurus.model.User;
import net.tecgurus.repository.QuoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.math.RoundingMode;
import java.util.Base64;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuoteService {

    @Value("${quote.validity.days:3}")
    private int quoteValidityDays;

    @Autowired private QuoteRepository quoteRepository;
    @Autowired private UserService userService;
    @Autowired private SequenceGeneratorService seq;
    @Autowired private EmailService emailService;
    @Autowired private MongoTemplate mongoTemplate;

    @Transactional
    public List<Quote> searchQuotes(
            String number,
            String username,
            String userId,
            Integer itemsCount,
            BigDecimal totalPrice,
            BigDecimal profitMargin,
            String status,
            String date,
            String nombre,
            String expiredAt,
            String sort,
            String dir
    ) {
        Query query = new Query();

        if (number != null && !number.isBlank() && number.matches("\\d+")) {
            query.addCriteria(Criteria.where("number").is(Integer.parseInt(number.trim())));
        }
        if (username != null && !username.isBlank()) {
            query.addCriteria(Criteria.where("username").regex(username, "i"));
        }
        if (userId != null && !userId.isBlank()) {
            query.addCriteria(Criteria.where("userId").is(userId));
        }
        if (totalPrice != null) {
            BigDecimal min = totalPrice.subtract(new BigDecimal("0.01"));
            BigDecimal max = totalPrice.add(new BigDecimal("0.01"));
            query.addCriteria(Criteria.where("totalPrice").gte(min).lte(max));
        }
        if (profitMargin != null) {
            query.addCriteria(Criteria.where("profitMargin").is(profitMargin));
        }
        if (itemsCount != null) {
            query.addCriteria(Criteria.where("items").size(itemsCount));
        }
        if (status != null && !status.isBlank()) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (date != null && !date.isBlank()) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate filterDate = LocalDate.parse(date, formatter);
            LocalDateTime start = filterDate.atStartOfDay();
            LocalDateTime end = filterDate.plusDays(1).atStartOfDay();
            Date startDate = Date.from(start.atZone(ZoneId.systemDefault()).toInstant());
            Date endDate = Date.from(end.atZone(ZoneId.systemDefault()).toInstant());
            query.addCriteria(Criteria.where("createdAt").gte(startDate).lt(endDate));
        }

        if (expiredAt != null && !expiredAt.isBlank()) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate filterDate = LocalDate.parse(expiredAt, formatter);
            LocalDateTime start = filterDate.atStartOfDay();
            LocalDateTime end = filterDate.plusDays(1).atStartOfDay();
            Date startDate = Date.from(start.atZone(ZoneId.systemDefault()).toInstant());
            Date endDate = Date.from(end.atZone(ZoneId.systemDefault()).toInstant());

            query.addCriteria(Criteria.where("expiredAt").gte(startDate).lt(endDate));
        }

        // 🔎 Filtro por nombre de CLIENTE
        if (nombre != null && !nombre.isBlank()) {
            List<User> clients = userService.findByNameLike(nombre);  // punto 2
            List<String> clientIds = clients.stream().map(User::getId).toList();
            if (clientIds.isEmpty()) return List.of();
            query.addCriteria(Criteria.where("clientId").in(clientIds));
        }

        // Aplicar ordenamiento
        if (sort != null && !sort.trim().isEmpty()) {
            Sort.Direction direction = (dir != null && dir.equalsIgnoreCase("asc"))
                    ? Sort.Direction.ASC : Sort.Direction.DESC;

            // Mapear el campo de ordenamiento al campo correspondiente en la base de datos
            String sortField;
            switch (sort) {
                case "number":
                    sortField = "number";
                    break;
                case "username":
                    sortField = "username";
                    break;
                // Cambia el case para "nombre" a "clientId" o asegúrate de que exista el campo "nombre"
                case "nombre":
                    sortField = "clientId"; // O el campo correcto que contenga el nombre
                    break;

                case "totalPrice":
                    sortField = "totalPrice";
                    // Asegúrate de que los documentos tengan el campo como número, no como string
                    break;
                case "profitMargin":
                    sortField = "profitMargin";
                    break;
                case "status":
                    sortField = "status";
                    break;
                case "expiredAt":
                    sortField = "expiredAt";
                    break;
                case "createdAt":
                default:
                    sortField = "createdAt";
                    break;
            }

            query.with(Sort.by(direction, sortField));
        } else {
            // Ordenamiento por defecto
            query.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        // Ejecutar consulta y enriquecer resultados
        List<Quote> quotes = mongoTemplate.find(query, Quote.class);
        return quotes.stream().map(this::enrichQuote).collect(Collectors.toList());
    }


    @Transactional
    public List<Quote> getAllQuotes() {
        List<Quote> quotes = quoteRepository.findAll();
        return quotes.stream().map(this::enrichQuote).collect(Collectors.toList());
    }

    @Transactional
    public QuoteDetailRequest getDetail(String id) {
        Quote q = getQuoteById(id);
        if (q == null) return null;

        var request = new QuoteDetailRequest();
        request.setId(q.getId());
        request.setNumber(q.getNumber());
        request.setUserId(q.getUserId());
        request.setTotalPrice(q.getTotalPrice());
        request.setProfitMargin(q.getProfitMargin());
        request.setStatus(q.getStatus());
        request.setCreatedAt(q.getCreatedAt());
        request.setExpiredAt(q.getExpiredAt()); // <-- AÑADIR ESTA LÍNEA

        var user = userService.getUserById(q.getUserId());
        request.setUsername(user != null ? (user.getUsername() != null ? user.getUsername() : user.getSocialReason()) : null);

        var items = q.getItems();
        if (items != null && !items.isEmpty()) {
            // Usar directamente los datos almacenados en quotes.items
            // Ya no necesitamos consultar la tabla products
            var rich = new ArrayList<QuoteItem>();
            for (var it : items) {
                var copy = new QuoteItem();
                copy.setProductId(it.getProductId());
                copy.setQuantity(it.getQuantity());

                // Usar los datos que ya están almacenados en la cotización
                copy.setKey(it.getKey() != null ? it.getKey() : it.getProductId());
                copy.setName(it.getName() != null ? it.getName() : "Producto");
                copy.setUnitPrice(it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO);

                rich.add(copy);
            }
            request.setItems(rich);
        } else {
            request.setItems(List.of());
        }
        return request;
    }

    @Transactional
    public List<Quote> getQuotesByUserId(String userId) {
        List<Quote> quotes = quoteRepository.findByUserId(userId);
        return quotes.stream().map(this::enrichQuote).collect(Collectors.toList());
    }

    @Transactional
    public Quote getQuoteById(String id) {
        Quote quote = quoteRepository.findById(id).orElse(null);
        return enrichQuote(quote);
    }

    @Transactional
    public Quote saveQuote(Quote quote) {
        if (quote.getNumber() == null) {
            quote.setNumber(seq.next("quote"));
        }

        // Set creation and expiration dates
        if (quote.getCreatedAt() == null) {
            quote.setCreatedAt(LocalDateTime.now());
        }
        // Convert LocalDateTime to Date for expiredAt calculation
        Date createdDate = Date.from(quote.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
        Calendar cal = Calendar.getInstance();
        cal.setTime(createdDate);
        cal.add(Calendar.DAY_OF_MONTH, quoteValidityDays);
        quote.setExpiredAt(cal.getTime());

        if (quote.getProfitMargin() == null) {
            quote.setProfitMargin(BigDecimal.ZERO);
        }

        if (quote.getUserId() != null) {
            User user = userService.getUserById(quote.getUserId());
            if (user != null) {
                quote.setUsername(
                        user.getUsername() != null ? user.getUsername() : user.getSocialReason()
                );
            }
        }

        // Ya no consultamos la tabla products - usamos los datos que vienen en la cotización
        // Los items ya deben tener toda la información necesaria (key, name, unitPrice)
        // Si faltan datos, se asignan valores por defecto

        for (QuoteItem item : quote.getItems()) {
            // Asegurar que los campos obligatorios tengan valores por defecto si están vacíos
            if (item.getKey() == null || item.getKey().isEmpty()) {
                item.setKey(item.getProductId() != null ? item.getProductId() : "N/A");
            }
            if (item.getName() == null || item.getName().isEmpty()) {
                item.setName("Producto");
            }
            if (item.getUnitPrice() == null) {
                item.setUnitPrice(BigDecimal.ZERO);
            }
        }

        Quote saved = quoteRepository.save(quote);
        return getQuoteById(saved.getId());
    }


    @Transactional
    public void deleteQuote(String id) {
        quoteRepository.deleteById(id);
    }

    private static String esc(String s) {
        return org.apache.commons.text.StringEscapeUtils.escapeXml11(
                org.apache.commons.text.StringEscapeUtils.unescapeHtml4(s == null ? "" : s)
        );
    }

    @Transactional
    public byte[] buildPdf(String quoteId) throws Exception {
        Quote q = getQuoteById(quoteId);
        if (q == null) throw new IllegalArgumentException("Quote not found: " + quoteId);

        String logoHtml = "";
        // 1. Obtener el usuario que creó la cotización
        User creator = null;
        if (q.getUserId() != null) {
            creator = userService.getUserById(q.getUserId());
        }

        // 2. Verificar si el creador es ADMIN y cargar el logo solo en ese caso
        if (creator != null && "ADMIN".equals(creator.getRole())) {
            try {
                Resource resource = new ClassPathResource("LOGO.png");
                if (resource.exists()) {
                    byte[] logoBytes = StreamUtils.copyToByteArray(resource.getInputStream());
                    String logoBase64 = Base64.getEncoder().encodeToString(logoBytes);
                    logoHtml = "<div style='text-align:left; margin: 0 0 20px 10px;'>" +
                            "<img src='data:image/png;base64," + logoBase64 +
                            "' alt='Logo' style='max-height:80px;' />" +
                            "</div>";
                }
            } catch (Exception e) {
                System.err.println("Error al cargar el logo: " + e.getMessage());
                // Continuar sin el logo si hay un error
            }
        }

        User client = null;
        if (q.getClientId() != null) {
            client = userService.getUserById(q.getClientId());
        }

        List<QuoteItem> items = q.getItems();
        boolean hasItems = items != null && !items.isEmpty();

        // Ya no consultamos la tabla products - usamos los datos almacenados en quotes.items
        if (!hasItems) {
            items = new ArrayList<>();
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        int totalQuantity = 0;
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale.Builder().setLanguage("es").setRegion("MX").build());
        StringBuilder itemsHtmlBuilder = new StringBuilder();

        for (QuoteItem item : items) {
            // Usar los datos que ya están almacenados en la cotización
            String key = item.getKey() != null ? item.getKey() : (item.getProductId() != null ? item.getProductId() : "N/A");
            String name = item.getName() != null ? item.getName() : "Producto";
            int qty = item.getQuantity();
            BigDecimal basePrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;

            // Aplicar margen de ganancia al precio unitario
            //BigDecimal marginMultiplier = BigDecimal.ONE.add(q.getProfitMargin().divide(BigDecimal.valueOf(100)));
            BigDecimal profit = q.getProfitMargin() != null ? q.getProfitMargin() : BigDecimal.ZERO;
            BigDecimal marginMultiplier = BigDecimal.ONE.add(profit.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));

            BigDecimal unitPriceWithMargin = basePrice.multiply(marginMultiplier);
            BigDecimal lineTotal = unitPriceWithMargin.multiply(BigDecimal.valueOf(qty));

            subtotal = subtotal.add(lineTotal);
            totalQuantity += qty;

            itemsHtmlBuilder.append("<tr>")
                    .append("<td class='right'>").append(qty).append("</td>")
                    .append("<td>").append(esc(key)).append("</td>")
                    .append("<td>").append(esc(name)).append("</td>")
                    .append("<td class='right'>").append(esc(nf.format(unitPriceWithMargin))).append("</td>")
                    .append("<td class='right'>").append(esc(nf.format(lineTotal))).append("</td>")
                    .append("</tr>");
        }

        // Agregar fila de totales
        itemsHtmlBuilder.append("<tr style='border-top: 2px solid #333; font-weight: bold; background-color: #f8f9fa;'>")
                .append("<td></td>")
                .append("<td colspan='2' style='text-align: right; padding-right: 10px;'><b>TOTALES:</b></td>")
                .append("<td class='right'>—</td>")
                .append("<td class='right'><b>").append(esc(nf.format(subtotal))).append("</b></td>")
                .append("</tr>");

        String itemsHtml = itemsHtmlBuilder.toString();

        // El subtotal ya incluye el margen aplicado a cada producto, así que es el total final
        BigDecimal total = subtotal;

        // Formatear fechas
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String formattedDate = "";
        if (q.getCreatedAt() != null) {
            formattedDate = q.getCreatedAt()
                    .atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(ZoneId.of("America/Mexico_City"))
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"));
        }

        String formattedExpiredAt = "";
        if (q.getExpiredAt() != null) {
            formattedExpiredAt = q.getExpiredAt().toInstant()
                    .atZone(ZoneId.of("America/Mexico_City"))
                    .format(dateFormatter);
        }

        // Folio
        String folio = q.getNumber() != null
                ? "Q-" + String.format("%02d", q.getNumber())
                : "Q-SINNUMERO";

        String html = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE html>\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"es\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\" />\n" +
                "  <title>Cotización</title>\n" +
                "  <style>\n" +
                "    @page { size: A4; margin: 24mm 18mm; }\n" +
                "    body{ font-family: 'Noto Sans', sans-serif; color:#111; margin:0; }\n" +
                "    h1{ font-size:22px; margin:0 0 6px 0; }\n" +
                "    .muted{ color:#666; font-size:12px }\n" +
                "    .box{ border:1px solid #ddd; border-radius:8px; padding:12px; margin-top:10px }\n" +
                "    table{ width:100%; border-collapse:collapse; margin-top:10px }\n" +
                "    th,td{ border-bottom:1px solid #eee; padding:8px; font-size:13px }\n" +
                "    th{ text-align:left; background:#f7f7f7 }\n" +
                "    .right{ text-align:right; }\n" +
                "    .totals{ margin-top:14px; max-width:320px; margin-left:auto }\n" +
                "    .line{ display:flex; justify-content:space-between; padding:6px 0 }\n" +
                "    .big{ font-size:18px; font-weight:bold }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                (logoHtml.isEmpty() ? "" : logoHtml) +
                "    <h1>Cotización</h1>\n" +
                "    <div class=\"muted\">Documento generado automáticamente</div>\n" +
                "    <div class=\"box\">\n" +
                "       <div><b>Cliente:</b> " + esc(client != null && client.getClave() != null ? client.getClave() : "") + "<b></b> " + esc(client != null ? client.getNombre() : "Cliente no especificado") + "</div>\n" +
                "      <div><b>Email:</b> " + esc(client != null ? client.getEmail() : "") + "</div>\n" +
                "      <div><b>Fecha de cotización:</b> " + formattedDate + "</div>\n" +
                "      <div><b>Vigencia hasta:</b> " + formattedExpiredAt + "</div>\n" +
                "      <div><b>Folio:</b> " + folio + "</div>\n" +
                "      <div><b>Estatus:</b> " + esc(q.getStatus()) + "</div>\n" +
                "    </div>\n" +
                "    <table>\n" +
                "      <thead>\n" +
                "        <tr><th class=\"right\">Cantidad</th><th>Clave</th><th>Nombre</th><th class=\"right\">Precio</th><th class=\"right\">Total</th></tr>\n" +
                "      </thead>\n" +
                "      <tbody>" + itemsHtml + "</tbody>\n" +
                "    </table>\n" +
                "    <div class=\"totals\">\n" +
                "      <div class=\"line\"><span><b>Total</b></span><span class=\"big\">" + esc(nf.format(total)) + "</span></div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

        try (var out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();

        }
    }


    private Quote enrichQuote(Quote quote) {
        if (quote == null) return null;

        // username del creador (ya lo tenías)
        if (quote.getUserId() != null) {
            User user = userService.getUserById(quote.getUserId());
            if (user != null) {
                quote.setUsername(user.getUsername() != null ? user.getUsername() : user.getSocialReason());
            }
        }

        if (quote.getClientId() != null) {
            User client = userService.getUserById(quote.getClientId());
            if (client != null) {
                String clientName =
                        client.getNombre() != null ? client.getNombre() :
                                client.getSocialReason() != null ? client.getSocialReason() :
                                        client.getUsername();
                quote.setNombre(clientName);
            }
        }
        return quote;
    }

    @Transactional
    public void sendQuoteEmail(String quoteId, String email) throws Exception {
        // 1. Reutiliza la lógica para obtener la cotización
        Quote q = getQuoteById(quoteId);
        if (q == null) {
            throw new IllegalArgumentException("Cotización no encontrada: " + quoteId);
        }

        // Valida que el email no sea nulo o vacío
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("No se proporcionó un correo electrónico para el envío.");
        }

        // 2. Reutiliza la lógica para generar el PDF
        byte[] pdfBytes = buildPdf(quoteId);

        // Prepara los datos para el correo
        String subject = "Confirmación de Cotización" + (q.getNumber() != null ? " #" + q.getNumber() : q.getId());
        String body = "Estimado Cliente General,\n" +
                "\n" +
                "Le adjuntamos la cotización correspondiente.\n" +
                "\n" +
                "Saludos cordiales,\n" +
                "El equipo de ventas";
        // El nombre del adjunto será manejado por EmailService

        // 3. Llama al servicio de correo con los 6 argumentos correctos
        emailService.sendEmailWithAttachment(email, subject, body, body, pdfBytes, "application/pdf");
    }

    // Helper para cálculo de precio con margen (visible en package para tests)
    BigDecimal computeUnitPriceWithMargin(BigDecimal basePrice, BigDecimal profitPercent) {
        BigDecimal base = basePrice != null ? basePrice : BigDecimal.ZERO;
        BigDecimal profit = profitPercent != null ? profitPercent : BigDecimal.ZERO;

        BigDecimal multiplier = BigDecimal.ONE.add(
                profit.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
        );
        return base.multiply(multiplier);
    }
}
