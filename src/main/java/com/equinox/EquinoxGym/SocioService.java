package com.equinox.EquinoxGym;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
public class SocioService {

    private final SocioRepository socioRepository;
    private final CuotaService cuotaService;

    public SocioService(SocioRepository socioRepository, CuotaService cuotaService) {
        this.socioRepository = socioRepository;
        this.cuotaService = cuotaService;
    }

    public boolean actualizarEstadoSocio(Socio socio) {
        if (socio == null) {
            return false;
        }

        EstadoSocio estadoAnterior = socio.getEstado();
        boolean cambioAlgunaCuota = false;
        LocalDate hoy = LocalDate.now();

        boolean tieneVencida = false;
        boolean tieneInactivaPor20Dias = false;

        for (Cuota cuota : socio.getCuotas()) {
            cambioAlgunaCuota |= cuotaService.actualizarEstadoCuota(cuota);

            if (cuota.getEstado() == EstadoCuota.VENCIDA) {
                tieneVencida = true;

                long diasAtraso = ChronoUnit.DAYS.between(cuota.getFechaVencimiento(), hoy);
                if (diasAtraso >= 20) {
                    tieneInactivaPor20Dias = true;
                }
            }
        }

        if (socio.isBaja()) {
            // La baja manual manda: no se recalcula el estado a partir de las
            // cuotas, si no el socio volveria a figurar activo al recargar.
            socio.setEstado(EstadoSocio.INACTIVO);
        } else if (tieneInactivaPor20Dias) {
            socio.setEstado(EstadoSocio.INACTIVO);
        } else if (tieneVencida) {
            socio.setEstado(EstadoSocio.MOROSO);
        } else {
            socio.setEstado(EstadoSocio.ACTIVO);
        }

        boolean cambioVigencia = sincronizarVigencia(socio);

        return cambioAlgunaCuota || cambioVigencia || estadoAnterior != socio.getEstado();
    }

    /**
     * Deja "Vence plan" igual al vencimiento de la cuota que falta pagar, y el
     * inicio del plan un periodo antes. Es lo que ya hacen el alta, el cobro y
     * la anulacion; esto cubre las ediciones a mano, que dejaban dos fechas
     * distintas en pantalla ("Faltan 21 dias" y a la vez la cuota vencida).
     * Como se llama cada vez que se recalcula el estado, los socios que ya
     * quedaron desalineados se corrigen solos la proxima vez que se abren.
     *
     * @return si cambio alguna de las dos fechas
     */
    boolean sincronizarVigencia(Socio socio) {
        if (socio.getPlan() == null || socio.getPlan().getDuracionMeses() <= 0 || socio.getCuotas() == null) {
            return false;
        }
        LocalDate proximoVencimiento = socio.getCuotas().stream()
                .filter(cuota -> cuota.getFechaPago() == null && cuota.getFechaVencimiento() != null)
                .map(Cuota::getFechaVencimiento)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (proximoVencimiento == null) {
            return false;
        }
        LocalDate inicio = proximoVencimiento.minusMonths(socio.getPlan().getDuracionMeses());
        boolean cambio = !proximoVencimiento.equals(socio.getFechaVencimientoPlan())
                || !inicio.equals(socio.getFechaInicioPlan());
        socio.setFechaVencimientoPlan(proximoVencimiento);
        socio.setFechaInicioPlan(inicio);
        return cambio;
    }

    public List<Socio> listarTodosActualizados() {
        List<Socio> socios = socioRepository.findAll();

        List<Socio> modificados = socios.stream()
                .filter(this::actualizarEstadoSocio)
                .toList();
        if (!modificados.isEmpty()) {
            socioRepository.saveAll(modificados);
        }

        return socios;
    }
}
