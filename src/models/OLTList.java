package models;
import java.util.ArrayList;
import java.util.List;
import utils.ConfigManager;

public class OLTList {
    private static List<OLT> dynamicOLTs = new ArrayList<>();
    private static boolean initialized = false;

    private static void initialize() {
        if (!initialized) {
            dynamicOLTs = ConfigManager.getInstance().getDynamicOLTs();
            initialized = true;
        }
    }

    public static List<OLT> getOLTs() {
        initialize();
        List<OLT> allOLTs = new ArrayList<>();

        for (String[] entry : Secrets.OLT_LIST) {
            allOLTs.add(new OLT(entry[0], entry[1]));
        }

        allOLTs.addAll(dynamicOLTs);

        return allOLTs;
    }

    public static void addOLT(OLT olt) {
        initialize();
        if (olt != null && !isDuplicate(olt)) {
            dynamicOLTs.add(olt);
            ConfigManager.getInstance().saveDynamicOLTs(dynamicOLTs);
        }
    }

    public static void removeOLT(OLT olt) {
        initialize();
        if (olt != null) {
            dynamicOLTs.removeIf(dynamicOlt ->
                    dynamicOlt.name.equals(olt.name) && dynamicOlt.ip.equals(olt.ip));
            ConfigManager.getInstance().saveDynamicOLTs(dynamicOLTs);
        }
    }

    private static boolean isDuplicate(OLT newOlt) {
        List<OLT> allOLTs = getOLTs();
        for (OLT existingOlt : allOLTs) {
            if (existingOlt.name.equalsIgnoreCase(newOlt.name) || existingOlt.getIp().equals(newOlt.getIp())) {
                return true;
            }
        }
        return false;
    }

    public static boolean canRemove(OLT olt) {
        for (String[] entry : Secrets.OLT_LIST) {
            if (entry[0].equals(olt.name) && entry[1].equals(olt.ip)) {
                return false;
            }
        }
        return true;
    }

    public static List<OLT> getDynamicOLTs() {
        initialize();
        return new ArrayList<>(dynamicOLTs);
    }
}