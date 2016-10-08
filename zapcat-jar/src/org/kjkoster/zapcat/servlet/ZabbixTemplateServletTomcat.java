package org.kjkoster.zapcat.servlet;

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
public class ZabbixTemplateServletTomcat extends ZabbixTemplateServletBase {

    private static final long serialVersionUID = 6675506730016914413L;

    private static final Logger LOGGER = Logger.getLogger(ZabbixTemplateServletTomcat.class);
    private static final String HOST_NAME_PREFIX = "tomcat_";

    public ZabbixTemplateServletTomcat() {
        super(LOGGER, HOST_NAME_PREFIX);
    }

    ZabbixTemplateServletTomcat(Logger log) {
        super(log, HOST_NAME_PREFIX);
    }

    @Override
    Set<ObjectName> getManagers(final MBeanServer mbeanserver) throws MalformedObjectNameException {
        return mbeanserver.queryNames(new ObjectName("Catalina:type=Manager,*"), null);
    }

    @Override
    Set<ObjectName> getProcessors(final MBeanServer mbeanserver) throws MalformedObjectNameException {
        return mbeanserver.queryNames(new ObjectName("Catalina:type=GlobalRequestProcessor,*"), null);
    }

    @Override
    ZabbixTemplateServletBase newInstance() {
        return new ZabbixTemplateServletTomcat();
    }

    @Override
    void writeVersionItem(PrintWriter out) throws MalformedObjectNameException {
        writeItem(out, "tomcat version",
                new ObjectName("Catalina:type=Server"), "serverInfo",
                Type.Character, null, Store.AsIs, Time.OncePerHour);
    }

    @Override
    void writeProcessorCompressionItem(PrintWriter out, String name) throws MalformedObjectNameException {
        final String port = port(name);
        writeItem(out, name + " gzip compression",
                new ObjectName("Catalina:type=ProtocolHandler,port=" + port),
                "compression", Type.Character, null, Store.AsIs, Time.OncePerHour);
    }

    @Override
    Type getMaxActiveSessionsType() {
        return Type.Integer;
    }

    @Override
    ObjectName getProcessorObjectName(String name) throws MalformedObjectNameException {
        return new ObjectName("Catalina:type=ThreadPool,name=" + name);
    }

    @Override
    void writeProcessorCompressionTrigger(PrintWriter out, String name) throws MalformedObjectNameException {
        final String port = port(name);
        writeTrigger(out, "gzip compression is off for connector "
                + name + " on {HOSTNAME}",
                "{{HOSTNAME}:jmx[Catalina:type=ProtocolHandler,port="
                + port + "][compression].str(off)}=1", 2);
    }

    private String port(final String name) {
        return name.substring(name.indexOf('-') + 1);
    }

}
