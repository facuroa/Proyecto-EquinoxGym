package com.equinox.EquinoxGym;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocioRepository extends JpaRepository<Socio, Long> {
    long countByEstado(EstadoSocio estado);

    Optional<Socio> findByDni(String dni);

    List<Socio> findByNombreContainingIgnoreCaseOrApellidoContainingIgnoreCaseOrDniContainingIgnoreCase(
            String nombre, String apellido, String dni);

    @Query("""
            SELECT s FROM Socio s
            WHERE (:estado IS NULL OR s.estado = :estado)
              AND (:buscar = '' OR
                   LOWER(COALESCE(s.nombre, '')) LIKE LOWER(CONCAT('%', :buscar, '%')) OR
                   LOWER(COALESCE(s.apellido, '')) LIKE LOWER(CONCAT('%', :buscar, '%')) OR
                   LOWER(COALESCE(s.dni, '')) LIKE LOWER(CONCAT('%', :buscar, '%')))
            """)
    Page<Socio> buscarPaginado(@Param("buscar") String buscar,
                               @Param("estado") EstadoSocio estado,
                               Pageable pageable);
}
