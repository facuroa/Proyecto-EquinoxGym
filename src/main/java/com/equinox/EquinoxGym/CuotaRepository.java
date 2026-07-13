package com.equinox.EquinoxGym;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface CuotaRepository extends JpaRepository<Cuota, Long> {

    long countByEstado(EstadoCuota estado);

    long countByEstadoAndFechaPagoBetween(EstadoCuota estado, LocalDate desde, LocalDate hasta);

    List<Cuota> findByEstado(EstadoCuota estado);

    List<Cuota> findByFechaVencimientoBeforeAndEstadoNot(LocalDate fecha, EstadoCuota estado);

    List<Cuota> findByFechaPagoIsNullOrderByFechaVencimientoAsc();

    List<Cuota> findTop6ByFechaPagoIsNullAndFechaVencimientoBeforeOrderByFechaVencimientoAsc(LocalDate fecha);

    List<Cuota> findTop6ByFechaPagoIsNullAndFechaVencimientoBetweenOrderByFechaVencimientoAsc(
            LocalDate desde, LocalDate hasta);

    long countByFechaPagoIsNullAndFechaVencimientoBetween(LocalDate desde, LocalDate hasta);

    boolean existsBySocio_IdAndFechaVencimiento(Long socioId, LocalDate fechaVencimiento);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cuota c WHERE c.id = :id")
    Optional<Cuota> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(c.monto), 0) FROM Cuota c WHERE c.estado = 'PAGADA' AND c.fechaPago BETWEEN :desde AND :hasta")
    BigDecimal sumarRecaudadoDelPeriodo(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Query("""
            SELECT c FROM Cuota c
            WHERE (:estado IS NULL OR c.estado = :estado)
              AND (:buscar = '' OR
                   LOWER(COALESCE(c.socio.nombre, '')) LIKE LOWER(CONCAT('%', :buscar, '%')) OR
                   LOWER(COALESCE(c.socio.apellido, '')) LIKE LOWER(CONCAT('%', :buscar, '%')) OR
                   LOWER(COALESCE(c.socio.dni, '')) LIKE LOWER(CONCAT('%', :buscar, '%')))
            """)
    Page<Cuota> buscarPaginado(@Param("buscar") String buscar,
                               @Param("estado") EstadoCuota estado,
                               Pageable pageable);
}
