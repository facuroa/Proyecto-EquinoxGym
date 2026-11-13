package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Eliminaciones definitivas de socios, cuotas y pagos. Son para corregir
 * errores de carga (un socio cargado dos veces, un cobro de prueba): lo normal
 * sigue siendo dar de baja o anular, que conservan el historial.
 *
 * Cada eliminacion exige escribir la palabra de confirmacion y un motivo, y
 * deja en Auditoria quien la hizo y una copia de lo que se borro.
 *
 * Los movimientos de caja de los pagos borrados se tratan segun la caja: si
 * esta abierta se quitan, asi el efectivo esperado del arqueo se corrige; si
 * ya se cerro se conservan, para no alterar un arqueo que ya se hizo.
 */
@Service
@Transactional
public class EliminacionService {

    public static final String PALABRA_CONFIRMACION = "ELIMINAR";

    private static final int MOTIVO_MINIMO = 5;
    private static final int MOTIVO_MAXIMO = 300;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SocioRepository socioRepository;
    private final CuotaRepository cuotaRepository;
    private final PagoRepository pagoRepository;
    private final MovimientoCajaRepository movimientoRepository;
    private final SeguimientoMorosidadRepository seguimientoRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final SocioService socioService;
    private final AuditoriaService auditoriaService;

    public EliminacionService(SocioRepository socioRepository,
                              CuotaRepository cuotaRepository,
                              PagoRepository pagoRepository,
                              MovimientoCajaRepository movimientoRepository,
                              SeguimientoMorosidadRepository seguimientoRepository,
                              AsistenciaRepository asistenciaRepository,
                              SocioService socioService,
                              AuditoriaService auditoriaService) {
        this.socioRepository = socioRepository;
        this.cuotaRepository = cuotaRepository;
        this.pagoRepository = pagoRepository;
        this.movimientoRepository = movimientoRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.socioService = socioService;
        this.auditoriaService = auditoriaService;
    }

    /** Lo que se borraria junto con un socio, para avisarlo antes de confirmar. */
    @Transactional(readOnly = true)
    public ImpactoEliminacion impactoSocio(Socio socio) {
        List<Pago> pagos = pagoRepository.findByCuota_Socio_Id(socio.getId());
        return new ImpactoEliminacion(
                (int) cuotaRepository.countBySocio_Id(socio.getId()),
                pagos.size(),
                totalConfirmado(pagos),
                asistenciaRepository.countBySocio_Id(socio.getId()));
    }

    // ------------------------------------------------------------------
    // Eliminaciones definitivas (solo administracion)
    // ------------------------------------------------------------------

    /** @return el nombre del socio eliminado, para confirmarlo en pantalla */
    public String eliminarSocio(Long socioId, String confirmacion, String motivo, String usuario) {
        String motivoLimpio = validarConfirmacion(confirmacion, motivo);
        Socio socio = socioId == null ? null : socioRepository.findById(socioId).orElse(null);
        if (socio == null) {
            throw new IllegalArgumentException("El socio ya no existe.");
        }

        String nombre = describirSocio(socio);
        List<Pago> pagos = pagoRepository.findByCuota_Socio_Id(socioId);
        BigDecimal total = totalConfirmado(pagos);
        long anulados = pagos.stream().filter(Pago::isAnulado).count();
        EfectoCaja caja = new EfectoCaja();
        for (Pago pago : pagos) {
            caja.sumar(quitarDeCaja(pago));
            pago.setCuotaRenovacionGenerada(null);
            pagoRepository.delete(pago);
        }
        pagoRepository.flush();

        // Las cuotas se buscan en la base y no solo en la lista del socio, asi
        // no queda ninguna colgada que impida borrarlo.
        List<Cuota> cuotasDelSocio = cuotaRepository.findBySocio_Id(socioId);
        int cuotas = cuotasDelSocio.size();
        if (socio.getCuotas() != null) {
            socio.getCuotas().clear();
        }
        cuotaRepository.deleteAll(cuotasDelSocio);
        cuotaRepository.flush();

        long ingresos = asistenciaRepository.countBySocio_Id(socioId);
        asistenciaRepository.deleteBySocio_Id(socioId);
        seguimientoRepository.deleteBySocio_Id(socioId);
        socioRepository.delete(socio);
        socioRepository.flush();

        auditoriaService.registrar(usuario, "Socio eliminado (definitivo)",
                nombre + " · " + plural(cuotas, "cuota", "cuotas")
                        + " · " + plural(pagos.size(), "pago", "pagos") + " por $ " + total
                        + (anulados > 0 ? " (" + plural(anulados, "anulado", "anulados") + ")" : "")
                        + " · " + plural(ingresos, "ingreso", "ingresos")
                        + caja.texto() + " · motivo: " + motivoLimpio);
        return socio.getNombreCompleto().trim();
    }

