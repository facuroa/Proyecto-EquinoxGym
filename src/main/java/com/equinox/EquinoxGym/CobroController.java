package com.equinox.EquinoxGym;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/cobrar")
public class CobroController {

    private static final Pattern DNI_VALIDO = Pattern.compile("^[0-9]{6,12}$");
    private static final Pattern TELEFONO_VALIDO = Pattern.compile("^[+0-9()\\s-]{6,25}$");
    private static final Pattern EMAIL_VALIDO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final SocioRepository socioRepository;
    private final PlanRepository planRepository;
    private final CobroService cobroService;
    private final SocioService socioService;
    private final PagoRepository pagoRepository;

    public CobroController(SocioRepository socioRepository,
                           PlanRepository planRepository,
                           CobroService cobroService,
                           SocioService socioService,
                           PagoRepository pagoRepository) {
        this.socioRepository = socioRepository;
        this.planRepository = planRepository;
        this.cobroService = cobroService;
        this.socioService = socioService;
        this.pagoRepository = pagoRepository;
    }

    /**
     * Pantalla principal de Cobro. Según los parámetros que llegan por la URL
     * (después de un redirect) decide qué mostrar: el buscador solo, el panel
     * de un socio ya seleccionado, o el formulario de alta rápida.
     */
    @GetMapping
    public String pantallaCobro(@RequestParam(required = false) Long socioId,
                                @RequestParam(required = false) String altaRapida,
                                @RequestParam(required = false) String q,
                                @RequestParam(required = false) String mensaje,
                                @RequestParam(required = false) String error,
                                Model model) {

        model.addAttribute("planes", planRepository.findByActivoTrueOrderByDuracionMesesAsc());
        model.addAttribute("mensaje", mensaje);
        model.addAttribute("error", error);

        if (socioId != null) {
            Socio socio = socioRepository.findById(socioId).orElse(null);
            if (socio != null) {
                if (socioService.actualizarEstadoSocio(socio)) {
                    socioRepository.save(socio);
                }

                model.addAttribute("socio", socio);
                Optional<Cuota> cuotaPendiente = cobroService.obtenerCuotaPendienteMasAntigua(socio);
                model.addAttribute("cuotaPendiente", cuotaPendiente.orElse(null));
            }
        }

        if ("true".equals(altaRapida)) {
            model.addAttribute("mostrarAltaRapida", true);
            model.addAttribute("dniSugerido", (q != null && q.matches("\\d+")) ? q : "");
        }

        return "cobrar";
    }

    /**
     * Endpoint del autocompletado. Devuelve JSON (no una vista HTML) porque
     * el JavaScript de cobrar.html lo consume con fetch() para armar el
     * dropdown de sugerencias mientras el usuario tipea.
     */
    @GetMapping("/buscar")
    @ResponseBody
    public List<SocioBusquedaDTO> buscar(@RequestParam String q) {
        return cobroService.buscarSocios(q);
    }

