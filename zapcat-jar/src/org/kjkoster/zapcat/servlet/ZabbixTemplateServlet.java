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

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;

import org.apache.log4j.Logger;

/**
 * A servlet that generates the Tomcat Zabbix template. We generate the template
 * for Tomcat because it is so configuraton-dependent. Zabbix really is not able
 * to deal with very dynamic systems.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 * 
 * @deprecated by Brett Cave - replaced by
 * ZabbixTemplateServletTomcat which also uses the new getMBeanServer method
 * 
 * @see #ZabbixTemplateServletTomcat()
 * 
 */
@Deprecated
public class ZabbixTemplateServlet extends ZabbixTemplateServletTomcat {
    static final long serialVersionUID = 1245376184346210185L;
    private static final Logger LOGGER = Logger.getLogger(ZabbixTemplateServlet.class);

    public ZabbixTemplateServlet() {
        super(LOGGER);
    }

    @Override
    ZabbixTemplateServletBase newInstance() {
        return new ZabbixTemplateServlet();
    }

    @Override
    MBeanServer getMBeanServer() {
        return ManagementFactory.getPlatformMBeanServer();
    }
}