    /** @return una descripcion corta de la cuota eliminada */
    public String eliminarCuota(Long cuotaId, String confirmacion, String motivo, String usuario) {
        String motivoLimpio = validarConfirmacion(confirmacion, motivo);
        Cuota cuota = cuotaId == null ? null : cuotaRepository.findById(cuotaId).orElse(null);
        if (cuota == null) {
            throw new IllegalArgumentException("La cuota ya no existe.");
        }

        Socio socio = cuota.getSocio();
        String descripcion = describirCuota(cuota);
        List<Pago> pagos = pagoRepository.findByCuota_Id(cuotaId);
        BigDecimal total = totalConfirmado(pagos);
        EfectoCaja caja = new EfectoCaja();
        for (Pago pago : pagos) {
            // La renovacion que genero este pago se conserva: es la cuota
            // del periodo siguiente y se puede eliminar aparte si sobra.
            caja.sumar(quitarDeCaja(pago));
            pago.setCuotaRenovacionGenerada(null);
            pagoRepository.delete(pago);
        }
        pagoRepository.flush();
        quitarCuota(cuota);
        recalcular(socio);

        auditoriaService.registrar(usuario, "Cuota eliminada (definitivo)",
                descripcion + " · " + plural(pagos.size(), "pago eliminado", "pagos eliminados")
                        + (pagos.isEmpty() ? "" : " por $ " + total)
                        + caja.texto() + " · motivo: " + motivoLimpio);
        return descripcion;
    }

    /**
     * Si el pago estaba confirmado, la cuota vuelve a quedar pendiente y se
     * borra la renovacion que habia generado, igual que al anular. Si esa
     * renovacion ya se cobro, hay que eliminar primero ese pago.
     *
     * @return el numero de comprobante eliminado
     */
    public String eliminarPago(Long pagoId, String confirmacion, String motivo, String usuario) {
        String motivoLimpio = validarConfirmacion(confirmacion, motivo);
        Pago pago = pagoId == null ? null : pagoRepository.findById(pagoId).orElse(null);
        if (pago == null) {
            throw new IllegalArgumentException("El pago ya no existe.");
        }

        Cuota cuota = pago.getCuota();
        Socio socio = cuota != null ? cuota.getSocio() : null;
        String numero = pago.getNumeroComprobante();
        String descripcion = numero + " · " + (socio != null ? describirSocio(socio) : "sin socio")
                + " · $ " + pago.getMonto() + " · " + pago.getMedioPago()
                + (pago.getFechaPago() != null ? " · " + FORMATO_FECHA.format(pago.getFechaPago()) : "")
                + (pago.isAnulado() ? " · estaba anulado" : "");

        if (!pago.isAnulado()) {
            Cuota renovacion = pago.getCuotaRenovacionGenerada();
            if (renovacion != null) {
                if (renovacion.getFechaPago() != null || pagoRepository.existsByCuota_Id(renovacion.getId())) {
                    throw new IllegalStateException("No se puede eliminar " + numero
                            + ": la cuota siguiente que generó este pago ya tiene un pago. Eliminá primero ese pago.");
                }
                pago.setCuotaRenovacionGenerada(null);
                pagoRepository.saveAndFlush(pago);
                quitarCuota(renovacion);
            }
            if (cuota != null) {
                cuota.setFechaPago(null);
            }
        }

        EfectoCaja caja = new EfectoCaja();
        caja.sumar(quitarDeCaja(pago));
        pagoRepository.delete(pago);
        pagoRepository.flush();
        recalcular(socio);

        auditoriaService.registrar(usuario, "Pago eliminado (definitivo)",
                descripcion + caja.texto() + " · motivo: " + motivoLimpio);
        return numero;
    }

    // ------------------------------------------------------------------
    // Eliminaciones simples (las que ya existian, ahora auditadas)
    // ------------------------------------------------------------------

