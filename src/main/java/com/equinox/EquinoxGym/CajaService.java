package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class CajaService {

    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("999999999.99");

    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoRepository;
    private final AuditoriaService auditoriaService;

    public CajaService(CajaRepository cajaRepository,
                       MovimientoCajaRepository movimientoRepository,
                       AuditoriaService auditoriaService) {
        this.cajaRepository = cajaRepository;
        this.movimientoRepository = movimientoRepository;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public CajaResumenDTO obtenerResumen(String usuario, boolean administrador) {
        String usuarioNormalizado = normalizarUsuario(usuario);
        CajaResumenDTO resumen = new CajaResumenDTO();
        resumen.setAdministrador(administrador);

        CajaSesion cajaActual = cajaRepository
                .findFirstByUsuarioAperturaAndEstadoOrderByFechaAperturaDesc(
                        usuarioNormalizado, EstadoCaja.ABIERTA)
                .orElse(null);
        resumen.setCajaActual(cajaActual);

        if (cajaActual != null) {
            List<MovimientoCaja> movimientos = movimientoRepository
                    .findByCaja_IdOrderByFechaRegistroDescIdDesc(cajaActual.getId());
            resumen.setMovimientos(movimientos);
            calcularTotales(resumen, movimientos);
            resumen.setSaldoEsperado(calcularSaldoEsperado(cajaActual, movimientos));
        }

        resumen.setHistorial(administrador
                ? cajaRepository.findTop20ByEstadoOrderByFechaCierreDesc(EstadoCaja.CERRADA)
                : cajaRepository.findTop20ByUsuarioAperturaAndEstadoOrderByFechaCierreDesc(
                        usuarioNormalizado, EstadoCaja.CERRADA));
        return resumen;
    }

    public CajaSesion abrirCaja(String usuario, BigDecimal montoInicial, String observaciones) {
        String usuarioNormalizado = normalizarUsuario(usuario);
        if (esUsuarioSistema(usuarioNormalizado)) {
            throw new IllegalStateException("No se puede abrir una caja sin un usuario autenticado.");
        }
        if (cajaRepository.findFirstByUsuarioAperturaAndEstadoOrderByFechaAperturaDesc(
                usuarioNormalizado, EstadoCaja.ABIERTA).isPresent()) {
            throw new IllegalStateException("Ya tenés una caja abierta.");
        }

        CajaSesion caja = new CajaSesion();
        caja.setUsuarioApertura(usuarioNormalizado);
        caja.setFechaApertura(LocalDateTime.now());
        caja.setMontoInicial(validarMonto(montoInicial, true, "El fondo inicial"));
        caja.setObservacionesApertura(validarTextoOpcional(observaciones, 500, "La observación de apertura"));
        caja.setEstado(EstadoCaja.ABIERTA);
        CajaSesion cajaGuardada = cajaRepository.save(caja);
        auditoriaService.registrar(usuarioNormalizado, "Caja abierta",
                "Fondo inicial: $ " + cajaGuardada.getMontoInicial());
        return cajaGuardada;
    }

    public MovimientoCaja registrarMovimientoManual(String usuario,
                                                     TipoMovimientoCaja tipo,
                                                     BigDecimal monto,
                                                     String concepto) {
        String usuarioNormalizado = normalizarUsuario(usuario);
        CajaSesion caja = obtenerCajaAbierta(usuarioNormalizado);
        if (tipo == null) {
            throw new IllegalArgumentException("Seleccioná si el movimiento es un ingreso o un egreso.");
        }
        String conceptoLimpio = validarConcepto(concepto);
        OrigenMovimientoCaja origen = tipo == TipoMovimientoCaja.INGRESO
                ? OrigenMovimientoCaja.INGRESO_MANUAL
                : OrigenMovimientoCaja.EGRESO_MANUAL;
        MovimientoCaja movimiento = guardarMovimiento(caja, tipo, origen, validarMonto(monto, false, "El monto"),
                conceptoLimpio, usuarioNormalizado, null);
        auditoriaService.registrar(usuarioNormalizado, "Movimiento de caja",
                tipo == TipoMovimientoCaja.INGRESO ? "Ingreso: " + conceptoLimpio : "Egreso: " + conceptoLimpio);
        return movimiento;
    }

    public CajaSesion cerrarCaja(String usuario,
                                 Long cajaId,
                                 BigDecimal montoDeclarado,
                                 String observaciones) {
        String usuarioNormalizado = normalizarUsuario(usuario);
        CajaSesion caja = cajaRepository.findByIdForUpdate(cajaId)
                .orElseThrow(() -> new IllegalArgumentException("La caja seleccionada ya no existe."));
        if (caja.getEstado() != EstadoCaja.ABIERTA) {
            throw new IllegalStateException("Esta caja ya fue cerrada.");
        }
        if (!caja.getUsuarioApertura().equals(usuarioNormalizado)) {
            throw new IllegalStateException("Solo quien abrió la caja puede cerrarla.");
        }

        BigDecimal declarado = validarMonto(montoDeclarado, true, "El efectivo contado");
        List<MovimientoCaja> movimientos = movimientoRepository
                .findByCaja_IdOrderByFechaRegistroDescIdDesc(caja.getId());
        BigDecimal esperado = calcularSaldoEsperado(caja, movimientos);

        caja.setEstado(EstadoCaja.CERRADA);
        caja.setFechaCierre(LocalDateTime.now());
        caja.setUsuarioCierre(usuarioNormalizado);
        caja.setMontoEsperadoCierre(esperado);
        caja.setMontoDeclaradoCierre(declarado);
        caja.setDiferenciaCierre(declarado.subtract(esperado));
        caja.setObservacionesCierre(validarTextoOpcional(
                observaciones, 500, "La observación de cierre"));
        CajaSesion cajaCerrada = cajaRepository.save(caja);
        auditoriaService.registrar(usuarioNormalizado, "Caja cerrada",
                "Efectivo esperado: $ " + esperado + " · contado: $ " + declarado
                        + " · diferencia: $ " + cajaCerrada.getDiferenciaCierre());
        return cajaCerrada;
    }

    public void validarCajaParaPago(String medioPago, String usuario) {
        String usuarioNormalizado = normalizarUsuario(usuario);
        if (esUsuarioSistema(usuarioNormalizado)) {
            return;
        }
        obtenerCajaAbierta(usuarioNormalizado);
    }

    public void registrarPago(Pago pago, String usuario) {
        if (pago == null) {
            return;
        }
        String usuarioNormalizado = normalizarUsuario(usuario);
        if (esUsuarioSistema(usuarioNormalizado)) {
            return;
        }
        OrigenMovimientoCaja origen = origenPago(pago.getMedioPago());
        if (pago.getId() != null && movimientoRepository.existsByPago_IdAndOrigen(pago.getId(), origen)) {
            return;
        }
        CajaSesion caja = obtenerCajaAbierta(usuarioNormalizado);
        String socio = pago.getCuota() != null && pago.getCuota().getSocio() != null
                ? pago.getCuota().getSocio().getNombreCompleto().trim() : "socio";
        guardarMovimiento(caja, TipoMovimientoCaja.INGRESO, origen,
                validarMonto(pago.getMonto(), false, "El pago"),
                "Cobro de cuota · " + socio, usuarioNormalizado, pago);
    }

    public void registrarAnulacionPago(Pago pago, String usuario) {
        if (pago == null) {
            return;
        }
        String usuarioNormalizado = normalizarUsuario(usuario);
        if (esUsuarioSistema(usuarioNormalizado)) {
            return;
        }
        OrigenMovimientoCaja origen = origenAnulacion(pago.getMedioPago());
        if (pago.getId() != null && movimientoRepository.existsByPago_IdAndOrigen(pago.getId(), origen)) {
            return;
        }
        CajaSesion caja = obtenerCajaAbierta(usuarioNormalizado);
        guardarMovimiento(caja, TipoMovimientoCaja.EGRESO, origen,
                validarMonto(pago.getMonto(), false, "El pago"),
                "Anulación · " + pago.getNumeroComprobante(), usuarioNormalizado, pago);
    }

    private CajaSesion obtenerCajaAbierta(String usuario) {
        return cajaRepository.findFirstByUsuarioAperturaAndEstadoOrderByFechaAperturaDesc(
                usuario, EstadoCaja.ABIERTA)
                .orElseThrow(() -> new CajaCerradaException(
                        "Abrí tu caja antes de registrar pagos o movimientos."));
    }

    private MovimientoCaja guardarMovimiento(CajaSesion caja,
                                              TipoMovimientoCaja tipo,
                                              OrigenMovimientoCaja origen,
                                              BigDecimal monto,
                                              String concepto,
                                              String usuario,
                                              Pago pago) {
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setCaja(caja);
        movimiento.setTipo(tipo);
        movimiento.setOrigen(origen);
        movimiento.setMonto(monto);
        movimiento.setConcepto(concepto);
        movimiento.setFechaRegistro(LocalDateTime.now());
        movimiento.setRegistradoPor(usuario);
        movimiento.setPago(pago);
        return movimientoRepository.save(movimiento);
    }

    private void calcularTotales(CajaResumenDTO resumen, List<MovimientoCaja> movimientos) {
        BigDecimal cobros = BigDecimal.ZERO;
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal egresos = BigDecimal.ZERO;
        BigDecimal transferencias = BigDecimal.ZERO;
        BigDecimal tarjetas = BigDecimal.ZERO;
        BigDecimal otrosMedios = BigDecimal.ZERO;
        for (MovimientoCaja movimiento : movimientos) {
            BigDecimal montoFirmado = movimiento.getTipo() == TipoMovimientoCaja.INGRESO
                    ? dinero(movimiento.getMonto()) : dinero(movimiento.getMonto()).negate();
            if (movimiento.getOrigen().isTransferencia()) {
                transferencias = transferencias.add(montoFirmado);
            } else if (movimiento.getOrigen().isTarjeta()) {
                tarjetas = tarjetas.add(montoFirmado);
            } else if (movimiento.getOrigen().isOtroMedio()) {
                otrosMedios = otrosMedios.add(montoFirmado);
            } else if (movimiento.getTipo() == TipoMovimientoCaja.EGRESO) {
                egresos = egresos.add(dinero(movimiento.getMonto()));
            } else if (movimiento.getOrigen() == OrigenMovimientoCaja.PAGO_EFECTIVO) {
                cobros = cobros.add(dinero(movimiento.getMonto()));
            } else {
                ingresos = ingresos.add(dinero(movimiento.getMonto()));
            }
        }
        resumen.setCobrosEfectivo(cobros);
        resumen.setIngresosManuales(ingresos);
        resumen.setEgresos(egresos);
        resumen.setTransferencias(transferencias);
        resumen.setTarjetas(tarjetas);
        resumen.setOtrosMedios(otrosMedios);
    }

    private BigDecimal calcularSaldoEsperado(CajaSesion caja, List<MovimientoCaja> movimientos) {
        BigDecimal saldo = dinero(caja.getMontoInicial());
        for (MovimientoCaja movimiento : movimientos) {
            if (!movimiento.getOrigen().isAfectaEfectivo()) {
                continue;
            }
            BigDecimal monto = dinero(movimiento.getMonto());
            saldo = movimiento.getTipo() == TipoMovimientoCaja.INGRESO
                    ? saldo.add(monto) : saldo.subtract(monto);
        }
        return saldo;
    }

    private BigDecimal validarMonto(BigDecimal monto, boolean permiteCero, String nombre) {
        if (monto == null || (permiteCero ? monto.signum() < 0 : monto.signum() <= 0)) {
            throw new IllegalArgumentException(nombre + (permiteCero
                    ? " no puede ser negativo." : " debe ser mayor a cero."));
        }
        if (monto.compareTo(MONTO_MAXIMO) > 0) {
            throw new IllegalArgumentException(nombre + " supera el máximo permitido.");
        }
        return monto.setScale(2, RoundingMode.HALF_UP);
    }

    private String validarConcepto(String concepto) {
        String limpio = concepto == null ? "" : concepto.trim();
        if (limpio.length() < 3 || limpio.length() > 250) {
            throw new IllegalArgumentException("El concepto debe tener entre 3 y 250 caracteres.");
        }
        return limpio;
    }

    private String validarTextoOpcional(String texto, int maximo, String nombre) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException(nombre + " no puede superar los " + maximo + " caracteres.");
        }
        return limpio;
    }

    private BigDecimal dinero(BigDecimal monto) {
        return monto == null ? BigDecimal.ZERO : monto;
    }

    private OrigenMovimientoCaja origenPago(String medioPago) {
        if (medioPago == null) {
            return OrigenMovimientoCaja.PAGO_OTRO;
        }
        return switch (medioPago.trim().toLowerCase()) {
            case "efectivo" -> OrigenMovimientoCaja.PAGO_EFECTIVO;
            case "transferencia" -> OrigenMovimientoCaja.PAGO_TRANSFERENCIA;
            case "tarjeta" -> OrigenMovimientoCaja.PAGO_TARJETA;
            default -> OrigenMovimientoCaja.PAGO_OTRO;
        };
    }

    private OrigenMovimientoCaja origenAnulacion(String medioPago) {
        if (medioPago == null) {
            return OrigenMovimientoCaja.ANULACION_OTRO;
        }
        return switch (medioPago.trim().toLowerCase()) {
            case "efectivo" -> OrigenMovimientoCaja.ANULACION_PAGO;
            case "transferencia" -> OrigenMovimientoCaja.ANULACION_TRANSFERENCIA;
            case "tarjeta" -> OrigenMovimientoCaja.ANULACION_TARJETA;
            default -> OrigenMovimientoCaja.ANULACION_OTRO;
        };
    }

    private String normalizarUsuario(String usuario) {
        return usuario == null || usuario.isBlank() ? "sistema" : usuario.trim();
    }

    private boolean esUsuarioSistema(String usuario) {
        return "sistema".equalsIgnoreCase(usuario);
    }
}
