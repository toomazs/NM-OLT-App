package models;

import java.util.Objects;

public class OLT {
    public String name;
    public String ip;
    public String port;
    public String user;
    public String password;

    public OLT(String name, String ip, String port, String user, String password) {
        this.name = name;
        this.ip = ip;
        this.port = (port == null || port.isEmpty()) ? "22" : port;
        this.user = (user == null || user.isEmpty()) ? Secrets.SSH_USER : user;
        this.password = (password == null || password.isEmpty()) ? Secrets.SSH_PASS : password;
    }

    public OLT(String name, String ip, String port) {
        this(name, ip, port, Secrets.SSH_USER, Secrets.SSH_PASS);
    }

    public OLT(String name, String ip) {
        this(name, ip, "22", Secrets.SSH_USER, Secrets.SSH_PASS);
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public String getPort() {
        return port;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return name + " (" + ip + ":" + port + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OLT olt = (OLT) o;
        return Objects.equals(ip, olt.ip) && Objects.equals(port, olt.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }
}