    /** Cuota sin ningun pago asociado: la puede quitar cualquier usuario. */
    public String eliminarCuotaSinPagos(Long cuotaId, String usuario) {
        Cuota cuota = cuotaId == null ? null : cuotaRepository.findById(cuotaId).orElse(null);
        if (cuota == null) {
            throw new IllegalArgumentException("La cuota ya no existe.");
        }
        if (pagoRepository.existsByCuota_Id(cuotaId) || pagoRepository.existsByCuotaRenovacionGenerada_Id(cuotaId)) {
            throw new IllegalStateException("No se puede eliminar una cuota vinculada al historial de pagos.");
        }
        Socio socio = cuota.getSocio();
        String descripcion = describirCuota(cuota);
        quitarCuota(cuota);
        recalcular(socio);
        auditoriaService.registrar(usuario, "Cuota eliminada", descripcion);
        return descripcion;
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private String validarConfirmacion(String confirmacion, String motivo) {
        if (confirmacion == null || !PALABRA_CONFIRMACION.equalsIgnoreCase(confirmacion.trim())) {
            throw new IllegalArgumentException("Para confirmar escribí " + PALABRA_CONFIRMACION + ".");
        }
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.length() < MOTIVO_MINIMO || limpio.length() > MOTIVO_MAXIMO) {
            throw new IllegalArgumentException("Contá en pocas palabras por qué se elimina (entre "
                    + MOTIVO_MINIMO + " y " + MOTIVO_MAXIMO + " caracteres).");
        }
        return limpio;
    }

    /**
     * Saca la cuota de la lista del socio antes de borrarla. Si no, al
     * guardar el socio la lista la vuelve a grabar y la cuota "eliminada"
     * sigue apareciendo.
     */
    private void quitarCuota(Cuota cuota) {
        for (Pago pago : pagoRepository.findByCuotaRenovacionGenerada_Id(cuota.getId())) {
            pago.setCuotaRenovacionGenerada(null);
            pagoRepository.save(pago);
        }
        Socio socio = cuota.getSocio();
        if (socio != null && socio.getCuotas() != null) {
            socio.getCuotas().removeIf(c -> c.getId() != null && c.getId().equals(cuota.getId()));
        }
        cuotaRepository.delete(cuota);
        cuotaRepository.flush();
    }

    private EfectoCaja quitarDeCaja(Pago pago) {
        EfectoCaja efecto = new EfectoCaja();
        for (MovimientoCaja movimiento : movimientoRepository.findByPago_Id(pago.getId())) {
            if (movimiento.getCaja() != null && movimiento.getCaja().getEstado() == EstadoCaja.ABIERTA) {
                movimientoRepository.delete(movimiento);
                efecto.quitados++;
            } else {
                movimiento.setPago(null);
                movimientoRepository.save(movimiento);
                efecto.conservados++;
            }
        }
        return efecto;
    }

    private void recalcular(Socio socio) {
        if (socio == null) {
            return;
        }
        socioService.actualizarEstadoSocio(socio);
        socioRepository.save(socio);
    }

    private static BigDecimal totalConfirmado(List<Pago> pagos) {
        return pagos.stream()
                .filter(pago -> !pago.isAnulado() && pago.getMonto() != null)
                .map(Pago::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String describirSocio(Socio socio) {
        return socio.getNombreCompleto().trim() + " (DNI " + socio.getDni() + ")";
    }

    private static String describirCuota(Cuota cuota) {
        return "Cuota de " + (cuota.getSocio() != null ? describirSocio(cuota.getSocio()) : "sin socio")
                + (cuota.getFechaVencimiento() != null
                        ? " con vencimiento " + FORMATO_FECHA.format(cuota.getFechaVencimiento()) : "")
                + " · $ " + cuota.getMonto();
    }

    private static String plural(long cantidad, String singular, String plural) {
        return cantidad + " " + (cantidad == 1 ? singular : plural);
    }

    /** Cuantos movimientos de caja se quitaron y cuantos se conservaron. */
    private static final class EfectoCaja {
        private int quitados;
        private int conservados;

        private void sumar(EfectoCaja otro) {
            quitados += otro.quitados;
            conservados += otro.conservados;
        }

        private String texto() {
            if (quitados == 0 && conservados == 0) {
                return "";
            }
            StringBuilder texto = new StringBuilder(" · caja:");
            if (quitados > 0) {
                texto.append(" ").append(plural(quitados, "movimiento quitado", "movimientos quitados"))
                        .append(" de la caja abierta");
            }
            if (conservados > 0) {
                texto.append(quitados > 0 ? " y" : "").append(" ")
                        .append(plural(conservados, "movimiento conservado", "movimientos conservados"))
                        .append(" en cajas cerradas");
            }
            return texto.toString();
        }
    }

    /** Resumen de lo que arrastra eliminar un socio. */
    public record ImpactoEliminacion(int cuotas, int pagos, BigDecimal totalPagado, long ingresos) {
    }
}
