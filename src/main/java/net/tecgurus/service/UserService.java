package net.tecgurus.service;

import net.tecgurus.controller.exception.LastAdminException;
import net.tecgurus.model.User;
import net.tecgurus.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired private UserRepository userRepository;
    @Autowired private MongoTemplate mongoTemplate;

    @Autowired private PasswordEncoder passwordEncoder;


    @Transactional
    public User getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String)) {
            String username = auth.getName();
            return userRepository.findByUsername(username).orElse(null);
        }
        return null;
    }

    @Transactional
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User getUserById(String id) {
        return userRepository.findById(id).orElse(null);
    }

    @Transactional
    public User saveUser(User user) {
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }
        if (user.getActive() == null) {
            user.setActive(true);
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            String p = user.getPassword();
            boolean looksLikeBCrypt = p.startsWith("$2a$") || p.startsWith("$2b$") || p.startsWith("$2y$");
            if (!looksLikeBCrypt) {
                user.setPassword(passwordEncoder.encode(p));
            }
        }
        return userRepository.save(user);
    }

    //Si o si al dar de alta un nuevo admin se tiene que poner en el
    // estatus como activo para que se haga la consulta a la bd por medio del repositorio
    @Transactional
    public void deleteUser(String id) {
        User u = getUserById(id);
        if (u == null) {
            throw new IllegalArgumentException("Usuario no encontrado: " + id);
        }

        // Si es ADMIN activo, valida que no sea el último
        if ("ADMIN".equals(u.getRole()) && Boolean.TRUE.equals(u.getActive())) {
            long adminsActivos = userRepository.countByRoleAndActive("ADMIN", true);
            if (adminsActivos <= 1) {
                throw new LastAdminException("No se puede eliminar al último administrador.");
            }
        }

        userRepository.deleteById(id);
    }

    @Transactional
    public Page<User> getFilteredUsers(
            String username,
            String socialReason,
            String email,
            String direction,
            String role,
            Boolean active,
            String clave,
            String nombre,
            String rfc,
            String calle,
            String codigoPostal,
            String telefono,
            String claveVendedor,
            String regimenFiscal,
            String estadoTimbrado,
            String nombreComercial,
            int page,
            int size,
            String sort,
            String dir
    ) {
        // Validar y normalizar parámetros de ordenamiento
        Sort sortObj = validateAndCreateSort(sort, dir);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        // Crear consulta
        Query query = buildQuery(
                username, socialReason, email, direction, role, active,
                clave, nombre, rfc, calle, codigoPostal, telefono,
                claveVendedor, regimenFiscal, estadoTimbrado, nombreComercial
        );

        // Ejecutar consulta
        long total = mongoTemplate.count(query, User.class);
        query.with(pageable);
        List<User> users = mongoTemplate.find(query, User.class);

        return new PageImpl<>(users, pageable, total);
    }

    private Sort validateAndCreateSort(String sort, String dir) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "createdAt");
        }

        // Lista de campos permitidos para ordenar
        List<String> allowedFields = List.of(
                "username", "socialReason", "email", "direction", "role",
                "active", "createdAt", "codigoPostal", "clave", "nombre",
                "rfc", "calle", "telefono", "claveVendedor", "regimenFiscal",
                "estadoTimbrado", "nombreComercial", "estado"  // Added "estado" here
        );

        // Validar campo de ordenamiento
        if (!allowedFields.contains(sort)) {
            sort = "createdAt";
        }

        // Validar dirección de ordenamiento
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ?
                Sort.Direction.DESC : Sort.Direction.ASC;

        return Sort.by(direction, sort);
    }

    private Query buildQuery(
            String username, String socialReason, String email,
            String direction, String role, Boolean active,
            String clave, String nombre, String rfc, String calle,
            String codigoPostal, String telefono, String claveVendedor,
            String regimenFiscal, String estadoTimbrado, String nombreComercial
    ) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        // Agregar criterios de búsqueda
        addCriteriaIfNotEmpty(criteriaList, "username", username, true);
        addCriteriaIfNotEmpty(criteriaList, "socialReason", socialReason, true);
        addCriteriaIfNotEmpty(criteriaList, "email", email, true);
        addCriteriaIfNotEmpty(criteriaList, "direction", direction, true);
        addCriteriaIfNotEmpty(criteriaList, "role", role, false); // Búsqueda exacta para role
        addCriteriaIfNotEmpty(criteriaList, "clave", clave, true);
        addCriteriaIfNotEmpty(criteriaList, "nombre", nombre, true);
        addCriteriaIfNotEmpty(criteriaList, "rfc", rfc, true);
        addCriteriaIfNotEmpty(criteriaList, "calle", calle, true);
        addCriteriaIfNotEmpty(criteriaList, "codigoPostal", codigoPostal, true);
        addCriteriaIfNotEmpty(criteriaList, "telefono", telefono, true);
        addCriteriaIfNotEmpty(criteriaList, "claveVendedor", claveVendedor, true);
        addCriteriaIfNotEmpty(criteriaList, "regimenFiscal", regimenFiscal, true);
        addCriteriaIfNotEmpty(criteriaList, "estadoTimbrado", estadoTimbrado, false); // Búsqueda exacta
        addCriteriaIfNotEmpty(criteriaList, "nombreComercial", nombreComercial, true);

        // Manejo especial para el campo active
        if (active != null) {
            criteriaList.add(Criteria.where("active").is(active));
        }

        // Combinar criterios con AND
        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        return query;
    }

    private void addCriteriaIfNotEmpty(List<Criteria> criteriaList, String field, String value, boolean useRegex) {
        if (value != null && !value.isBlank()) {
            if (useRegex) {
                criteriaList.add(Criteria.where(field).regex(value, "i"));
            } else {
                criteriaList.add(Criteria.where(field).is(value));
            }
        }
    }

    @Transactional
    public User toggleActive(String id) {
        return userRepository.findById(id)
                .map(user -> {
                    boolean current = Boolean.TRUE.equals(user.getActive());
                    // Si está activo y es ADMIN, y lo vas a desactivar
                    if (current && "ADMIN".equals(user.getRole())) {
                        long adminsActivos = userRepository.countByRoleAndActive("ADMIN", true);
                        if (adminsActivos <= 1) {
                            throw new LastAdminException("No se puede desactivar al último administrador.");
                        }
                    }
                    user.setActive(!current);
                    return userRepository.save(user);
                })
                .orElse(null);
    }

    @Transactional
    public User updateUser(String id, User incoming) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) return null;
        User existing = opt.get();

        // Estado actual
        boolean eraAdminActivo = "ADMIN".equals(existing.getRole()) && Boolean.TRUE.equals(existing.getActive());

        // Estado resultante (usa incoming si viene, si no, el actual)
        String newRole = incoming.getRole() != null ? incoming.getRole() : existing.getRole();
        Boolean newActive = incoming.getActive() != null ? incoming.getActive() : existing.getActive();
        boolean seraAdminActivo = "ADMIN".equals(newRole) && Boolean.TRUE.equals(newActive);

        // Si pasa de ADMIN activo → a no-admin o a inactivo, valida último
        if (eraAdminActivo && !seraAdminActivo) {
            long adminsActivos = userRepository.countByRoleAndActive("ADMIN", true);
            if (adminsActivos <= 1) {
                throw new LastAdminException("No se puede degradar o desactivar al último administrador.");
            }
        }

        if (incoming.getUsername() != null && !incoming.getUsername().isBlank()) existing.setUsername(incoming.getUsername());
        if (incoming.getEmail() != null) existing.setEmail(incoming.getEmail());
        if (incoming.getRole() != null) existing.setRole(incoming.getRole());
        if (incoming.getDirection() != null) existing.setDirection(incoming.getDirection());
        if (incoming.getSocialReason() != null) existing.setSocialReason(incoming.getSocialReason());
        if (incoming.getClave() != null) existing.setClave(incoming.getClave());
        if (incoming.getEstatus() != null) existing.setEstatus(incoming.getEstatus());
        if (incoming.getNombre() != null) existing.setNombre(incoming.getNombre());
        if (incoming.getRfc() != null) existing.setRfc(incoming.getRfc());
        if (incoming.getCalle() != null) existing.setCalle(incoming.getCalle());
        if (incoming.getCodigoPostal() != null) existing.setCodigoPostal(incoming.getCodigoPostal());
        if (incoming.getEstado() != null) existing.setEstado(incoming.getEstado());
        if (incoming.getTelefono() != null) existing.setTelefono(incoming.getTelefono());
        if (incoming.getClaveVendedor() != null) existing.setClaveVendedor(incoming.getClaveVendedor());
        if (incoming.getRegimenFiscal() != null) existing.setRegimenFiscal(incoming.getRegimenFiscal());
        if (incoming.getEstadoTimbrado() != null) existing.setEstadoTimbrado(incoming.getEstadoTimbrado());
        if (incoming.getNombreComercial() != null) existing.setNombreComercial(incoming.getNombreComercial());
        if (incoming.getActive() != null) existing.setActive(incoming.getActive());
        if (incoming.getCustomPrices() != null) existing.setCustomPrices(incoming.getCustomPrices());
        if (incoming.getIsGeneral() != null) existing.setIsGeneral(incoming.getIsGeneral());

        if (incoming.getPassword() != null && !incoming.getPassword().isBlank()) {
            String p = incoming.getPassword();
            boolean looksLikeBCrypt = p.startsWith("$2a$") || p.startsWith("$2b$") || p.startsWith("$2y$");
            if (!looksLikeBCrypt) {
                existing.setPassword(passwordEncoder.encode(p));
            } else {
                existing.setPassword(p);
            }
        }
        if (existing.getCreatedAt() == null) {
            existing.setCreatedAt(LocalDateTime.now());
        }
        return userRepository.save(existing);
    }

  public List<User> findByNameLike(String term) {
    if (term == null || term.isBlank()) return List.of();
    Query q = new Query(new Criteria().orOperator(
      Criteria.where("nombre").regex(term, "i"),
      Criteria.where("socialReason").regex(term, "i"),
      Criteria.where("username").regex(term, "i")
    ));
    // si manejas roles y solo quieres clientes: q.addCriteria(Criteria.where("role").is("CLIENT"));
    return mongoTemplate.find(q, User.class);
  }
}

