package models;

public class Usuario {
    private String nome;
    private String usuario;
    private String cargo;

    public Usuario(String nome, String usuario, String cargo) {
        this.nome = nome;
        this.usuario = usuario;
        this.cargo = cargo;
    }

    public String getNome() { return nome; }
    public String getUsuario() { return usuario; }
    public String getCargo() { return cargo; }
}
