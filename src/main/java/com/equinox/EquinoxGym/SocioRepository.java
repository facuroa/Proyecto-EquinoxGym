package com.equinox.EquinoxGym;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocioRepository extends JpaRepository<Socio, Long> {
    long countByEstado(EstadoSocio estado);

    Optional<Socio> findByDni(String dni);

    List<Socio> findByNombreContainingIgnoreCaseOrApellidoContainingIgnoreCaseOrDniContainingIgnoreCase(
            String nombre, String apellido, String dni);
}