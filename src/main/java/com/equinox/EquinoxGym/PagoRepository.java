package com.equinox.EquinoxGym;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    @Query("SELECT COALESCE(SUM(p.monto), 0) FROM Pago p WHERE p.anulado = false AND p.fechaPago BETWEEN :inicio AND :fin")
    BigDecimal obtenerTotalRecaudadoDelMes(@Param("inicio") LocalDate inicio,
                                           @Param("fin") LocalDate fin);

    long countByFechaPagoBetweenAndAnuladoFalse(LocalDate inicio, LocalDate fin);

    List<Pago> findAllByOrderByFechaPagoDescIdDesc();

    List<Pago> findByAnuladoFalseOrderByFechaPagoAscIdAsc();

    List<Pago> findTop6ByAnuladoFalseAndFechaPagoOrderByFechaRegistroDescIdDesc(LocalDate fechaPago);

    long countByFechaPagoAndAnuladoFalse(LocalDate fechaPago);

    List<Pago> findByMedioPago(String medioPago);

    List<Pago> findByFechaPago(LocalDate fechaPago);

    List<Pago> findByMedioPagoAndFechaPago(String medioPago, LocalDate fechaPago);

    List<Pago> findByCuota_Socio_Id(Long socioId);

    List<Pago> findByCuota_Socio_IdOrderByFechaPagoDescIdDesc(Long socioId);

    Optional<Pago> findFirstByCuota_Socio_IdAndAnuladoFalseOrderByFechaRegistroDescIdDesc(Long socioId);

    List<Pago> findByCuota_Socio_IdAndMedioPago(Long socioId, String medioPago);

    List<Pago> findByCuota_Socio_IdAndFechaPago(Long socioId, LocalDate fechaPago);

    List<Pago> findByCuota_Socio_IdAndMedioPagoAndFechaPago(Long socioId, String medioPago, LocalDate fechaPago);

    boolean existsByCuota_Id(Long cuotaId);

    boolean existsByCuota_IdAndAnuladoFalse(Long cuotaId);

    boolean existsByCuota_Socio_Id(Long socioId);

    boolean existsByCuotaRenovacionGenerada_Id(Long cuotaId);

    @Query("""
            SELECT p FROM Pago p
            WHERE (:buscar = '' OR
                   LOWER(COALESCE(p.cuota.socio.nombre, '')) LIKE LOWER(CONCAT('%', :buscar, '%')) OR
                   LOWER(COALESCE(p.cuota.socio.apellido, '')) LIKE LOWER(CONCAT('%', :buscar, '%')) OR
                   LOWER(COALESCE(p.cuota.socio.dni, '')) LIKE LOWER(CONCAT('%', :buscar, '%')))
              AND (:medioPago = '' OR p.medioPago = :medioPago)
              AND (:desde IS NULL OR p.fechaPago >= :desde)
              AND (:hasta IS NULL OR p.fechaPago <= :hasta)
              AND (:anulado IS NULL OR p.anulado = :anulado)
            """)
    Page<Pago> buscarPaginado(@Param("buscar") String buscar,
                              @Param("medioPago") String medioPago,
                              @Param("desde") LocalDate desde,
                              @Param("hasta") LocalDate hasta,
                              @Param("anulado") Boolean anulado,
                              Pageable pageable);

}