    /** Alta rápida de un socio nuevo + su plan + su primera cuota, en un solo paso. */
    @PostMapping("/nuevo-socio")
    public String altaRapida(@RequestParam String nombre,
                             @RequestParam String apellido,
                             @RequestParam String dni,
                             @RequestParam(required = false) String telefono,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String domicilioActual,
                             @RequestParam(required = false) String fechaNacimiento,
                             @RequestParam(value = "tieneLesiones", required = false) Boolean tieneLesiones,
                             @RequestParam(required = false) String detalleLesiones,
                             @RequestParam(required = false) Long planId,
                             @RequestParam(required = false) String fechaInicioPlan,
                             @RequestParam(value = "cobrarAlta", required = false) Boolean cobrarAlta,
                             @RequestParam(value = "montoInicial", required = false) BigDecimal montoInicial,
                             @RequestParam(value = "medioPagoInicial", required = false) String medioPagoInicial) {

        nombre = limpiar(nombre);
        apellido = limpiar(apellido);
        dni = normalizarDni(dni);
        telefono = limpiar(telefono);
        email = limpiar(email);
        domicilioActual = limpiar(domicilioActual);
        detalleLesiones = limpiar(detalleLesiones);

        LocalDate nacimiento;
        LocalDate inicio;
        try {
            nacimiento = parseFechaONull(fechaNacimiento);
            inicio = parseFechaONull(fechaInicioPlan);
        } catch (RuntimeException e) {
            return "redirect:/cobrar?altaRapida=true&error=fechaInvalida";
        }

        if (nombre == null || apellido == null || dni == null) {
            return "redirect:/cobrar?altaRapida=true&error=datosInvalidos";
        }
        if (nombre.length() > 80 || apellido.length() > 80
                || !DNI_VALIDO.matcher(dni).matches()
                || (telefono != null && !TELEFONO_VALIDO.matcher(telefono).matches())
                || (email != null && !EMAIL_VALIDO.matcher(email).matches())
                || (domicilioActual != null && domicilioActual.length() > 255)
                || (nacimiento != null && nacimiento.isAfter(LocalDate.now()))
                || (Boolean.TRUE.equals(tieneLesiones) && detalleLesiones == null)
                || (detalleLesiones != null && detalleLesiones.length() > 1000)) {
            return "redirect:/cobrar?altaRapida=true&error=datosInvalidos";
        }

        if (socioRepository.findByDni(dni).isPresent()) {
            return "redirect:/cobrar?altaRapida=true&error=dniDuplicado";
        }

        if (planId == null) {
            return "redirect:/cobrar?altaRapida=true&error=datosInvalidos";
        }
        Plan plan = planRepository.findById(planId).orElse(null);
        if (plan == null || !plan.isActivo()) {
            return "redirect:/cobrar?altaRapida=true&error=planInvalido";
        }

        if (inicio == null) {
            inicio = LocalDate.now();
        }

        boolean cobrarAltaMarcado = Boolean.TRUE.equals(cobrarAlta);

        try {
            Socio socio = cobroService.altaRapidaConPlanYCobro(
                    nombre, apellido, dni, telefono, email, domicilioActual, nacimiento,
                    Boolean.TRUE.equals(tieneLesiones), detalleLesiones, plan, inicio,
                    cobrarAltaMarcado, montoInicial, medioPagoInicial);

            if (cobrarAltaMarcado) {
                Optional<Pago> pagoInicial = pagoRepository
                        .findFirstByCuota_Socio_IdAndAnuladoFalseOrderByFechaRegistroDescIdDesc(socio.getId());
                if (pagoInicial.isPresent()) {
                    return "redirect:/pagos/" + pagoInicial.get().getId()
                            + "/comprobante?origenSocio=" + socio.getId();
                }
            }
            String mensaje = cobrarAltaMarcado ? "pagado" : "cuotaGenerada";
            return "redirect:/cobrar?socioId=" + socio.getId() + "&mensaje=" + mensaje;
        } catch (CajaCerradaException e) {
            return "redirect:/cobrar?altaRapida=true&error=cajaCerrada";
        } catch (IllegalArgumentException e) {
            return "redirect:/cobrar?altaRapida=true&error=pagoInvalido";
        }
    }

    /** Asigna un plan a un socio existente que no tenía uno activo. */
    @PostMapping("/asignar-plan")
    public String asignarPlan(@RequestParam Long socioId,
                              @RequestParam Long planId,
                              @RequestParam(required = false) String fechaInicioPlan) {

        Socio socio = socioRepository.findById(socioId).orElse(null);
        Plan plan = planRepository.findById(planId).orElse(null);
        if (socio == null || plan == null || !plan.isActivo()) {
            return "redirect:/cobrar?error=planInvalido";
        }

        LocalDate inicio;
        try {
            inicio = parseFechaONull(fechaInicioPlan);
        } catch (RuntimeException e) {
            return "redirect:/cobrar?socioId=" + socioId + "&error=fechaInvalida";
        }
        if (inicio == null) {
            inicio = LocalDate.now();
        }

        cobroService.asignarPlanAExistente(socio, plan, inicio);

        return "redirect:/cobrar?socioId=" + socioId + "&mensaje=cuotaGenerada";
    }

