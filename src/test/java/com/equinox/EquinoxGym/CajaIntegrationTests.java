package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class CajaIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CajaService cajaService;

    @Autowired
    private CajaRepository cajaRepository;

    @Autowired
    private MovimientoCajaRepository movimientoRepository;

    @Autowired
    private CobroService cobroService;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Test
    void cajaRequiereInicioDeSesion() throws Exception {
        mockMvc.perform(get("/caja"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "recepcion-caja", roles = "RECEPCIONISTA")
    void recepcionPuedeAbrirRegistrarYCerrarSuJornada() throws Exception {
        mockMvc.perform(get("/caja"))
                .andExpect(status().isOk())
                .andExpect(view().name("caja"))
                .andExpect(model().attributeExists("resumen"))
                .andExpect(content().string(containsString("Apertura de caja")));

        mockMvc.perform(post("/caja/abrir")
                        .with(csrf())
                        .param("montoInicial", "10000")
                        .param("observaciones", "Inicio del turno mañana"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/caja"));

        CajaSesion caja = cajaRepository
                .findFirstByUsuarioAperturaAndEstadoOrderByFechaAperturaDesc(
                        "recepcion-caja", EstadoCaja.ABIERTA)
                .orElseThrow();

        mockMvc.perform(post("/caja/movimientos")
                        .with(csrf())
                        .param("tipo", "INGRESO")
                        .param("monto", "2000")
                        .param("concepto", "Cambio adicional"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/caja/movimientos")
                        .with(csrf())
                        .param("tipo", "EGRESO")
                        .param("monto", "500")
                        .param("concepto", "Compra de limpieza"))
                .andExpect(status().is3xxRedirection());

        CajaResumenDTO resumen = cajaService.obtenerResumen("recepcion-caja", false);
        assertThat(resumen.getSaldoEsperado()).isEqualByComparingTo("11500.00");
        assertThat(resumen.getMovimientos()).hasSize(2);

        mockMvc.perform(get("/caja"))
                .andExpect(status().isOk())
                .andExpect(view().name("caja"))
                .andExpect(content().string(containsString("Efectivo esperado")))
                .andExpect(content().string(containsString("Transferencias")))
                .andExpect(content().string(containsString("Tarjetas")))
                .andExpect(content().string(containsString("Registrar movimiento")))
                .andExpect(content().string(containsString("Cerrar jornada")))
                .andExpect(content().string(containsString("Compra de limpieza")));

        mockMvc.perform(post("/caja/cerrar")
                        .with(csrf())
                        .param("cajaId", caja.getId().toString())
                        .param("montoDeclarado", "11400")
                        .param("observaciones", "Faltante revisado"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/caja"));

        CajaSesion cerrada = cajaRepository.findById(caja.getId()).orElseThrow();
        assertThat(cerrada.getEstado()).isEqualTo(EstadoCaja.CERRADA);
        assertThat(cerrada.getMontoEsperadoCierre()).isEqualByComparingTo("11500.00");
        assertThat(cerrada.getMontoDeclaradoCierre()).isEqualByComparingTo("11400.00");
        assertThat(cerrada.getDiferenciaCierre()).isEqualByComparingTo("-100.00");

        mockMvc.perform(get("/caja"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mi historial")))
                .andExpect(content().string(containsString("Apertura de caja")));
    }

    @Test
    @Transactional
    @WithMockUser(username = "cajero-efectivo", roles = "RECEPCIONISTA")
    void cobroYAnulacionEnEfectivoImpactanAutomaticamenteEnCaja() {
        CajaSesion caja = cajaService.abrirCaja("cajero-efectivo", new BigDecimal("5000"), null);
        Cuota cuota = guardarCuotaConSocio("Cobro caja", "30000");

        Pago pago = cobroService.registrarPago(cuota, new BigDecimal("30000"), "Efectivo");

        List<MovimientoCaja> despuesDelCobro = movimientoRepository
                .findByCaja_IdOrderByFechaRegistroDescIdDesc(caja.getId());
        assertThat(despuesDelCobro).hasSize(1);
        assertThat(despuesDelCobro.get(0).getOrigen()).isEqualTo(OrigenMovimientoCaja.PAGO_EFECTIVO);
        assertThat(despuesDelCobro.get(0).getPago().getId()).isEqualTo(pago.getId());
        assertThat(cajaService.obtenerResumen("cajero-efectivo", false).getSaldoEsperado())
                .isEqualByComparingTo("35000.00");

        cobroService.anularPago(pago.getId(), "Cobro cargado por error");

        List<MovimientoCaja> despuesDeAnular = movimientoRepository
                .findByCaja_IdOrderByFechaRegistroDescIdDesc(caja.getId());
        assertThat(despuesDeAnular).hasSize(2);
        assertThat(despuesDeAnular).extracting(MovimientoCaja::getOrigen)
                .containsExactly(OrigenMovimientoCaja.ANULACION_PAGO, OrigenMovimientoCaja.PAGO_EFECTIVO);
        assertThat(cajaService.obtenerResumen("cajero-efectivo", false).getSaldoEsperado())
                .isEqualByComparingTo("5000.00");
    }

    @Test
    @Transactional
    @WithMockUser(username = "cajero-sin-apertura", roles = "RECEPCIONISTA")
    void cualquierPagoExigeUnaJornadaDeCajaAbierta() {
        Cuota cuotaEfectivo = guardarCuotaConSocio("Sin apertura", "22000");

        assertThatThrownBy(() -> cobroService.registrarPago(
                cuotaEfectivo, new BigDecimal("22000"), "Efectivo"))
                .isInstanceOf(CajaCerradaException.class)
                .hasMessageContaining("Abrí tu caja");
        assertThat(cuotaRepository.findById(cuotaEfectivo.getId()).orElseThrow().getFechaPago()).isNull();
        assertThat(pagoRepository.findByCuota_Socio_Id(cuotaEfectivo.getSocio().getId())).isEmpty();
    }

    @Test
    @Transactional
    @WithMockUser(username = "cajero-transferencia", roles = "RECEPCIONISTA")
    void transferenciaSeReflejaEnCajaSinAlterarElEfectivoEsperado() {
        CajaSesion caja = cajaService.abrirCaja(
                "cajero-transferencia", new BigDecimal("1000"), null);
        Cuota cuotaTransferencia = guardarCuotaConSocio("Transferencia", "24000");

        Pago pago = cobroService.registrarPago(
                cuotaTransferencia, new BigDecimal("24000"), "Transferencia");

        assertThat(pago.getId()).isNotNull();
        List<MovimientoCaja> movimientos = movimientoRepository
                .findByCaja_IdOrderByFechaRegistroDescIdDesc(caja.getId());
        assertThat(movimientos).hasSize(1);
        assertThat(movimientos.get(0).getOrigen())
                .isEqualTo(OrigenMovimientoCaja.PAGO_TRANSFERENCIA);

        CajaResumenDTO resumen = cajaService.obtenerResumen("cajero-transferencia", false);
        assertThat(resumen.getTransferencias()).isEqualByComparingTo("24000.00");
        assertThat(resumen.getSaldoEsperado()).isEqualByComparingTo("1000.00");

        cobroService.anularPago(pago.getId(), "Transferencia cargada por error");
        CajaResumenDTO despuesDeAnular = cajaService.obtenerResumen("cajero-transferencia", false);
        assertThat(despuesDeAnular.getTransferencias()).isEqualByComparingTo("0.00");
        assertThat(despuesDeAnular.getSaldoEsperado()).isEqualByComparingTo("1000.00");
    }

    @Test
    @Transactional
    @WithMockUser(username = "cajero-duplicado", roles = "RECEPCIONISTA")
    void noPermiteDosCajasAbiertasParaElMismoUsuario() {
        cajaService.abrirCaja("cajero-duplicado", BigDecimal.ZERO, null);

        assertThatThrownBy(() -> cajaService.abrirCaja(
                "cajero-duplicado", new BigDecimal("1000"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ya tenés una caja abierta");
    }

    private Cuota guardarCuotaConSocio(String nombre, String monto) {
        Socio socio = new Socio();
        socio.setNombre(nombre);
        socio.setApellido("Prueba");
        socio.setDni(Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits()).substring(0, 8));
        socio.setEstado(EstadoSocio.ACTIVO);
        socio = socioRepository.save(socio);

        Cuota cuota = new Cuota();
        cuota.setSocio(socio);
        cuota.setFechaVencimiento(LocalDate.now());
        cuota.setMonto(new BigDecimal(monto));
        cuota.setEstado(EstadoCuota.PENDIENTE);
        return cuotaRepository.save(cuota);
    }
}
