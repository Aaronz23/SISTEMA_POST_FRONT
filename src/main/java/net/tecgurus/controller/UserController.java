package net.tecgurus.controller;

import net.tecgurus.config.JwtTokenProvider;
import net.tecgurus.controller.exception.LastAdminException;
import net.tecgurus.model.CustomPrice;
import net.tecgurus.model.User;
import net.tecgurus.repository.CustomPriceRepository;
import net.tecgurus.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired private UserService userService;

    @Autowired private CustomPriceRepository customPriceRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private JwtTokenProvider tokenProvider;


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User user) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        user.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);
        return ResponseEntity.ok(Map.of("token", jwt));
    }



    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        User user = userService.getUserById(id);
        if (user != null) {
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User createdUser = userService.saveUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }



    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody User user) {
        User updated = userService.updateUser(id, user);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
                try {
                      userService.deleteUser(id);
                        return ResponseEntity.noContent().build();
                    } catch (LastAdminException e) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(Map.of("message", e.getMessage()));
                    }
            }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(@RequestHeader("username") String username) {
        Optional<User> foundUser = userService.findByUsername(username);
        if (foundUser.isPresent()) {
            return ResponseEntity.ok(foundUser.get());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
    }

    @GetMapping("/{id}/prices")
    public ResponseEntity<List<CustomPrice>> getCustomPricesByUser(@PathVariable String id) {
        List<CustomPrice> prices = customPriceRepository.findByUserId(id);
        return ResponseEntity.ok(prices);
    }

    @PutMapping("/{id}/prices")
    public ResponseEntity<?> updateCustomPrices(@PathVariable String id, @RequestBody List<CustomPrice> prices) {
        customPriceRepository.deleteByUserId(id);
        for (CustomPrice price : prices) {
            price.setUserId(id); // 👈 aquí se asegura
        }
        List<CustomPrice> saved = customPriceRepository.saveAll(prices);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public Page<User> getUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String socialReason,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String clave,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String rfc,
            @RequestParam(required = false) String calle,
            @RequestParam(required = false) String codigoPostal,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false) String claveVendedor,
            @RequestParam(required = false) String regimenFiscal,
            @RequestParam(required = false) String estadoTimbrado,
            @RequestParam(required = false) String nombreComercial,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false, defaultValue = "asc") String dir
    ) {
        return userService.getFilteredUsers(
                username, socialReason, email, direction, role, active,
                clave, nombre, rfc, calle, codigoPostal, telefono,
                claveVendedor, regimenFiscal, estadoTimbrado, nombreComercial,
                page, size, sort, dir
        );
    }




    private Sort buildSort(String sort, String dir) {
        if (sort == null || sort.isBlank()) return Sort.unsorted();
        List<String> allowed = List.of("username","socialReason","email","direction","role","active","createdAt");
        if (!allowed.contains(sort)) sort = "createdAt";
        Sort.Direction d = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(d, sort);
    }



    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<User> toggleActive(@PathVariable String id) {
        User updated = userService.toggleActive(id);
        return (updated != null)
                ? ResponseEntity.ok(updated)
                : ResponseEntity.notFound().build();
    }








}
