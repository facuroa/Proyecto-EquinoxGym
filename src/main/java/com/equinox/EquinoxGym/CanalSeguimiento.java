package com.equinox.EquinoxGym;

public enum CanalSeguimiento {
    WHATSAPP("WhatsApp"),
    LLAMADA("Llamada"),
    PRESENCIAL("Presencial"),
    EMAIL("Email"),
    OTRO("Otro");

    private final String descripcion;

    CanalSeguimiento(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}

