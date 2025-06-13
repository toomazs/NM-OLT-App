import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// Deus abencoe a IA

public enum HuaweiOltCommands {
    // Comandos básicos
    ENABLE("enable"),
    CONFIG("config"),
    DISPLAY("display"),
    PORT_DESC("port desc"),
    INTERFACE("interface"),
    QUIT("quit"),
    SAVE("save"),
    REBOOT("reboot"),
    UNDO("undo"),
    COMMIT("commit"),
    COMPARE_CONFIGURATION("compare configuration"),
    DESCRIPTION("description"),
    COPY("copy"),
    DELETE("delete"),
    DIR("dir"),
    PING("ping"),
    TRACERT("tracert"),
    SSH("ssh"),
    TELNET("telnet"),
    SCROLL("scroll"),
    LANGUAGE("language"),
    IDLE_TIMEOUT("idle-timeout"),
    SYSTEM("system"),
    SUPER("super"),

    // Comandos de patch
    PATCH_ACTIVATE("patch activate"),
    PATCH_LOAD("patch load"),
    PATCH_DELETE("patch delete"),

    // Comandos de backup
    BACKUP_CONFIGURATION("backup configuration"),
    BACKUP_DATA("backup data"),
    RESTORE_CONFIGURATION("restore configuration"),
    RESTORE_DATA("restore data"),

    // Comandos display - sistema
    DISPLAY_VERSION("display version"),
    DISPLAY_BOARD("display board"),
    DISPLAY_DEVICE("display device"),
    DISPLAY_TIME("display time"),
    DISPLAY_USERS("display users"),
    DISPLAY_HISTORY_COMMAND("display history-command"),
    DISPLAY_CURRENT_CONFIGURATION("display current-configuration"),
    DISPLAY_SAVED_CONFIGURATION("display saved-configuration"),
    DISPLAY_STARTUP("display startup"),
    DISPLAY_LOG_ALL("display log all"),
    DISPLAY_LOG_BUFFER("display log buffer"),
    DISPLAY_ALARM_ACTIVE("display alarm active"),
    DISPLAY_ALARM_HISTORY("display alarm history"),
    DISPLAY_CPU_USAGE("display cpu-usage"),
    DISPLAY_MEMORY_USAGE("display memory-usage"),
    DISPLAY_TEMPERATURE("display temperature"),
    DISPLAY_FAN("display fan"),
    DISPLAY_POWER("display power"),
    DISPLAY_ENVIRONMENT("display environment"),

    // Comandos display - rede
    DISPLAY_IP_INTERFACE_BRIEF("display ip interface brief"),
    DISPLAY_IP_ROUTING_TABLE("display ip routing-table"),
    DISPLAY_ARP("display arp"),
    DISPLAY_ARP_ALL("display arp all"),
    DISPLAY_MAC_ADDRESS("display mac-address"),
    DISPLAY_MAC_ADDRESS_DYNAMIC("display mac-address dynamic"),
    DISPLAY_MAC_ADDRESS_STATIC("display mac-address static"),

    // Comandos display - VLAN
    DISPLAY_VLAN("display vlan"),
    DISPLAY_VLAN_ALL("display vlan all"),
    DISPLAY_VLAN_SUMMARY("display vlan summary"),
    DISPLAY_VLAN_PORT("display vlan port"),

    // Comandos display - porta
    DISPLAY_PORT_STATE("display port state"),
    DISPLAY_PORT_STATISTICS("display port statistics"),
    DISPLAY_PORT_DESC_DISPLAY("display port desc"),

    // Comandos display - tráfego e QoS
    DISPLAY_TRAFFIC_TABLE_IP("display traffic table ip"),
    DISPLAY_TRAFFIC_TABLE_IP_ALL("display traffic table ip all"),
    DISPLAY_QOS_PROFILE("display qos profile"),
    DISPLAY_QOS_PROFILE_ALL("display qos profile all"),

    // Comandos display - ONT profiles
    DISPLAY_ONT_SRVPROFILE_GPON("display ont-srvprofile gpon"),
    DISPLAY_ONT_LINEPROFILE_GPON("display ont-lineprofile gpon"),
    DISPLAY_DBA_PROFILE("display dba-profile"),
    DISPLAY_DBA_PROFILE_ALL("display dba-profile all"),

