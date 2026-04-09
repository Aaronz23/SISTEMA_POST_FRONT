package net.tecgurus.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Data
@Document(collection = "users")
public class User implements UserDetails {

    @Id
    private String id;

    private String username;
    private String password;
    private String role; // "CLIENT" or "ADMIN"

    // Personal data
    private String direction;
    private String email;
    private String socialReason;

    private String clave;
    private String estatus;          // O boolean si decides manejar activo/inactivo diferente de `active`
    private String nombre;
    private String rfc;
    private String calle;
    private String codigoPostal;
    private String estado;
    private String telefono;
    private String claveVendedor;
    private String regimenFiscal;
    private String estadoTimbrado;
    private String nombreComercial;

    private List<CustomPrice> customPrices;
    private Boolean isGeneral; // Para marcar si es cliente general
    private Boolean active;     // Estado activo/inactivo
    private LocalDateTime createdAt;

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username; // Use username as the username for authentication
    }


    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return active;
    }
}