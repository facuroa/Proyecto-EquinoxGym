package com.equinox.EquinoxGym;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeguimientoMorosidadRepository extends JpaRepository<SeguimientoMorosidad, Long> {

    @Query("""
            SELECT s FROM SeguimientoMorosidad s
            WHERE s.socio.id IN :sociosIds
            ORDER BY s.fechaRegistro DESC, s.id DESC
            """)
    List<SeguimientoMorosidad> buscarPorSocios(@Param("sociosIds") List<Long> sociosIds);

    List<SeguimientoMorosidad> findBySocio_IdOrderByFechaRegistroDescIdDesc(Long socioId);

    void deleteBySocio_Id(Long socioId);
}