    // Comandos display - ONT info
    DISPLAY_ONT_INFO_SUMMARY("display ont info summary"),
    DISPLAY_ONT_INFO_BY_SN("display ont info by-sn"),
    DISPLAY_ONT_INFO_BY_LOID("display ont info by-loid"),
    DISPLAY_ONT_INFO_BY_IP("display ont info by-ip"),
    DISPLAY_ONT_INFO_BY_MAC("display ont info by-mac"),
    DISPLAY_ONT_INFO("display ont info"),
    DISPLAY_ONT_VERSION("display ont version"),
    DISPLAY_ONT_CAPABILITY("display ont capability"),
    DISPLAY_ONT_STATUS("display ont status"),
    DISPLAY_ONT_WAN_INFO("display ont wan-info"),
    DISPLAY_ONT_IPTV_INFO("display ont iptv-info"),
    DISPLAY_ONT_VOIP_INFO("display ont voip-info"),
    DISPLAY_ONT_ALARM_INFO("display ont alarm-info"),
    DISPLAY_ONT_ALARM_PROFILE("display ont alarm-profile"),
    DISPLAY_ONT_AUTOFIND_ALL("display ont autofind all"),
    DISPLAY_ONT_OPTICAL_INFO("display ont optical-info"),
    DISPLAY_ONT_REGISTER_INFO("display ont register-info"),
    DISPLAY_ONT_TRAFFIC("display ont traffic"),
    DISPLAY_ONT_PORT_STATE("display ont port state"),
    DISPLAY_ONT_PORT_STATISTICS("display ont port statistics"),
    DISPLAY_ONT_VIDEO_SERVICE_INFO("display ont video-service-info"),

    // Comandos display - service port
    DISPLAY_SERVICE_PORT_ALL("display service-port all"),
    DISPLAY_SERVICE_PORT("display service-port"),
    DISPLAY_SERVICE_PORT_PORT("display service-port port"),
    DISPLAY_SERVICE_PORT_VLAN("display service-port vlan"),
    DISPLAY_SERVICE_PORT_INDEX("display service-port index"),
    DISPLAY_SERVICE_PORT_STATISTICS("display service-port statistics"),

    // Comandos display - outros
    DISPLAY_SNMP_AGENT_SYS_INFO("display snmp-agent sys-info"),
    DISPLAY_NTP_SERVICE_STATUS("display ntp-service status"),
    DISPLAY_SSH_SERVER_STATUS("display ssh server status"),
    DISPLAY_TELNET_SERVER_STATUS("display telnet server status"),
    DISPLAY_LOAD_BALANCING("display load balancing"),
    DISPLAY_PATCH_INFORMATION("display patch information"),
    DISPLAY_ELABEL("display elabel"),

    // Comandos de configuração
    INTERFACE_CONFIG("interface"),
    VLAN_CONFIG("vlan"),
    PORT_VLAN("port vlan"),
    TRAFFIC_TABLE_IP("traffic table ip"),
    UNDO_TRAFFIC_TABLE("undo traffic table"),
    QOS_PROFILE_CONFIG("qos profile"),
    UNDO_QOS_PROFILE("undo qos profile"),
    DBA_PROFILE_CONFIG("dba-profile"),
    UNDO_DBA_PROFILE("undo dba-profile"),
    ONT_SRVPROFILE_GPON("ont-srvprofile gpon"),
    UNDO_ONT_SRVPROFILE("undo ont-srvprofile"),
    ONT_LINEPROFILE_GPON("ont-lineprofile gpon"),
    UNDO_ONT_LINEPROFILE("undo ont-lineprofile"),
    ONT_ALARM_PROFILE("ont alarm-profile"),
    UNDO_ONT_ALARM_PROFILE("undo ont alarm-profile"),
    SNMP_AGENT("snmp-agent"),
    NTP_SERVICE("ntp-service"),
    SSH_SERVER("ssh server"),
    TELNET_SERVER("telnet server"),
    USER_INTERFACE_VTY("user-interface vty"),
    AAA("aaa"),
    TERMINAL_USER("terminal user"),
    SYSTEMNAME("systemname"),
    TIME_ZONE("time-zone"),
    SYSLOG_SERVER("syslog-server"),
    HEADER_LOGIN("header login"),
    MAC_ADDRESS_STATIC("mac-address static"),
    ARP_STATIC("arp static"),
    IP_ROUTE_STATIC("ip route-static"),
    SECURITY("security"),
    LOAD_BALANCING_CONFIG("load balancing"),

    // Comandos de interface
    DISPLAY_THIS("display this"),
    PORT("port"),
    DESCRIPTION_CONFIG("description"),
    SHUTDOWN("shutdown"),
    UNDO_SHUTDOWN("undo shutdown"),
    SPEED("speed"),
    DUPLEX("duplex"),
    PORT_VLAN_CONFIG("port vlan"),
    PORT_DEFAULT_VLAN("port default vlan"),
    PORT_HYBRID_VLAN("port hybrid vlan"),
    PORT_LINK_TYPE("port link-type"),
    MAC_LIMIT_MAXIMUM("mac-limit maximum"),
    BROADCAST_SUPPRESSION("broadcast-suppression"),
    MULTICAST_SUPPRESSION("multicast-suppression"),
    UNICAST_SUPPRESSION("unicast-suppression"),
    QOS_APPLY("qos apply"),
    TRUST("trust"),
    STP("stp"),
    LOOP_DETECT("loop-detect"),

