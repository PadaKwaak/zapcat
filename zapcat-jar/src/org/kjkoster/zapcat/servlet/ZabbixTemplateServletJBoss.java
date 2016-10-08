package org.kjkoster.zapcat.servlet;

// TODO: Convert this from javax.management mbean server to org.jboss mbean stuff ;)

/* This file is part of Zapcat.
 *
 * Zapcat is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * Zapcat is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * Zapcat. If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.PrintWriter;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.log4j.Logger;

/**
 * A servlet that generates the Tomcat Zabbix template. We generate the template
 * for Tomcat because it is so configuraton-dependent. Zabbix really is not able
 * to deal with very dynamic systems.
 *
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public class ZabbixTemplateServletJBoss extends ZabbixTemplateServletBase {

    private static final long serialVersionUID = 1245376184346210185L;

    private static final Logger LOGGER = Logger.getLogger(ZabbixTemplateServletJBoss.class);
    private static final String HOST_NAME_PREFIX = "jboss_";

    public ZabbixTemplateServletJBoss() {
        super(LOGGER, HOST_NAME_PREFIX);
    }

    @Override
    Set<ObjectName> getManagers(MBeanServer mbeanserver) throws MalformedObjectNameException {
        return mbeanserver.queryNames(new ObjectName("jboss.web:type=Manager,*"), null);
    }

    @Override
    Set<ObjectName> getProcessors(MBeanServer mbeanserver) throws MalformedObjectNameException {
        return mbeanserver.queryNames(new ObjectName("jboss.web:type=GlobalRequestProcessor,*"), null);
    }

    @Override
    ObjectName getProcessorObjectName(String name) throws MalformedObjectNameException {
        return new ObjectName("jboss.web:type=ThreadPool,name=" + name);
    }

    @Override
    ZabbixTemplateServletBase newInstance() {
        return new ZabbixTemplateServletJBoss();
    }

    @Override
    void writeVersionItem(PrintWriter out) throws MalformedObjectNameException {
        writeItem(out, "JBoss version",
                new ObjectName("jboss.system:type=Server"), "VersionNumber",
                Type.Character, null, Store.AsIs, Time.OncePerHour);
    }

    @Override
    void writeProcessorCompressionItem(PrintWriter out, String name) throws MalformedObjectNameException {
        final String port = port(name);
        final String address = address(name);
        log.debug("Writing: " + "jboss.web:type=ProtocolHandler,port=" + port + ",address=" + address);
        writeItem(out, name + " gzip compression", new ObjectName(
                "jboss.web:type=ProtocolHandler,port=" + port + ",address=" + address),
                "compression", Type.Character, null, Store.AsIs,
                Time.OncePerHour);
    }

    @Override
    void writeProcessorCompressionTrigger(PrintWriter out, String name) throws MalformedObjectNameException {
        final String port = port(name);
        final String address = address(name);
        writeTrigger(out, "gzip compression is off for connector "
                + name + " on {HOSTNAME}",
                "{{HOSTNAME}:jmx[jboss.web:type=ProtocolHandler,port="
                + port + ",address=" + address + "][compression].str(off)}=1", 2);
    }

    @Override
    Type getMaxActiveSessionsType() {
        return Type.Float;
    }

    private String address(final String name) {
        final String addressPort = name.substring(name.indexOf('-') + 1);
        return "%2F" + addressPort.substring(0, addressPort.indexOf('-'));
    }

    private String port(final String name) {
        final String addressPort = name.substring(name.indexOf('-') + 1);
        return addressPort.substring(addressPort.indexOf('-') + 1);
    }
}
