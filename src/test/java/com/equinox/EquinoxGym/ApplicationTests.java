package com.equinox.EquinoxGym;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void eliminarPorGetNoEstaPermitido() throws Exception {
        mockMvc.perform(get("/socios/eliminar/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
	void eliminarPorPostSinCsrfEstaBloqueado() throws Exception {
		mockMvc.perform(post("/socios/eliminar/1"))
				.andExpect(status().isForbidden());
	}

    @Test
    @WithMockUser(roles = "ADMIN")
    void historialDePagosConAuditoriaRenderizaCorrectamente() throws Exception {
        mockMvc.perform(get("/pagos"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboardOperativoRenderizaCorrectamente() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk());
    }
}
