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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private CobroService cobroService;

    @Autowired
    private CuotaRepository cuotaRepository;

    @Autowired
    private SeguimientoMorosidadRepository seguimientoMorosidadRepository;

    @Autowired
    private MorosidadService morosidadService;

    @Autowired
    private CajaService cajaService;

    @Test
    void contextLoads() {
    }

    @Test
    void morosidadRequiereInicioDeSesion() throws Exception {
        mockMvc.perform(get("/morosidad"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
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
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("payment-toolbar-header")))
                .andExpect(content().string(containsString("for=\"pagoDesde\"")))
                .andExpect(content().string(containsString("for=\"pagoHasta\"")));
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void cuotasMantienenTresEspaciosDeAccionAlineados() throws Exception {
        String sufijo = String.valueOf(System.nanoTime());
        Socio socio = new Socio();
        socio.setNombre("Acciones");
        socio.setApellido("Alineadas");
        socio.setDni(sufijo.substring(sufijo.length() - 8));
        socio.setEstado(EstadoSocio.ACTIVO);
        socio = socioRepository.save(socio);

        Cuota pendiente = new Cuota();
        pendiente.setSocio(socio);
        pendiente.setMonto(new BigDecimal("10000"));
        pendiente.setFechaVencimiento(LocalDate.now().plusDays(5));
        pendiente.setEstado(EstadoCuota.PENDIENTE);

        Cuota pagada = new Cuota();
        pagada.setSocio(socio);
        pagada.setMonto(new BigDecimal("10000"));
        pagada.setFechaVencimiento(LocalDate.now().minusMonths(1));
        pagada.setFechaPago(LocalDate.now().minusMonths(1));
        pagada.setEstado(EstadoCuota.PAGADA);
        cuotaRepository.saveAll(List.of(pendiente, pagada));

        mockMvc.perform(get("/cuotas").param("buscar", socio.getDni()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cuota-action-buttons")))
                .andExpect(content().string(containsString("cuota-action-placeholder")))
                .andExpect(content().string(containsString("cuota-action-button")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboardOperativoRenderizaCorrectamente() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void dashboardAgrupaLaDeudaPorSocioYConservaLosAtrasosGraves() throws Exception {
        String sufijo = String.valueOf(System.nanoTime());

        Socio socioAtrasado = new Socio();
        socioAtrasado.setNombre("Socio");
        socioAtrasado.setApellido("Atraso grave");
        socioAtrasado.setDni(sufijo.substring(sufijo.length() - 8));
        socioAtrasado.setEstado(EstadoSocio.ACTIVO);
        socioAtrasado = socioRepository.save(socioAtrasado);

        Cuota cuotaAntigua = new Cuota();
        cuotaAntigua.setSocio(socioAtrasado);
        cuotaAntigua.setMonto(new BigDecimal("23000"));
        cuotaAntigua.setFechaVencimiento(LocalDate.now().minusDays(35));
        cuotaAntigua.setEstado(EstadoCuota.PENDIENTE);
        socioAtrasado.getCuotas().add(cuotaAntigua);

        Cuota cuotaReciente = new Cuota();
        cuotaReciente.setSocio(socioAtrasado);
        cuotaReciente.setMonto(new BigDecimal("12000"));
        cuotaReciente.setFechaVencimiento(LocalDate.now().minusDays(5));
        cuotaReciente.setEstado(EstadoCuota.PENDIENTE);
        socioAtrasado.getCuotas().add(cuotaReciente);
        cuotaRepository.saveAll(List.of(cuotaAntigua, cuotaReciente));

        Socio socioProximo = new Socio();
        socioProximo.setNombre("Socio");
        socioProximo.setApellido("Renovación cercana");
        socioProximo.setDni(String.valueOf(Long.parseLong(socioAtrasado.getDni()) + 1));
        socioProximo.setEstado(EstadoSocio.ACTIVO);
        socioProximo = socioRepository.save(socioProximo);

        Cuota cuotaProxima = new Cuota();
        cuotaProxima.setSocio(socioProximo);
        cuotaProxima.setMonto(new BigDecimal("18000"));
        cuotaProxima.setFechaVencimiento(LocalDate.now().plusDays(3));
        cuotaProxima.setEstado(EstadoCuota.PENDIENTE);
        socioProximo.getCuotas().add(cuotaProxima);
        cuotaRepository.save(cuotaProxima);

        var resultado = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Socios con cuotas vencidas")))
                .andReturn();

        DashboardDTO resumen = (DashboardDTO) resultado.getModelAndView().getModel().get("resumen");
        org.junit.jupiter.api.Assertions.assertEquals(1, resumen.getMorosidadPrioritaria().size());
        org.junit.jupiter.api.Assertions.assertEquals(2,
                resumen.getMorosidadPrioritaria().get(0).getCantidadCuotas());
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("35000").compareTo(
                resumen.getMorosidadPrioritaria().get(0).getSaldoPendiente()));
        org.junit.jupiter.api.Assertions.assertEquals(1, resumen.getSociosMorosos());
        org.junit.jupiter.api.Assertions.assertEquals(1, resumen.getRenovacionesProximas().size());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void altaSocioRechazaDatosPersonalesInvalidos() throws Exception {
        mockMvc.perform(post("/socios/guardar")
                        .with(csrf())
                        .param("nombre", " ")
                        .param("apellido", "")
                        .param("dni", "ABC")
                        .param("fechaNacimiento", "2999-01-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("nuevo-socio"))
                .andExpect(model().attributeHasFieldErrors(
                        "socio", "nombre", "apellido", "dni", "fechaNacimiento"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void altaCuotaRechazaMontoCero() throws Exception {
        mockMvc.perform(post("/cuotas/guardar")
                        .with(csrf())
                        .param("monto", "0")
                        .param("fechaVencimiento", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("nueva-cuota"))
                .andExpect(model().attributeHasFieldErrors("cuota", "socioId", "monto"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void altaUsuarioRechazaContrasenaDebil() throws Exception {
        mockMvc.perform(post("/usuarios/guardar")
                        .with(csrf())
                        .param("username", "recepcion_prueba")
                        .param("passwordNuevo", "123")
                        .param("rol", "RECEPCIONISTA"))
                .andExpect(status().isOk())
                .andExpect(view().name("nuevo-usuario"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin-prueba", roles = "ADMIN")
    void comprobanteYFichaFinancieraRenderizanCorrectamente() throws Exception {
        Plan plan = new Plan();
        plan.setNombre("Plan comprobante " + UUID.randomUUID());
        plan.setPrecio(new BigDecimal("15000"));
        plan.setDuracionMeses(1);
        plan.setActivo(true);
        plan = planRepository.save(plan);

        Socio socio = new Socio();
        socio.setNombre("Socio");
        socio.setApellido("Comprobante");
        socio.setDni("99887766");
        socio.setEstado(EstadoSocio.ACTIVO);
        socio = socioRepository.save(socio);

        Cuota cuota = cobroService.asignarPlanAExistente(socio, plan, LocalDate.now());
        cajaService.abrirCaja("admin-prueba", BigDecimal.ZERO, null);
        Pago pago = cobroService.registrarPago(cuota, plan.getPrecio(), "Efectivo");

        mockMvc.perform(get("/pagos/{id}/comprobante", pago.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("comprobante-pago"))
                .andExpect(model().attributeExists("pago", "socio"))
                .andExpect(content().string(containsString("Cobrar próxima cuota")));

        mockMvc.perform(get("/cobrar").param("socioId", socio.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("cobrar"))
                .andExpect(content().string(containsString("Confirmar y cobrar")))
                .andExpect(content().string(containsString("data-confirm-payment")));

        mockMvc.perform(get("/socios/ver/{id}", socio.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("detalle-socio"))
                .andExpect(model().attributeExists(
                        "pagosRecientes", "totalPagado", "deudaPendiente", "cuotasPendientes"));
    }

    @Test
    @Transactional
    @WithMockUser(username = "recepcion-prueba", roles = "RECEPCIONISTA")
    void morosidadPermitePriorizarYRegistrarSeguimiento() throws Exception {
        Socio socio = new Socio();
        socio.setNombre("Andrea");
        socio.setApellido("Morosa " + UUID.randomUUID());
        socio.setDni(String.valueOf(System.nanoTime()).substring(0, 8));
        socio.setTelefono("5493815551234");
        socio.setEstado(EstadoSocio.MOROSO);
        socio = socioRepository.save(socio);

        Cuota cuota = new Cuota();
        cuota.setSocio(socio);
        cuota.setMonto(new BigDecimal("23000"));
        cuota.setFechaVencimiento(LocalDate.now().minusDays(12));
        cuota.setEstado(EstadoCuota.VENCIDA);
        cuota = cuotaRepository.save(cuota);
        String notaSeguimiento = "Se comprometió a pagar el viernes por transferencia y pidió que lo contacten por la tarde.";

        mockMvc.perform(get("/morosidad").param("filtro", "8_30"))
                .andExpect(status().isOk())
                .andExpect(view().name("morosidad"))
                .andExpect(model().attributeExists("gestiones", "resumen", "canales"))
                .andExpect(content().string(containsString("Andrea")))
                .andExpect(content().string(containsString("12 días de atraso")))
                .andExpect(content().string(containsString("data-label=\"Último contacto\"")))
                .andExpect(content().string(containsString("Registrar contacto con Andrea")));

        mockMvc.perform(post("/morosidad/{socioId}/seguimientos", socio.getId())
                        .with(csrf())
                        .param("canal", "WHATSAPP")
                        .param("nota", notaSeguimiento)
                        .param("filtro", "8_30")
                        .param("page", "0"))
                .andExpect(status().is3xxRedirection());

        List<SeguimientoMorosidad> seguimientos = seguimientoMorosidadRepository
                .findBySocio_IdOrderByFechaRegistroDescIdDesc(socio.getId());
        org.junit.jupiter.api.Assertions.assertEquals(1, seguimientos.size());
        org.junit.jupiter.api.Assertions.assertEquals("recepcion-prueba", seguimientos.get(0).getRegistradoPor());
        org.junit.jupiter.api.Assertions.assertEquals(CanalSeguimiento.WHATSAPP, seguimientos.get(0).getCanal());

        mockMvc.perform(get("/morosidad")
                        .param("filtro", "8_30")
                        .param("buscar", socio.getDni()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WhatsApp · recepcion-prueba")))
                .andExpect(content().string(containsString(notaSeguimiento)))
                .andExpect(content().string(containsString("<details")));
    }

    @Test
    @Transactional
    @WithMockUser(roles = "RECEPCIONISTA")
    void morosidadAgrupaCuotasPermiteBuscarYRetiraLasPagadas() throws Exception {
        String sufijo = String.valueOf(System.nanoTime());
        String telefono = "381" + sufijo.substring(sufijo.length() - 7);
        String telefonoFormateado = telefono.substring(0, 3) + " "
                + telefono.substring(3, 6) + "-" + telefono.substring(6);

        Socio socio = new Socio();
        socio.setNombre("Socio");
        socio.setApellido("Agrupado " + UUID.randomUUID());
        socio.setDni(sufijo.substring(sufijo.length() - 8));
        socio.setTelefono(telefonoFormateado);
        socio.setEstado(EstadoSocio.MOROSO);
        socio = socioRepository.save(socio);

        Cuota cuotaAntigua = new Cuota();
        cuotaAntigua.setSocio(socio);
        cuotaAntigua.setMonto(new BigDecimal("23000"));
        cuotaAntigua.setFechaVencimiento(LocalDate.now().minusDays(18));
        cuotaAntigua.setEstado(EstadoCuota.VENCIDA);

        Cuota cuotaReciente = new Cuota();
        cuotaReciente.setSocio(socio);
        cuotaReciente.setMonto(new BigDecimal("12000"));
        cuotaReciente.setFechaVencimiento(LocalDate.now().minusDays(4));
        cuotaReciente.setEstado(EstadoCuota.VENCIDA);

        List<Cuota> cuotas = cuotaRepository.saveAll(List.of(cuotaAntigua, cuotaReciente));

        var resultado = mockMvc.perform(get("/morosidad")
                        .param("filtro", "VENCIDAS")
                        .param("buscar", telefono))
                .andExpect(status().isOk())
                .andExpect(view().name("morosidad"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<GestionMorosidadDTO> gestiones = (List<GestionMorosidadDTO>) resultado
                .getModelAndView().getModel().get("gestiones");
        org.junit.jupiter.api.Assertions.assertEquals(1, gestiones.size());
        org.junit.jupiter.api.Assertions.assertEquals(2, gestiones.get(0).getCantidadCuotas());
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("35000")
                .compareTo(gestiones.get(0).getSaldoPendiente()));

        cuotas.forEach(cuota -> {
            cuota.setFechaPago(LocalDate.now());
            cuota.setEstado(EstadoCuota.PAGADA);
        });
        cuotaRepository.saveAll(cuotas);

        var resultadoPagado = mockMvc.perform(get("/morosidad")
                        .param("filtro", "VENCIDAS")
                        .param("buscar", telefono))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<GestionMorosidadDTO> gestionesPagadas = (List<GestionMorosidadDTO>) resultadoPagado
                .getModelAndView().getModel().get("gestiones");
        org.junit.jupiter.api.Assertions.assertTrue(gestionesPagadas.isEmpty());
    }

    @Test
    void whatsappNormalizaNumerosArgentinosSinAdivinarFormatosInvalidos() {
        GestionMorosidadDTO gestion = new GestionMorosidadDTO();
        Socio socio = new Socio();
        gestion.setSocio(socio);

        socio.setTelefono("381 555-1234");
        org.junit.jupiter.api.Assertions.assertEquals("https://wa.me/5493815551234", gestion.getWhatsappUrl());

        socio.setTelefono("+54 9 381 555-1234");
        org.junit.jupiter.api.Assertions.assertEquals("https://wa.me/5493815551234", gestion.getWhatsappUrl());

        socio.setTelefono("+54 381 555-1234");
        org.junit.jupiter.api.Assertions.assertEquals("https://wa.me/5493815551234", gestion.getWhatsappUrl());

        socio.setTelefono("0381 555-1234");
        org.junit.jupiter.api.Assertions.assertEquals("https://wa.me/5493815551234", gestion.getWhatsappUrl());

        socio.setTelefono("12345");
        org.junit.jupiter.api.Assertions.assertNull(gestion.getWhatsappUrl());
    }

    @Test
    @Transactional
    @WithMockUser(roles = "ADMIN")
    void eliminarSocioSinPagosEliminaTambienSuSeguimiento() throws Exception {
        String sufijo = String.valueOf(System.nanoTime());

        Socio socio = new Socio();
        socio.setNombre("Socio");
        socio.setApellido("Sin pagos");
        socio.setDni(sufijo.substring(sufijo.length() - 8));
        socio.setEstado(EstadoSocio.MOROSO);
        socio = socioRepository.save(socio);

        SeguimientoMorosidad seguimiento = new SeguimientoMorosidad();
        seguimiento.setSocio(socio);
        seguimiento.setCanal(CanalSeguimiento.LLAMADA);
        seguimiento.setNota("Registro previo a la eliminación");
        seguimiento.setFechaRegistro(LocalDateTime.now());
        seguimiento.setRegistradoPor("admin-prueba");
        seguimientoMorosidadRepository.save(seguimiento);

        mockMvc.perform(post("/socios/eliminar/{id}", socio.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        org.junit.jupiter.api.Assertions.assertTrue(socioRepository.findById(socio.getId()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(seguimientoMorosidadRepository
                .findBySocio_IdOrderByFechaRegistroDescIdDesc(socio.getId()).isEmpty());
    }

    @Test
    @Transactional
    void morosidadClasificaTodosLosRangosYCalculaMetricas() {
        record CasoPrioridad(String filtro, int desplazamientoDias) {}

        List<CasoPrioridad> casos = List.of(
                new CasoPrioridad("MAS_30", -40),
                new CasoPrioridad("8_30", -12),
                new CasoPrioridad("1_7", -3),
                new CasoPrioridad("HOY", 0),
                new CasoPrioridad("PROXIMAS", 5));

        for (int i = 0; i < casos.size(); i++) {
            CasoPrioridad caso = casos.get(i);
            Socio socio = new Socio();
            socio.setNombre("Prioridad");
            socio.setApellido(caso.filtro());
            socio.setDni(String.valueOf(10000000L + Math.floorMod(System.nanoTime() + i, 89999999L)));
            socio.setEstado(EstadoSocio.ACTIVO);
            socio = socioRepository.save(socio);

            Cuota cuota = new Cuota();
            cuota.setSocio(socio);
            cuota.setMonto(new BigDecimal("1000"));
            cuota.setFechaVencimiento(LocalDate.now().plusDays(caso.desplazamientoDias()));
            cuota.setEstado(EstadoCuota.PENDIENTE);
            cuotaRepository.save(cuota);
        }

        for (CasoPrioridad caso : casos) {
            MorosidadResumenDTO filtrado = morosidadService.obtenerResumen(caso.filtro(), "");
            org.junit.jupiter.api.Assertions.assertEquals(1, filtrado.getGestiones().size());
            org.junit.jupiter.api.Assertions.assertEquals(
                    caso.filtro(), filtrado.getGestiones().get(0).getCategoria());
        }

        MorosidadResumenDTO vencidas = morosidadService.obtenerResumen("VENCIDAS", "");
        org.junit.jupiter.api.Assertions.assertEquals(3, vencidas.getGestiones().size());

        MorosidadResumenDTO todaLaMesa = morosidadService.obtenerResumen("TODOS", "");
        org.junit.jupiter.api.Assertions.assertEquals(5, todaLaMesa.getGestiones().size());
        org.junit.jupiter.api.Assertions.assertEquals(3, todaLaMesa.getSociosMorosos());
        org.junit.jupiter.api.Assertions.assertEquals(1, todaLaMesa.getVencenHoy());
        org.junit.jupiter.api.Assertions.assertEquals(1, todaLaMesa.getVencenProximos());
        org.junit.jupiter.api.Assertions.assertEquals(
                0, new BigDecimal("3000").compareTo(todaLaMesa.getDeudaVencida()));
    }
}
