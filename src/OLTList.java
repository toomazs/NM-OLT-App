import java.util.ArrayList;
import java.util.List;

public class OLTList {
    public static List<OLT> getOLTs() {
        List<OLT> olts = new ArrayList<>();
        for (String[] entry : Secrets.OLT_LIST) {
            olts.add(new OLT(entry[0], entry[1]));
        }
        return olts;
    }
}
