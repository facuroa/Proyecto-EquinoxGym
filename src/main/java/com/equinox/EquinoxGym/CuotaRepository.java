package com.equinox.EquinoxGym;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface CuotaRepository extends JpaRepository<Cuota, Long> {

    long countByEstado(EstadoCuota estado);

    long countByEstadoAndFechaPagoBetween(EstadoCuota estado, LocalDate desde, LocalDate hasta);

    List<Cuota> findByEstado(EstadoCuota estado);

    List<Cuota> findByFechaVencimientoBeforeAndEstadoNot(LocalDate fecha, EstadoCuota estado);

    List<Cuota> findByFechaPagoIsNullOrderByFechaVencimientoAsc();

    boolean existsBySocio_IdAndFechaVencimiento(Long socioId, LocalDate fechaVencimiento);

    @Query("SELECT COALESCE(SUM(c.monto), 0) FROM Cuota c WHERE c.estado = 'PAGADA' AND c.fechaPago BETWEEN :desde AND :hasta")
    BigDecimal sumarRecaudadoDelPeriodo(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