    // Comandos ONT
    PORT_ONT_ADD("port <port_id> ont add"),
    PORT_ONT_DELETE("port <port_id> ont delete"),
    PORT_ONT_MODIFY("port <port_id> ont modify"),
    PORT_ONT_CONFIRM("port <port_id> ont confirm"),
    ONT_ADD("ont add"),
    ONT_DELETE("ont delete"),
    ONT_MODIFY("ont modify"),
    ONT_CONFIRM("ont confirm"),
    ONT_PORT_ATTRIBUTE("ont port attribute"),
    ONT_IPCONFIG("ont ipconfig"),
    ONT_WAN_CONFIG("ont wan-config"),
    ONT_INTERNET_CONFIG("ont internet-config"),
    ONT_VOICE_CONFIG("ont voice-config"),
    ONT_VIDEO_CONFIG("ont video-config"),
    ONT_MULTICAST_FORWARD("ont multicast-forward"),
    SERVICE_PORT_CONFIG("service-port"),
    UNDO_SERVICE_PORT("undo service-port"),
    DISPLAY_ONT_INFO_CONFIG("display ont info"),
    DISPLAY_ONT_OPTICAL_INFO_CONFIG("display ont optical-info"),
    DISPLAY_ONT_REGISTER_INFO_CONFIG("display ont register-info"),
    DISPLAY_ONT_TRAFFIC_CONFIG("display ont traffic"),
    ONT_AUTO_LEARN("ont auto-learn"),
    ONT_INTERCONNECTION_ENABLE("ont-interconnection enable"),
    LASER("laser"),
    OPTICAL_ALARM_PROFILE("optical-alarm-profile"),
    POWER_SAVING("power-saving"),

    // Comandos adicionais
    FLOW_CONTROL("flow-control"),
    JUMBOFRAME("jumboframe"),
    AUTO_NEG("auto-neg"),

    // Parâmetros e modificadores
    BY_SN("by-sn"),
    SUMMARY("summary"),
    ALL("all"),
    PORT_PARAM("port"),
    VLAN_PARAM("vlan"),
    ONT("ont"),
    IP("ip"),
    INDEX("index"),
    PROFILE("profile"),
    GPON("gpon"),
    ETHERNET("ethernet"),
    ETH("eth"),
    POTS("pots"),
    VEIP("veip"),
    GEMPORT("gemport"),
    SN_AUTH("sn-auth"),
    LOID_AUTH("loid-auth"),
    PASSWORD_AUTH("password-auth"),
    OMCI("omci"),
    UP_STREAM("up-stream"),
    DOWN_STREAM("down-stream"),
    CIR("cir"),
    PIR("pir"),
    CBS("cbs"),
    PBS("pbs"),
    PRIORITY("priority"),
    WEIGHT("weight"),
    QUEUE("queue"),
    PROFILE_ID("profile-id"),
    PROFILE_INDEX("profile-index"),
    PROFILE_NAME("profile-name"),
    SMART("smart"),
    STANDARD("standard"),
    MUX("mux"),
    QINQ("qinq"),
    STACKING("stacking"),
    ACCESS("access"),
    TRUNK("trunk"),
    HYBRID("hybrid"),
    TAGGED("tagged"),
    UNTAGGED("untagged"),
    ENABLE_PARAM("enable"),
    DISABLE("disable"),
    ON("on"),
    OFF("off"),
    ADD("add"),
    DELETE_PARAM("delete"),
    MODIFY("modify"),
    CONFIRM("confirm"),
    DHCP("dhcp"),
    STATIC("static"),
    PPPOE("pppoe"),
    ACTIVE("active"),
    HISTORY("history"),
    BRIEF("brief"),
    DYNAMIC("dynamic"),
    STATIC_PARAM("static"),
    CONFIGURATION("configuration"),
    STATE("state"),
    STATISTICS("statistics"),
    VERSION("version"),
    CAPABILITY("capability"),
    WAN_INFO("wan-info"),
    OPTICAL_INFO("optical-info"),
    REGISTER_INFO("register-info"),
    AUTOFIND("autofind");


    // A partir daqui ja nao eh mais IA


    private final String command;

    HuaweiOltCommands(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    public static List<String> getAllCommands() {
        return Arrays.stream(HuaweiOltCommands.values())
                .map(HuaweiOltCommands::getCommand)
                .collect(Collectors.toList());
    }

    public static Optional<HuaweiOltCommands> findByCommand(String command) {
        return Arrays.stream(HuaweiOltCommands.values())
                .filter(cmd -> cmd.getCommand().equals(command))
                .findFirst();
    }

    public static List<HuaweiOltCommands> findCommandsStartingWith(String prefix) {
        return Arrays.stream(HuaweiOltCommands.values())
                .filter(cmd -> cmd.getCommand().toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return command;
    }
}