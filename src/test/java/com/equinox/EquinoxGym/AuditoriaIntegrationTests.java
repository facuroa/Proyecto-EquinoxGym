package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class AuditoriaIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private EventoAuditoriaRepository eventoRepository;

    @Test
    @Transactional
    void registraLaOperacionConSuResponsable() {
        auditoriaService.registrar("recepcion-prueba", "Pago registrado", "Cuota de Socio Prueba");

        EventoAuditoria evento = eventoRepository.findTop100ByOrderByFechaRegistroDescIdDesc().get(0);
        assertThat(evento.getUsuario()).isEqualTo("recepcion-prueba");
        assertThat(evento.getCategoria()).isEqualTo("Pago registrado");
        assertThat(evento.getDetalle()).isEqualTo("Cuota de Socio Prueba");
    }

    @Test
    @WithMockUser(roles = "RECEPCIONISTA")
    void recepcionNoPuedeAccederALaAuditoria() throws Exception {
        mockMvc.perform(get("/auditoria"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administracionPuedeConsultarLaAuditoria() throws Exception {
        mockMvc.perform(get("/auditoria"))
                .andExpect(status().isOk())
                .andExpect(view().name("auditoria"));
    }
}
