package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ComprobantePdfDescargaTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CobroService cobroService;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private CajaService cajaService;

    @Test
    @WithMockUser(username = "recepcion", roles = "RECEPCIONISTA")
    void elMostradorDescargaElComprobanteEnPdf() throws Exception {
        cajaService.abrirCaja("recepcion", new BigDecimal("0"), "caja de prueba");
        Long pagoId = registrarUnPago();

        MvcResult resultado = mockMvc.perform(get("/pagos/{id}/descargar-pdf", pagoId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andReturn();

        byte[] pdf = resultado.getResponse().getContentAsByteArray();
        // Un PDF valido siempre arranca con la firma "%PDF-".
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);

        assertThat(resultado.getResponse().getHeader("Content-Disposition"))
                .contains("Comprobante-EQX-");
    }

    @Test
    @WithMockUser(username = "recepcion", roles = "RECEPCIONISTA")
    void unPagoInexistenteNoRompeLaPantalla() throws Exception {
        mockMvc.perform(get("/pagos/{id}/descargar-pdf", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinIniciarSesionNoSePuedeBajarElComprobante() throws Exception {
        Long pagoId = registrarUnPago();

        // El endpoint queda detras del login: el link no sirve para mandarselo
        // a un socio, solo para que lo use el mostrador ya autenticado.
        mockMvc.perform(get("/pagos/{id}/descargar-pdf", pagoId))
                .andExpect(status().is3xxRedirection());
    }

    private Long registrarUnPago() {
        Plan plan = new Plan();
        plan.setNombre("Mensual descarga " + System.nanoTime());
        plan.setDuracionMeses(1);
        plan.setPrecio(new BigDecimal("25000"));
        plan = planRepository.save(plan);

        LocalDate hoy = LocalDate.now();
        cobroService.altaRapidaConPlanYCobro(
                "Lucía", "Fernández", "33444555", "3624000111", "lucia@email.com",
                "Av. Siempreviva 742", hoy, false, null,
                plan, hoy, true, plan.getPrecio(), "Efectivo");

        return pagoRepository.findAll().stream()
                .map(Pago::getId)
                .max(Long::compareTo)
                .orElseThrow();
    }
}
