package com.equinox.EquinoxGym;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    @EntityGraph(attributePaths = "socio")
    List<Asistencia> findByFechaOrderByFechaHoraDescIdDesc(LocalDate fecha);

    List<Asistencia> findBySocio_IdAndFechaOrderByNumeroTurnoAsc(Long socioId, LocalDate fecha);

    long countBySocio_IdAndFechaBetween(Long socioId, LocalDate desde, LocalDate hasta);

    @Query("""
            SELECT DISTINCT a.fecha FROM Asistencia a
            WHERE a.socio.id = :socioId AND a.fecha >= :desde
            ORDER BY a.fecha
            """)
    List<LocalDate> fechasDeSocioDesde(@Param("socioId") Long socioId, @Param("desde") LocalDate desde);

    @Query("SELECT MAX(a.fecha) FROM Asistencia a WHERE a.socio.id = :socioId")
    LocalDate ultimaFechaDeSocio(@Param("socioId") Long socioId);

    /** Primer dia con ingresos cargados: marca desde cuando hay datos para medir. */
    @Query("SELECT MIN(a.fecha) FROM Asistencia a")
    LocalDate primeraFechaRegistrada();

    /**
     * Solo los horarios, sin traer socios ni cuotas: el historial recorre
     * semanas enteras y con esto alcanza para armar los promedios.
     */
    @Query("SELECT a.fechaHora FROM Asistencia a WHERE a.fecha BETWEEN :desde AND :hasta")
    List<LocalDateTime> horariosEntre(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Pares (id del socio, fecha de su ultimo ingreso). */
    @Query("SELECT a.socio.id, MAX(a.fecha) FROM Asistencia a GROUP BY a.socio.id")
    List<Object[]> ultimaFechaPorSocio();

    long countBySocio_Id(Long socioId);

    void deleteBySocio_Id(Long socioId);
}
