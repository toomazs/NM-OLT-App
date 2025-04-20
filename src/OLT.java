public class OLT {
    public String name;
    public String ip;

    public OLT(String name, String ip) {
        this.name = name;
        this.ip = ip;
    }

    @Override
    public String toString() {
        return name + " (" + ip + ")";
    }
}
