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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ReporteIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReporteService reporteService;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Test
    @WithMockUser(roles = "RECEPCIONISTA")
    void reportesSonExclusivosDeAdministracion() throws Exception {
        mockMvc.perform(get("/reportes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void reportesCalculanIngresosAltasRenovacionesYMediosSinAnulados() throws Exception {
        LocalDate hoy = LocalDate.now();
        LocalDate mesAnterior = hoy.minusMonths(1);

        Socio socioAntiguo = guardarSocio("Socio", "Renovación", hoy.minusMonths(2));
        Cuota cuotaInicial = guardarCuota(socioAntiguo, mesAnterior, new BigDecimal("18000"));
        guardarPago(cuotaInicial, mesAnterior, new BigDecimal("18000"), "Efectivo", false);

        Cuota cuotaRenovada = guardarCuota(socioAntiguo, hoy, new BigDecimal("30000"));
        guardarPago(cuotaRenovada, hoy, new BigDecimal("30000"), "Transferencia", false);

        Socio socioNuevo = guardarSocio("Socio", "Nuevo", hoy);
        Cuota cuotaAlta = guardarCuota(socioNuevo, hoy, new BigDecimal("20000"));
        guardarPago(cuotaAlta, hoy, new BigDecimal("20000"), "Efectivo", false);

        Socio socioConPagoAnulado = guardarSocio("Socio", "Anulado", hoy);
        Cuota cuotaAnulada = guardarCuota(socioConPagoAnulado, hoy, new BigDecimal("9000"));
        guardarPago(cuotaAnulada, hoy, new BigDecimal("9000"), "Tarjeta", true);

        ReporteResumenDTO reporte = reporteService.obtenerResumen(hoy, hoy);

        assertEquals(0, new BigDecimal("50000").compareTo(reporte.getIngresos()));
        assertEquals(2, reporte.getPagosConfirmados());
        assertEquals(2, reporte.getAltas());
        assertEquals(1, reporte.getRenovaciones());
        assertEquals(2, reporte.getMediosPago().size());
        assertEquals(List.of("Transferencia", "Efectivo"),
                reporte.getMediosPago().stream().map(ReporteMedioPagoDTO::getMedioPago).toList());
        assertEquals(0, new BigDecimal("50000")
                .compareTo(reporte.getEvolucionMensual().get(5).getIngresos()));

        mockMvc.perform(get("/reportes")
                        .param("desde", hoy.toString())
                        .param("hasta", hoy.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("reportes"))
                .andExpect(model().attributeExists("reporte", "desde", "hasta"))
                .andExpect(content().string(containsString("Ingresos confirmados")))
                .andExpect(content().string(containsString("Medios de pago")))
                .andExpect(content().string(containsString("Últimos 6 meses")));
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void reportesOrdenanUnPeriodoInvertidoSinRomperLaPantalla() throws Exception {
        LocalDate hoy = LocalDate.now();

        mockMvc.perform(get("/reportes")
                        .param("desde", hoy.toString())
                        .param("hasta", hoy.minusDays(4).toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("reportes"))
                .andExpect(model().attributeExists("avisoPeriodo"));
    }

    private Socio guardarSocio(String nombre, String apellido, LocalDate fechaAlta) {
        Socio socio = new Socio();
        socio.setNombre(nombre);
        socio.setApellido(apellido);
        socio.setDni(Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits()).substring(0, 8));
        socio.setEstado(EstadoSocio.ACTIVO);
        socio.setFechaAlta(fechaAlta);
        return socioRepository.save(socio);
    }

    private Cuota guardarCuota(Socio socio, LocalDate fecha, BigDecimal monto) {
        Cuota cuota = new Cuota();
        cuota.setSocio(socio);
        cuota.setFechaVencimiento(fecha);
        cuota.setFechaPago(fecha);
        cuota.setMonto(monto);
        cuota.setEstado(EstadoCuota.PAGADA);
        return cuotaRepository.save(cuota);
    }

    private Pago guardarPago(Cuota cuota,
                             LocalDate fecha,
                             BigDecimal monto,
                             String medioPago,
                             boolean anulado) {
        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setFechaPago(fecha);
        pago.setMonto(monto);
        pago.setMedioPago(medioPago);
        pago.setFechaRegistro(LocalDateTime.now());
        pago.setRegistradoPor("admin-prueba");
        pago.setAnulado(anulado);
        return pagoRepository.save(pago);
    }
}
