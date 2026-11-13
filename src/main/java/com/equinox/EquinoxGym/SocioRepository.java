package com.equinox.EquinoxGym;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocioRepository extends JpaRepository<Socio, Long> {
    long countByEstado(EstadoSocio estado);

    /*
     * Para chequear duplicados se pregunta "existe", nunca se trae "el" socio
     * con ese DNI: si la base ya tiene dos iguales, traer uno solo rompe.
     */
    boolean existsByDni(String dni);

    boolean existsByDniAndIdNot(String dni, Long id);

    /**
     * DNI cargados en mas de un socio (de antes del control por doble clic).
     * Mientras exista alguno, la base no puede crear el indice unico.
     */
    @Query("""
            SELECT s.dni FROM Socio s
            WHERE s.dni IS NOT NULL AND s.dni <> ''
            GROUP BY s.dni HAVING COUNT(s) > 1
            ORDER BY s.dni
            """)
    List<String> dnisRepetidos();

    List<Socio> findByEstadoAndBajaFalse(EstadoSocio estado);

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
