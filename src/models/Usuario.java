package models;

public class Usuario {
    private String nome;
    private String usuario;
    private String cargo;
    private boolean isAdmin;

    public Usuario(String nome, String usuario, String cargo, boolean isAdmin) {
        this.nome = nome;
        this.usuario = usuario;
        this.cargo = cargo;
        this.isAdmin = isAdmin;
    }

    public String getNome() { return nome; }
    public String getUsuario() { return usuario; }
    public String getCargo() { return cargo; }
    public boolean isAdmin() { return isAdmin; }

    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }
}
