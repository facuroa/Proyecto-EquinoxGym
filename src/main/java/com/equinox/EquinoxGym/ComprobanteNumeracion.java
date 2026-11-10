package com.equinox.EquinoxGym;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fija el prefijo de los numeros de comprobante al arrancar, segun lo que
 * tenga configurado cada instalacion (COMPROBANTE_PREFIJO en equinox.env).
 *
 * Existe para que Pago, que es una entidad y no pasa por el contenedor de
 * Spring, pueda armar "TRP-00000012" sin que cada plantilla tenga que
 * conocer el prefijo.
 */
@Component
public class ComprobanteNumeracion {

    public ComprobanteNumeracion(
            @Value("${equinox.branding.comprobante-prefijo:EQX}") String prefijo) {
        Pago.configurarPrefijo(prefijo);
    }
}
