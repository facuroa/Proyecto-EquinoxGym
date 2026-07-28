package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoServiceTests {

    private final SocioService socioService = new SocioService(null, new CuotaService());

    @Test
    void cuotaImpagaHastaElVencimientoSigueVigente() {
        Cuota cuota = cuota(LocalDate.now());

        new CuotaService().actualizarEstadoCuota(cuota);

        assertThat(cuota.getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
    }

    @Test
    void socioConMoraMenorAVeinteDiasEsMoroso() {
        Socio socio = socioConCuota(LocalDate.now().minusDays(19));

        socioService.actualizarEstadoSocio(socio);

        assertThat(socio.getEstado()).isEqualTo(EstadoSocio.MOROSO);
    }

    @Test
    void socioConVeinteDiasDeMoraEsInactivo() {
        Socio socio = socioConCuota(LocalDate.now().minusDays(20));

        socioService.actualizarEstadoSocio(socio);

        assertThat(socio.getEstado()).isEqualTo(EstadoSocio.INACTIVO);
    }

    private Socio socioConCuota(LocalDate vencimiento) {
        Socio socio = new Socio();
        socio.getCuotas().add(cuota(vencimiento));
        return socio;
    }

    private Cuota cuota(LocalDate vencimiento) {
        Cuota cuota = new Cuota();
        cuota.setFechaVencimiento(vencimiento);
        cuota.setMonto(BigDecimal.TEN);
        cuota.setEstado(EstadoCuota.PENDIENTE);
        return cuota;
    }
}