    /** Genera la primera cuota para socios que ya tienen plan pero quedaron sin cuotas. */
    @PostMapping("/generar-cuota-inicial")
    public String generarCuotaInicial(@RequestParam Long socioId,
                                      @RequestParam(required = false) String fechaInicioPlan,
                                      @RequestParam(value = "cobrarAlta", required = false) Boolean cobrarAlta,
                                      @RequestParam(value = "montoInicial", required = false) BigDecimal montoInicial,
                                      @RequestParam(value = "medioPagoInicial", required = false) String medioPagoInicial) {

        Socio socio = socioRepository.findById(socioId)
                .orElseThrow(() -> new RuntimeException("Socio no encontrado"));

        if (socio.getPlan() == null) {
            return "redirect:/cobrar?socioId=" + socioId + "&error=socioSinPlan";
        }

        if (socio.getCuotas() != null && !socio.getCuotas().isEmpty()) {
            return "redirect:/cobrar?socioId=" + socioId + "&error=cuotaExistente";
        }

        LocalDate inicio;
        try {
            inicio = parseFechaONull(fechaInicioPlan);
        } catch (RuntimeException e) {
            return "redirect:/cobrar?socioId=" + socioId + "&error=fechaInvalida";
        }
        if (inicio == null) {
            inicio = LocalDate.now();
        }

        boolean cobrarAltaMarcado = Boolean.TRUE.equals(cobrarAlta);

        try {
            if (cobrarAltaMarcado) {
                cobroService.validarCajaParaMedioPago(medioPagoInicial);
            }
            Cuota cuotaInicial = cobroService.asignarPlanAExistente(socio, socio.getPlan(), inicio);
            if (cobrarAltaMarcado) {
                BigDecimal montoACobrar = montoInicial != null ? montoInicial : socio.getPlan().getPrecio();
                Pago pago = cobroService.registrarPago(cuotaInicial, montoACobrar, medioPagoInicial);
                return "redirect:/pagos/" + pago.getId() + "/comprobante?origenSocio=" + socioId;
            }
            return "redirect:/cobrar?socioId=" + socioId + "&mensaje=cuotaGenerada";
        } catch (CajaCerradaException e) {
            return "redirect:/cobrar?socioId=" + socioId + "&error=cajaCerrada";
        } catch (IllegalArgumentException e) {
            return "redirect:/cobrar?socioId=" + socioId + "&error=pagoInvalido";
        }
    }

    /** Cobra la cuota pendiente que se le mostró al usuario en pantalla. */
    @PostMapping("/pagar")
    public String pagar(@RequestParam Long cuotaId,
                        @RequestParam Long socioId,
                        @RequestParam BigDecimal monto,
                        @RequestParam String medioPago) {
        try {
            Pago pago = cobroService.registrarPagoPorId(cuotaId, monto, medioPago);
            return "redirect:/pagos/" + pago.getId() + "/comprobante?origenSocio=" + socioId;
        } catch (CajaCerradaException e) {
            return "redirect:/cobrar?socioId=" + socioId + "&error=cajaCerrada";
        } catch (IllegalStateException e) {
            return "redirect:/cobrar?socioId=" + socioId + "&error=cuotaYaPagada";
        } catch (IllegalArgumentException e) {
            return "redirect:/cobrar?socioId=" + socioId + "&error=pagoInvalido";
        }
    }

    private LocalDate parseFechaONull(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        return LocalDate.parse(fecha);
    }

    private String normalizarDni(String dni) {
        String limpio = limpiar(dni);
        return limpio == null ? null : limpio.replaceAll("[.\\s-]", "");
    }

    private String limpiar(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
