package com.equinox.EquinoxGym;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CajaRepository extends JpaRepository<CajaSesion, Long> {

    Optional<CajaSesion> findFirstByUsuarioAperturaAndEstadoOrderByFechaAperturaDesc(
            String usuarioApertura, EstadoCaja estado);

    List<CajaSesion> findTop20ByEstadoOrderByFechaCierreDesc(EstadoCaja estado);

    List<CajaSesion> findTop20ByUsuarioAperturaAndEstadoOrderByFechaCierreDesc(
            String usuarioApertura, EstadoCaja estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CajaSesion c WHERE c.id = :id")
    Optional<CajaSesion> findByIdForUpdate(@Param("id") Long id);
}
