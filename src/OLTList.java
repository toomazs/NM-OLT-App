import java.util.ArrayList;
import java.util.List;

public class OLTList {
    public static List<OLT> getOLTs() {
        List<OLT> olts = new ArrayList<>();
        olts.add(new OLT("OLT_CCDA_01", "10.0.41.34"));
        olts.add(new OLT("OLT_CCDA_02", "10.0.41.54"));
        olts.add(new OLT("OLT_COTIA_01", "10.0.41.58"));
        olts.add(new OLT("OLT_COTIA_02", "10.0.41.62"));
        olts.add(new OLT("OLT_COTIA_03", "10.0.41.66"));
        olts.add(new OLT("OLT_COTIA_04", "10.0.41.70"));
        olts.add(new OLT("OLT_COTIA_05", "10.0.41.74"));
        olts.add(new OLT("OLT_EMBU_01", "10.0.41.18"));
        olts.add(new OLT("OLT_GRVN_01", "10.0.41.46"));
        olts.add(new OLT("OLT_ITPV_01", "10.0.42.10"));
        olts.add(new OLT("OLT_TRMS_01", "10.0.41.38"));
        olts.add(new OLT("OLT_TRMS_02", "10.0.41.50"));
        olts.add(new OLT("OLT_VGPA_01", "10.0.41.30"));
        return olts;
    }
}
