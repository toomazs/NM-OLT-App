package models;

public class Usuario {
    private String nome;
    private String usuario;
    private String cargo;
    private String status;
    private byte[] fotoPerfil;
    private String abaAtual;


    public Usuario(String nome, String usuario, String cargo, String status, byte[] fotoPerfil, String abaAtual) {
        this.nome = nome;
        this.usuario = usuario;
        this.cargo = cargo;
        this.status = status;
        this.fotoPerfil = fotoPerfil;
        this.abaAtual = abaAtual;
    }


    public String getNome() { return nome; }
    public String getUsuario() { return usuario; }
    public String getCargo() { return cargo; }
    public String getStatus() { return status; }
    public byte[] getFotoPerfil() {
        return fotoPerfil;
    }
    public String getAbaAtual() { return abaAtual; }

}
