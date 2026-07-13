package com.equinox.EquinoxGym;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    @Query("SELECT COALESCE(SUM(p.monto), 0) FROM Pago p WHERE p.fechaPago BETWEEN :inicio AND :fin")
    BigDecimal obtenerTotalRecaudadoDelMes(@Param("inicio") LocalDate inicio,
                                           @Param("fin") LocalDate fin);

    long countByFechaPagoBetween(LocalDate inicio, LocalDate fin);

    List<Pago> findByMedioPago(String medioPago);

    List<Pago> findByFechaPago(LocalDate fechaPago);

    List<Pago> findByMedioPagoAndFechaPago(String medioPago, LocalDate fechaPago);

    List<Pago> findByCuota_Socio_Id(Long socioId);

    List<Pago> findByCuota_Socio_IdAndMedioPago(Long socioId, String medioPago);

    List<Pago> findByCuota_Socio_IdAndFechaPago(Long socioId, LocalDate fechaPago);

    List<Pago> findByCuota_Socio_IdAndMedioPagoAndFechaPago(Long socioId, String medioPago, LocalDate fechaPago);

    boolean existsByCuota_Id(Long cuotaId);

    @Transactional
    void deleteByCuota_Id(Long cuotaId);
}
