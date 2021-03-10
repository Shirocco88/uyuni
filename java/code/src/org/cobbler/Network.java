/**
 * Copyright (c) 2009--2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.cobbler;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * Network
 */
public class Network {
    private final String name;
    private String netmask;
    private String ipAddress;
    private String ipv6Address;
    private String dnsname;
    private ArrayList<String> ipv6Secondaries;
    private boolean isStatic;
    private String macAddress;
    private String netmaskVariableName;
    private String bondingMasterVariableName;
    private String bondingTypeVariableName;
    private String bondingMaster;
    private String bondingOptions;
    private String bonding;
    private static String bondingMASTER;
    private static String bondingSLAVE;
    private static final String BONDING_NA = "na";
    /**
     * Constructor to create a new network interface
     * @param nameIn the name of the network
     * @param connection CobblerConnection object
     */
    public Network(CobblerConnection connection, String nameIn) {
        name = nameIn;

        // several variable names changed in cobbler 2.2
        if (connection.getVersion() >= 2.2) {
            netmaskVariableName = "netmask";
            bondingMasterVariableName = "interfacemaster";
            bondingTypeVariableName = "interfacetype";
            bondingMASTER = "bond";
            bondingSLAVE = "bond_slave";
        }
        else {
            netmaskVariableName = "subnet";
            bondingMasterVariableName = "bondingmaster";
            bondingTypeVariableName = "bonding";
            bondingMASTER = "master";
            bondingSLAVE = "slave";
        }
    }

    /**
     * Intentionally given default/package scope
     * returns a nicely formatted map that can be used by
     * the system record to set it in xmlrpc.
     * @return a map representation of the interface
     */
    Map<String, Object> toMap() {
        Map<String, Object> inet = new HashMap<String, Object>();
        addToMap(inet, "macaddress-" + name, macAddress);
        addToMap(inet, netmaskVariableName + "-" + name, netmask);
        addToMap(inet, "ipaddress-" + name, ipAddress);
        addToMap(inet, "static-" + name, isStatic);
        addToMap(inet, "ipv6address-" + name, ipv6Address);
        addToMap(inet, "ipv6secondaries-" + name, ipv6Secondaries);
        addToMap(inet, "dnsname-" + name, dnsname);
        addToMap(inet, bondingTypeVariableName + "-" + name, bonding);
        addToMap(inet, bondingMasterVariableName + "-" + name, bondingMaster);
        addToMap(inet, "bondingopts-" + name, bondingOptions);
        return inet;
    }

    private void addToMap(Map<String, Object> inet, String key, Object value) {
        // do not put null values and empty strings
        if (value != null && (!(value instanceof String) ||
                !StringUtils.isBlank((String)value))) {
            inet.put(key, value);
        }
    }

    /**
     * Given a interface name and map generated by the system record
     * this method creates a new Network object.
     * @param name the name of the interface
     * @param ifaceInfo the interface information
     * @return the network object
     */
    static Network load(CobblerConnection connection, String name,
            Map<String, Object> ifaceInfo) {
        Network net = new Network(connection, name);
        net.setMacAddress((String)ifaceInfo.get("mac_address"));
        net.setIpAddress((String)ifaceInfo.get("ip_address"));
        net.setStaticNetwork(ifaceInfo.containsKey("static") &&
                Boolean.TRUE.equals(ifaceInfo.get("static")));

        if (connection.getVersion() >= 2.2) {
            net.setNetmask((String)ifaceInfo.get("netmask"));
            net.setBondingMaster((String) ifaceInfo.get("interface_master"));
            net.setBonding((String) ifaceInfo.get("interface_type"));
        }
        else {
            net.setNetmask((String)ifaceInfo.get("subnet"));
            net.setBondingMaster((String) ifaceInfo.get("bonding_master"));
            net.setBonding((String) ifaceInfo.get("bonding"));
        }

        net.setIpv6Address((String) ifaceInfo.get("ipv6_address"));
        net.setIpv6Secondaries((ArrayList<String>) ifaceInfo.get("ipv6_secondaries"));
        net.setDnsname((String) ifaceInfo.get("dnsname"));
        net.setBondingOptions((String) ifaceInfo.get("bonding_opts"));

        return net;
    }

    /**
     * @return Returns the name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return Returns the netmask.
     */
    public String getNetmask() {
        return netmask;
    }

    /**
     * @param netmaskIn The netmask to set.
     */
    public void setNetmask(String netmaskIn) {
        netmask = netmaskIn;
    }

    /**
     * @return Returns the ipAddress.
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * @param ipAddressIn The ipAddress to set.
     */
    public void setIpAddress(String ipAddressIn) {
        ipAddress = ipAddressIn;
    }

    /**
     * @return Returns the IPv6 address of the interface.
     */
    public String getIpv6Address() {
        return ipv6Address;
    }

    /**
     * @param addressIn The IPv6 address to set.
     */
    public void setIpv6Address(String addressIn) {
        this.ipv6Address = addressIn;
    }

    /**
     * @return Returns the dnsname
     */
    public String getDnsname() {
        return dnsname;
    }

    /**
     * @param dnsnameIn The dnsname set.
     */
    public void setDnsname(String dnsnameIn) {
        this.dnsname = dnsnameIn;
    }

    /**
     * @return Returns secondary IPv6 addresses of the interface.
     */
    public ArrayList<String> getIpv6Secondaries() {
        return ipv6Secondaries;
    }

    /**
     * @param secondariesIn List of secondary IPv6 addresses to set.
     */
    public void setIpv6Secondaries(ArrayList<String> secondariesIn) {
        this.ipv6Secondaries = secondariesIn;
    }

    /**
     * @return Returns the isStatic.
     */
    public boolean isStaticNetwork() {
        return isStatic;
    }

    /**
     * @param staticIn The isStatic to set.
     */
    public void setStaticNetwork(boolean staticIn) {
        isStatic = staticIn;
    }

    /**
     * @return Returns the macAddress.
     */
    public String getMacAddress() {
        return macAddress;
    }

    /**
     * @param macAddressIn The macAddress to set.
     */
    public void setMacAddress(String macAddressIn) {
        macAddress = macAddressIn;
    }

    /**
     * @return Returns the bonding master.
     */
    public String getBondingMaster() {
        return bondingMaster;
    }

    /**
     * @param bondingMasterIn the bondingMaster to set.
     */
    public void setBondingMaster(String bondingMasterIn) {
        bondingMaster = bondingMasterIn;
    }

    /**
     * @return Returns the bonding options.
     */
    public String getBondingOptions() {
        return bondingOptions;
    }

    /**
     * @param bondingOptionsIn the bondingOptions to set.
     */
    public void setBondingOptions(String bondingOptionsIn) {
        bondingOptions = bondingOptionsIn;
    }

    /**
     * Set the Network as a bonding master.
     */
    public void makeBondingMaster() {
        bonding = bondingMASTER;
    }

    /**
     * Set the Network as a bonding slave.
     */
    public void makeBondingSlave() {
        bonding = bondingSLAVE;
    }

    /**
     * Set the Network as not applicable to bonding.
     */
    public void makeBondingNA() {
        bonding = BONDING_NA;
    }

    /**
     * @return Returns the bonding status [master, slave, na]
     */
    public String getBonding() {
        return bonding;
    }

    private void setBonding(String bondingIn) {
        bonding = bondingIn;
    }
}
