package com.equinox.EquinoxGym;

/**
 * Objeto liviano que devuelve el buscador de socios (vía JSON) en la
 * pantalla de Cobro. No expone la entidad Socio completa (con sus cuotas,
 * plan, etc.) porque para mostrar la lista de sugerencias del buscador
 * alcanza con estos 4 datos.
 */
public class SocioBusquedaDTO {

    private Long id;
    private String nombreCompleto;
    private String dni;
    private String estado;

    public SocioBusquedaDTO(Long id, String nombreCompleto, String dni, String estado) {
        this.id = id;
        this.nombreCompleto = nombreCompleto;
        this.dni = dni;
        this.estado = estado;
    }

    public Long getId() { return id; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getDni() { return dni; }
    public String getEstado() { return estado; }
}
