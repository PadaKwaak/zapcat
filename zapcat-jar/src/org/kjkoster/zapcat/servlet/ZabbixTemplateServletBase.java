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
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.kjkoster.zapcat.util.XmlFormatter;
import org.kjkoster.zapcat.zabbix.JMXHelper;
import org.kjkoster.zapcat.zabbix.ZabbixAgent;

/**
 * A servlet that generates a Tomcat/JBoss Zabbix template. We generate the
 * template for Tomcat/JBoss because it is so configuraton-dependent. Zabbix
 * really is not able to deal with very dynamic systems.
 *
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
abstract class ZabbixTemplateServletBase extends HttpServlet {

    final Logger log;
    final String hostNamePrefix;

    public ZabbixTemplateServletBase(Logger log, String hostNamePrefix) {
        this.log = log;
        this.hostNamePrefix = hostNamePrefix;
    }

    enum Type {
        /**
         * Floating point data.
         */
        Float,
        /**
         * Character data, up to 255 bytes long.
         */
        Character,
        /**
         * Integer data, must be positive.
         */
        Integer;

        int getValue() {
            switch (this) {
                case Float:
                    return 0;
                case Character:
                    return 1;
                case Integer:
                    return 3;
            }

            throw new IllegalArgumentException("unknown value " + this);
        }
    }

    enum Time {
        /**
         * For configuration items, poll this item only once per hour.
         */
        OncePerHour,
        /**
         * For normal statistics, poll this item twice pr minute.
         */
        TwicePerMinute;

        int getValue() {
            switch (this) {
                case OncePerHour:
                    return 3600;
                case TwicePerMinute:
                    return 30;
            }

            throw new IllegalArgumentException("unknown value " + this);
        }
    }

    enum Store {
        /**
         * Store the value as-is.
         */
        AsIs,
        /**
         * Store the value as delta, interpreting the data on a per-second
         * basis.
         */
        AsDelta
    }

    abstract Set<ObjectName> getManagers(final MBeanServer mbeanserver) throws MalformedObjectNameException;

    abstract Set<ObjectName> getProcessors(final MBeanServer mbeanserver) throws MalformedObjectNameException;

    abstract ObjectName getProcessorObjectName(final String name) throws MalformedObjectNameException;

    abstract ZabbixTemplateServletBase newInstance();

    abstract void writeVersionItem(final PrintWriter out) throws MalformedObjectNameException;

    abstract void writeProcessorCompressionItem(final PrintWriter out, final String name) throws MalformedObjectNameException;

    abstract void writeProcessorCompressionTrigger(final PrintWriter out, final String name) throws MalformedObjectNameException;

    abstract Type getMaxActiveSessionsType();

    MBeanServer getMBeanServer() {
        return JMXHelper.getMBeanServer();
    }

    @SuppressWarnings(value = "unchecked")
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final PrintWriter out = response.getWriter();
        final MBeanServer mbeanserver = getMBeanServer();
        try {
            final Set<ObjectName> managers = getManagers(mbeanserver);
            final Set<ObjectName> processors = getProcessors(mbeanserver);
            ZabbixTemplateServletBase t = newInstance();
            response.setContentType("text/xml");
            t.writeHeader(out);
            t.writeItems(out, processors, managers);
            t.writeTriggers(out, processors);
            t.writeGraphs(out, processors, managers);
            t.writeFooter(out);
        } catch (Exception e) {
            log.error("unable to generate template", e);
            e.printStackTrace(out);
        } finally {
            out.flush();
        }
    }

    protected void writeHeader(final PrintWriter out) throws UnknownHostException {
        out.println("<?xml version=\"1.0\"?>");
        out.println("<zabbix_export version=\"1.0\" date=\"" + new SimpleDateFormat("dd.MM.yy").format(new Date()) + "\" time=\"" + new SimpleDateFormat("HH.mm").format(new Date()) + "\">");
        out.println("  <hosts>");
        out.println("    <host name=\"" + hostNamePrefix + InetAddress.getLocalHost().getHostName().replaceAll("[^a-zA-Z0-9]+", "_") + "\">");
        out.println("      <dns>" + InetAddress.getLocalHost().getHostName() + "</dns>");
        out.println("      <ip>" + InetAddress.getLocalHost().getHostAddress() + "</ip>");
        out.println("      <port>" + System.getProperty(ZabbixAgent.PORT_PROPERTY, "" + ZabbixAgent.DEFAULT_PORT) + "</port>");
        out.println("      <groups>");
        out.println("      </groups>");
    }

    protected void writeItems(final PrintWriter out, final Set<ObjectName> processors, final Set<ObjectName> managers) throws MalformedObjectNameException {
        out.println("      <items>");
        writeVersionItem(out);
        writeProcessorItems(out, processors);
        writeManagerItems(out, managers);
        out.println("      </items>");
    }

    protected void writeProcessorItems(final PrintWriter out, final Set<ObjectName> processors) throws MalformedObjectNameException {
        for (final ObjectName processor : processors) {
            final String name = name(processor);
            final ObjectName threadpool = getProcessorObjectName(name);
            writeItem(out, name + " bytes received per second", processor, "bytesReceived", Type.Float, "B", Store.AsDelta, Time.TwicePerMinute);
            writeItem(out, name + " bytes sent per second", processor, "bytesSent", Type.Float, "B", Store.AsDelta, Time.TwicePerMinute);
            writeItem(out, name + " requests per second", processor, "requestCount", Type.Float, null, Store.AsDelta, Time.TwicePerMinute);
            writeItem(out, name + " errors per second", processor, "errorCount", Type.Float, null, Store.AsDelta, Time.TwicePerMinute);
            writeItem(out, name + " processing time per second", processor, "processingTime", Type.Float, "s", Store.AsDelta, Time.TwicePerMinute);
            writeItem(out, name + " threads max", threadpool, "maxThreads", Type.Integer, null, Store.AsIs, Time.OncePerHour);
            writeItem(out, name + " threads allocated", threadpool, "currentThreadCount", Type.Integer, null, Store.AsIs, Time.TwicePerMinute);
            writeItem(out, name + " threads busy", threadpool, "currentThreadsBusy", Type.Integer, null, Store.AsIs, Time.TwicePerMinute);
            if (name.startsWith("http")) {
                writeProcessorCompressionItem(out, name);
            }
        }
    }

    protected void writeManagerItems(final PrintWriter out, final Set<ObjectName> managers) {
        for (final ObjectName manager : managers) {
            writeItem(out, "sessions " + path(manager) + " active", manager, "activeSessions", Type.Integer, null, Store.AsIs, Time.TwicePerMinute);
            writeItem(out, "sessions " + path(manager) + " peak", manager, "maxActiveSessions", getMaxActiveSessionsType(), null, Store.AsIs, Time.TwicePerMinute);
            writeItem(out, "sessions " + path(manager) + " rejected", manager, "rejectedSessions", Type.Integer, null, Store.AsIs, Time.TwicePerMinute);
        }
    }

    protected void writeItem(final PrintWriter out, final String description, final ObjectName objectname, final String attribute, final Type type, final String units, final Store store, final Time time) {
        out.println("        <item type=\"0\" key=\"jmx[" + XmlFormatter.escape(objectname) + "][" + XmlFormatter.escape(attribute) + "]\" value_type=\"" + type.getValue() + "\">");
        out.println("          <description>" + XmlFormatter.escape(description) + "</description>");
        out.println("          <delay>" + time.getValue() + "</delay>");
        out.println("          <history>90</history>");
        out.println("          <trends>365</trends>");
        if (units != null) {
            out.println("          <units>" + units + "</units>");
        }
        if (store == Store.AsDelta) {
            out.println("          <delta>1</delta>");
        }
        // we assume that all time is logged in milliseconds...
        if ("s".equals(units)) {
            out.println("          <multiplier>1</multiplier>");
            out.println("          <formula>0.001</formula>");
        } else {
            out.println("          <formula>1</formula>");
        }
        out.println("          <snmp_community>public</snmp_community>");
        out.println("          <snmp_oid>interfaces.ifTable.ifEntry.ifInOctets.1</snmp_oid>");
        out.println("          <snmp_port>161</snmp_port>");
        out.println("        </item>");
    }

    protected void writeTriggers(final PrintWriter out, final Set<ObjectName> processors) throws MalformedObjectNameException {
        out.println("      <triggers>");
        writeProcessorTriggers(out, processors);
        out.println("      </triggers>");
    }

    protected void writeProcessorTriggers(final PrintWriter out, final Set<ObjectName> processors) throws MalformedObjectNameException {
        for (final ObjectName processor : processors) {
            final String name = name(processor);
            final ObjectName threadpool = getProcessorObjectName(name);
            if (name.startsWith("http")) {
                writeProcessorCompressionTrigger(out, name);
            }
            writeTrigger(out, "70% " + name + " worker threads busy on {HOSTNAME}", "{{HOSTNAME}:jmx[" + threadpool + "][currentThreadsBusy].last(0)}>({{HOSTNAME}:jmx[" + threadpool + "][maxThreads].last(0)}*0.7)", 4);
        }
    }

    protected void writeTrigger(final PrintWriter out, final String description, final String expression, final int priority) {
        out.println("        <trigger>");
        out.println("          <description>" + XmlFormatter.escape(description) + "</description>");
        out.println("          <expression>" + XmlFormatter.escape(expression) + "</expression>");
        out.println("          <priority>" + priority + "</priority>");
        out.println("        </trigger>");
    }

    protected void writeGraphs(final PrintWriter out, final Set<ObjectName> processors, final Set<ObjectName> managers) throws MalformedObjectNameException {
        out.println("      <graphs>");
        writeProcessorGraphs(out, processors);
        writeManagerGraphs(out, managers);
        out.println("      </graphs>");
    }

    protected void writeProcessorGraphs(final PrintWriter out, final Set<ObjectName> processors) throws MalformedObjectNameException {
        for (final ObjectName processor : processors) {
            final String name = name(processor);
            final ObjectName threadpool = getProcessorObjectName(name);
            writeGraph(out, name + " worker threads", threadpool, "maxThreads", "currentThreadsBusy", "currentThreadCount");
        }
    }

    protected void writeManagerGraphs(final PrintWriter out, final Set<ObjectName> managers) {
        for (final ObjectName manager : managers) {
            writeGraph(out, "sessions " + path(manager), manager, "rejectedSessions", "activeSessions", "maxActiveSessions");
        }
    }

    protected void writeGraph(final PrintWriter out, final String name, final ObjectName objectname, final String redAttribute, final String greenAttribute, final String blueAttribute) {
        out.println("        <graph name=\"" + XmlFormatter.escape(name) + "\" width=\"900\" height=\"200\">");
        out.println("          <show_work_period>1</show_work_period>");
        out.println("          <show_triggers>1</show_triggers>");
        out.println("          <yaxismin>0.0000</yaxismin>");
        out.println("          <yaxismax>100.0000</yaxismax>");
        out.println("          <graph_elements>");
        out.println("            <graph_element item=\"{HOSTNAME}:jmx[" + XmlFormatter.escape(objectname) + "][" + redAttribute + "]\">");
        out.println("              <color>990000</color>");
        out.println("              <yaxisside>1</yaxisside>");
        out.println("              <calc_fnc>2</calc_fnc>");
        out.println("              <periods_cnt>5</periods_cnt>");
        out.println("            </graph_element>");
        out.println("            <graph_element item=\"{HOSTNAME}:jmx[" + XmlFormatter.escape(objectname) + "][" + greenAttribute + "]\">");
        out.println("              <color>009900</color>");
        out.println("              <yaxisside>1</yaxisside>");
        out.println("              <calc_fnc>2</calc_fnc>");
        out.println("              <periods_cnt>5</periods_cnt>");
        out.println("            </graph_element>");
        out.println("            <graph_element item=\"{HOSTNAME}:jmx[" + XmlFormatter.escape(objectname) + "][" + blueAttribute + "]\">");
        out.println("              <color>000099</color>");
        out.println("              <yaxisside>1</yaxisside>");
        out.println("              <calc_fnc>2</calc_fnc>");
        out.println("              <periods_cnt>5</periods_cnt>");
        out.println("            </graph_element>");
        out.println("          </graph_elements>");
        out.println("        </graph>");
    }

    protected String path(final ObjectName objectname) {
        final String name = objectname.toString();
        final int start;
        int pos = getPos(name, "path=");
        if (pos != -1) {
            start = pos;
        } else {
            pos = getPos(name, "context=");
            if (pos != -1) {
                start = pos;
            } else {
                start = 0;
            }
        }
        final int end = name.indexOf(',', start + 1);
        return name.substring(start, end == -1 ? name.length() : end);
    }

    protected int getPos(final String value, final String searchString) {
        final int pos = value.indexOf(searchString);
        return pos == -1 ? -1 : (pos + searchString.length());
    }

    protected String name(final ObjectName objectname) {
        final String name = objectname.toString();
        final int start = name.indexOf("name=") + 5;
        return name.substring(start);
    }

    protected void writeFooter(final PrintWriter out) {
        out.println("    </host>");
        out.println("  </hosts>");
        out.println("</zabbix_export>");
        out.println();
    }

}
