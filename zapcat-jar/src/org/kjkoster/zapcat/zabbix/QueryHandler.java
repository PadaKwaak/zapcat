package org.kjkoster.zapcat.zabbix;

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

import org.apache.log4j.Logger;
import org.kjkoster.zapcat.Trapper;

import javax.management.*;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A JMX query handler for Zabbix. The query handler reads the query from the
 * socket, parses the request and constructs and sends a response.
 * <p>
 * You can configure the protocol version to use and set it to either
 * &quot;1.1&quot; or &quot;1.4&quot;.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
final class QueryHandler implements Runnable {
    private static final Logger log = Logger.getLogger(QueryHandler.class);
    private static final String VERSION = "zapcat 1.4";

    private final Socket socket;

    private final StringBuilder hexdump = new StringBuilder();

    /**
     * The return value that Zabbix interprets as the agent not supporting the
     * item.
     */
    private static final String NOTSUPPORTED = "ZBX_NOTSUPPORTED";

    //zabbix key must have this pattern, jmx["name","attribute", <ignored>]
    private static final Pattern COMMA_PATTERN = Pattern.compile("([0-9a-zA-Z.-_]+)\\[\"([^\"\\]]+)\",\"([^\"\\]]+)\".*\\]");
    private MBeanServer mBeanServer;

    /**
     * Create a new query handler.
     *
     * @param mBeanServer
     * @param socket
     */
    public QueryHandler(MBeanServer mBeanServer, final Socket socket) {
        this.mBeanServer = mBeanServer;
        this.socket = socket;
    }

    /**
     * @see java.lang.Runnable#run()
     */
    public void run() {
        try {
            log.debug("started worker");
            try {
                do {
                    handleQuery();
                } while (socket.getInputStream().available() > 0);
            }catch(Exception ex){
                log.error("error handling equery", ex);
            } finally {
                if (socket != null) {
                    socket.close();
                }
            }
            log.debug("worker is done");
        } catch (Exception e) {
            log.error("dropping exception", e);
        }
    }

    private void handleQuery() throws IOException {
        String request = receive(socket.getInputStream());
        log.debug("received '" + request + "'");

        String response = response(request);
        // make sure we can send
        if (response == null) {
            response = "";
        }

        log.debug("sending '" + response + "'");
        send(response, socket.getOutputStream());
    }

    private String receive(final InputStream in) throws IOException {
        String line = "";
        int b = in.read();
        while (b != -1 && b != 0x0a) {
            line += (char) b;

            b = in.read();
        }
        
        // This adds support for zabbix_get to communicate with the agent.
        // As posted to the sourceforge project page by Jim Riggs (jhriggs)
        if (isProtocol14() && line.startsWith("ZBXD\1"))
        	line = line.substring(13);

        return line;
    }

