package com.equinox.EquinoxGym;

/** Que paso al intentar registrar el ingreso de un socio. */
public enum ResultadoIngreso {
    /** El ingreso quedo guardado. */
    REGISTRADO,
    /** El socio ya habia ingresado hoy: se puede forzar un segundo turno. */
    YA_REGISTRADO,
    /** El socio ya uso todos los ingresos permitidos para el dia. */
    LIMITE_DIARIO
}
