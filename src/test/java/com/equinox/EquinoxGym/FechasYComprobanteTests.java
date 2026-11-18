package com.equinox.EquinoxGym;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Errores reportados en Tropa Gym: la fecha de nacimiento se borraba al
 * editar un socio, y el comprobante mostraba como "vencimiento" la fecha de
 * la cuota pagada en lugar de hasta cuando queda al dia el socio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "recepcion-fechas", roles = "RECEPCIONISTA")
class FechasYComprobanteTests {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private MockMvc mockMvc;
    @Autowired private SocioRepository socioRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private CuotaRepository cuotaRepository;
    @Autowired private CobroService cobroService;
    @Autowired private CajaService cajaService;

    // ------------------------------------------------------------------
    // Fecha de nacimiento
    // ------------------------------------------------------------------

    @Test
    void laFechaDeNacimientoSeGuardaYSigueAhiAlEditar() throws Exception {
        String dni = dniNuevo();
        mockMvc.perform(post("/socios/guardar").with(csrf())
                        .param("nombre", "Cumple").param("apellido", "Prueba").param("dni", dni)
                        .param("fechaNacimiento", "1995-05-10"))
                .andExpect(redirectedUrl("/socios"));
        Socio socio = socioRepository.findAll().stream()
                .filter(s -> dni.equals(s.getDni())).findFirst().orElseThrow();
        assertThat(socio.getFechaNacimiento()).isEqualTo(LocalDate.of(1995, 5, 10));

        // El formulario de edicion trae la fecha en el formato del calendario.
        mockMvc.perform(get("/socios/editar/{id}", socio.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"1995-05-10\"")));

        // Guardar la edicion tal como vino el formulario conserva la fecha.
        mockMvc.perform(post("/socios/guardar").with(csrf())
                        .param("id", socio.getId().toString())
                        .param("nombre", "Cumple").param("apellido", "Editado").param("dni", dni)
                        .param("fechaNacimiento", "1995-05-10"))
                .andExpect(redirectedUrl("/socios"));
        assertThat(socioRepository.findById(socio.getId()).orElseThrow().getFechaNacimiento())
                .isEqualTo(LocalDate.of(1995, 5, 10));
    }

    @Test
    void editarUnaCuotaMuestraSusFechasEnElCalendario() throws Exception {
        Socio socio = socioConCuota(plan(), LocalDate.of(2026, 12, 3));
        Cuota cuota = socio.getCuotas().get(0);

        mockMvc.perform(get("/cuotas/editar/{id}", cuota.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2026-12-03\"")));
    }

    // ------------------------------------------------------------------
    // Comprobante: proximo vencimiento
    // ------------------------------------------------------------------

    @Test
    void elComprobanteDeUnAltaMuestraHastaCuandoQuedaAlDia() throws Exception {
        LocalDate hoy = LocalDate.now();
        Pago pago = pagar(socioConCuota(plan(), hoy));
        LocalDate hasta = hoy.plusMonths(1);

        assertThat(pago.getVigenteDesde()).isEqualTo(hoy);
        assertThat(pago.getVigenteHasta()).isEqualTo(hasta);

        MvcResult resultado = mockMvc.perform(get("/pagos/{id}/comprobante", pago.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Próximo vencimiento")))
                .andExpect(content().string(containsString(DMY.format(hasta))))
                .andExpect(content().string(containsString(
                        "Cuota Crossfit Fechas · del " + DMY.format(hoy) + " al " + DMY.format(hasta))))
                // Ya no dice que vence el mismo dia en que pago.
                .andExpect(content().string(not(containsString("vencimiento " + DMY.format(hoy)))))
                .andReturn();

        String whatsapp = URLDecoder.decode(
                (String) resultado.getModelAndView().getModel().get("whatsappUrl"), StandardCharsets.UTF_8);
        assertThat(whatsapp).contains("Recibimos tu pago", "Próximo vencimiento: *" + DMY.format(hasta) + "*");
    }

    @Test
    void pagandoAntesDelVencimientoConservaSuFecha() {
        LocalDate vence = LocalDate.now().plusDays(7);
        Pago pago = pagar(socioConCuota(plan(), vence));

        assertThat(pago.getVigenteDesde()).isEqualTo(vence);
        assertThat(pago.getVigenteHasta()).isEqualTo(vence.plusMonths(1));
    }

    @Test
    void pagandoAtrasadoElPeriodoArrancaElDiaDelPago() {
        LocalDate hoy = LocalDate.now();
        Pago pago = pagar(socioConCuota(plan(), hoy.minusDays(10)));

        assertThat(pago.getVigenteDesde()).isEqualTo(hoy);
        assertThat(pago.getVigenteHasta()).isEqualTo(hoy.plusMonths(1));
    }

    @Test
    void elPdfDelComprobanteTraeElProximoVencimiento() throws Exception {
        LocalDate hoy = LocalDate.now();
        Pago pago = pagar(socioConCuota(plan(), hoy));

        byte[] pdf = mockMvc.perform(get("/pagos/{id}/descargar-pdf", pago.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Próximo vencimiento", DMY.format(hoy.plusMonths(1)),
                    "del " + DMY.format(hoy) + " al " + DMY.format(hoy.plusMonths(1)));
        }
    }

    // ------------------------------------------------------------------
    // Datos de prueba
    // ------------------------------------------------------------------

    private Pago pagar(Socio socio) {
        cajaService.abrirCaja("recepcion-fechas", BigDecimal.ZERO, null);
        return cobroService.registrarPago(socio.getCuotas().get(0), new BigDecimal("50000"), "Efectivo");
    }

    private Plan plan() {
        Plan plan = new Plan();
        plan.setNombre("Crossfit Fechas");
        plan.setPrecio(new BigDecimal("50000"));
        plan.setDuracionMeses(1);
        return planRepository.save(plan);
    }

    private Socio socioConCuota(Plan plan, LocalDate vencimiento) {
        Socio socio = new Socio();
        socio.setNombre("Fecha");
        socio.setApellido("Prueba");
        socio.setDni(dniNuevo());
        socio.setTelefono("3624936988");
        socio.setEstado(EstadoSocio.ACTIVO);
        socio.setPlan(plan);
        socio.setFechaInicioPlan(vencimiento);
        socio.setFechaVencimientoPlan(vencimiento.plusMonths(1));
        Cuota cuota = new Cuota();
        cuota.setSocio(socio);
        cuota.setFechaVencimiento(vencimiento);
        cuota.setMonto(plan.getPrecio());
        cuota.setEstado(EstadoCuota.PENDIENTE);
        socio.getCuotas().add(cuota);
        return socioRepository.saveAndFlush(socio);
    }

    private static String dniNuevo() {
        return Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits()).substring(0, 9);
    }
}