    private String response(final String query) {
        //new query style compatible with zabbix 1.8.4 and above
        {
            Matcher matcher = COMMA_PATTERN.matcher(query);
            boolean didFindMatch = matcher.find();
            if (didFindMatch) {
                String op = matcher.group(1);
                String objectName = matcher.group(2);
                String attribute = matcher.group(3);
                log.debug("op = " + op + ", " + "objectName = " + objectName + ", " + "attribute = " + attribute);
                try {
                    return JMXHelper.query(mBeanServer, new ObjectName(objectName), attribute);
                } catch (Exception ex) {
                    log.debug("jmx error", ex);
                    return NOTSUPPORTED;
                }
            }
        }



        final int lastOpen = query.lastIndexOf('[');
        final int lastClose = query.lastIndexOf(']');
        String attribute = null;
        if (lastOpen >= 0 && lastClose >= 0) {
            attribute = query.substring(lastOpen + 1, lastClose);
        }

        /*
        *  This allows testing of trapper functionality from within this framework.
        *  Set key to trap[zabbixServer][host][key][value] and the agent will create
        *  a trapper that pushes to the zabbix server. (e.g. use with zabbix_get -k"trap...")
        *
        *  This needs some work. The agent should be able to receive a trap command
        *  to schedule a trapper client, or work in a similar method to the way zabbix
        *  agent active checks work.
        *
        *  This command should not be used at present. Stick to jmx / jmx_op.
        */
        if (query.startsWith("trap")) {
        	try {
        		StringTokenizer trapperParms = new StringTokenizer(query.substring(query.indexOf('[')), "[]", false);
        		String zabbixServer = trapperParms.nextToken();
        		String host = trapperParms.nextToken();
        		String key = trapperParms.nextToken();
        		String value = attribute;
        		if (sendTrap(zabbixServer, host, key, value)) {
        			log.debug("Success: " + key + "='" + value + "' for host " + host + " sent to the Zabbix Trapper on " + zabbixServer);
        			return key + "='" + value + "' for host " + host + " sent to the Zabbix Trapper on " + zabbixServer;
        		}
        		log.debug("Fail: " + key + "='" + value + "' for host " + host + " sent to the Zabbix Trapper on " + zabbixServer);
        		return NOTSUPPORTED;
        	} catch (Exception e) {
        		log.debug("Could not send trap from query " + query);
        		return NOTSUPPORTED;
        	}

        } else if (query.startsWith("jmx_op")) {
        	String query_string = query;
        	int index = query_string.indexOf(']');

        	if (index < 0)
        		return NOTSUPPORTED;

        	String objectName = query_string.substring(7, index);
        	query_string = query_string.substring(index+2);
        	index = query_string.indexOf(']');
        	if (index < 0)
        		return NOTSUPPORTED;

        	String op_name = query_string.substring(0, index);
        	query_string = query_string.substring(index);

        	try {
        		return JMXHelper.op_query(objectName, op_name, query_string);
        	} catch (InstanceNotFoundException e) {
        		log.debug("no bean named " + objectName, e);
        		return NOTSUPPORTED;
        	} catch (UnsupportedOperationException e) {
        		log.debug("operation named " + op_name + " is not supported on bean named " + objectName, e);
        		return NOTSUPPORTED;
        	} catch (IllegalArgumentException e) {
        		log.debug("parameters passed is illegal for operation named " + op_name + " on bean named " + objectName, e);
        		return NOTSUPPORTED;
        	} catch (Exception e) {
        		log.debug("exception with jmx_op", e);
        	}

        } else if (query.startsWith("jmx")) {
            final int firstClose = query.lastIndexOf(']', lastOpen);
            final int firstOpen = query.indexOf('[');
            if (firstClose == -1 || firstOpen == -1 || attribute == null) {
                return NOTSUPPORTED;
            }

            final String objectName = query
                    .substring(firstOpen + 1, firstClose);
            try {
                return JMXHelper.query(mBeanServer, new ObjectName(objectName), attribute);
            } catch (InstanceNotFoundException e) {
                log.debug("no bean named " + objectName, e);
                return NOTSUPPORTED;
            } catch (MalformedObjectNameException e) {
                log.debug("no bean named " + objectName, e);
                return NOTSUPPORTED;
            } catch (AttributeNotFoundException e) {
                log.debug("no attribute named " + attribute + " on bean named "
                        + objectName, e);
                return NOTSUPPORTED;
            } catch (MBeanException e) {
                log.warn("unable to find either " + objectName + " or "
                        + attribute, e);
                return NOTSUPPORTED;
            } catch (ReflectionException e) {
                log.warn("unable to find either " + objectName + " or "
                        + attribute, e);
                return NOTSUPPORTED;
            }
        } else if (query.startsWith("system.property")) {
            return querySystemProperty(attribute);
        } else if (query.startsWith("system.env")) {
            return queryEnvironment(attribute);
        } else if (query.equals("agent.ping")) {
            return "1";
        } else if (query.equals("agent.version")) {
            return VERSION;
        }

        return NOTSUPPORTED;
    }

    /*
     * This method will go away once I have added collection support to the
     * query handler.
     */
    private String querySystemProperty(final String key) {
        log.debug("System property[" + key + "]");
        return System.getProperty(key);
    }

    private String queryEnvironment(final String key) {
        log.debug("Environment[" + key + "]");
        return System.getenv(key);
    }

    private void send(final String response, final OutputStream outputStream)
            throws IOException {
        final BufferedOutputStream out = new BufferedOutputStream(outputStream);

        if (isProtocol14()) {
            // write magic marker
            write(out, (byte) 'Z');
            write(out, (byte) 'B');
            write(out, (byte) 'X');
            write(out, (byte) 'D');

            // write protocol version
            write(out, (byte) 0x01);

            // length as 64 bit integer, little endian format
            long length = response.length();
            for (int i = 0; i < 8; i++) {
                write(out, (byte) (length & 0xff));

                length >>= 8;
            }
        }

        // response itself
        for (int i = 0; i < response.length(); i++) {
            write(out, (byte) response.charAt(i));
        }

        out.flush();
        log.debug("sent bytes " + hexdump);
    }
    
    private boolean sendTrap(String zabbixServer, String host, String key, String value) {
		Trapper trapper = null;
		try {
			trapper = new ZabbixTrapper(zabbixServer, host);
			trapper.send(key, value);
		} catch (Exception e) {
			log.debug(e.toString());
			return false;
		} finally {
			trapper.stop();
		}
		
		return true;
		
	}

    private boolean isProtocol14() {
        final String protocolProperty = System
                .getProperty(ZabbixAgent.PROTOCOL_PROPERTY);
        if (protocolProperty == null || "1.4".equals(protocolProperty)) {
            return true;
        }
        if ("1.1".equals(protocolProperty)) {
            return false;
        }

        log.warn("Unsupported protocol '" + protocolProperty + "', using 1.4");
        return true;
    }

    private void write(final BufferedOutputStream out, final byte b)
            throws IOException {
        final String hex = Integer.toHexString(b);
        if (hex.length() < 2) {
            hexdump.append("0");
        }
        hexdump.append(hex).append(" ");

        out.write(b);
    }
}
