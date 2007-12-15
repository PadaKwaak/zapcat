package org.kjkoster.zapcat.test;

/* This file is part of Zapcat.
 *
 * Zapcat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Zapcat is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Zapcat.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

import junit.framework.TestCase;

import org.kjkoster.zapcat.Trapper;
import org.kjkoster.zapcat.zabbix.ZabbixTrapper;

/**
 * Test cases to try the trapper configuration.
 * 
 * @author Kees Jan Koster &lt;kjkoster@kjkoster.org&gt;
 */
public class ZabbixTrapperConfigurationTest extends TestCase {
    private int read = -1;

    private final byte[] buffer = new byte[1024];

    private final Properties originalProperties;

    /**
     * Set up the test, preserving the system's configuration.
     */
    public ZabbixTrapperConfigurationTest() {
        super();

        originalProperties = System.getProperties();
    }

    /**
     * @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        System.setProperties(originalProperties);
        assertNull(System.getProperty(ZabbixTrapper.SERVER_PROPERTY));
        assertNull(System.getProperty(ZabbixTrapper.PORT_PROPERTY));
        assertNull(System.getProperty(ZabbixTrapper.HOST_PROPERTY));
    }

    /**
     * Test starting and stopping a simple trapper.
     * 
     * @throws Exception
     *             When the test failed.
     */
    public void testStartAndStop() throws Exception {
        final Thread server = startServer(buffer, ZabbixTrapper.DEFAULT_PORT);

        trapSomeData();

        server.interrupt();
        server.join();
    }

    /**
     * Test starting and stopping a trapper on a non-standard server config.
     * 
     * @throws Exception
     *             When the test failed.
     */
    public void testSomeOtherPort() throws Exception {
        final int TEST_PORT = ZabbixTrapper.DEFAULT_PORT + 1;
        final Thread server = startServer(buffer, TEST_PORT);

        final Properties testProperties = (Properties) originalProperties
                .clone();
        testProperties.setProperty(ZabbixTrapper.PORT_PROPERTY, "" + TEST_PORT);
        System.setProperties(testProperties);
        assertEquals("" + TEST_PORT, System
                .getProperty(ZabbixTrapper.PORT_PROPERTY));

        trapSomeData();

        server.interrupt();
        server.join();
    }

    private void trapSomeData() throws Exception {
        final Trapper trapper = new ZabbixTrapper("localhost", "foo");
        trapper.send("bar", "baz");

        trapper.stop();

        // we compare byte-for-byte to avoid unicode issues...
        for (int i = 0; i < read; i++) {
            assertEquals(
                    (byte) "<req><host>Zm9v</host><key>YmFy</key><data>YmF6</data></req>                                                       "
                            .charAt(i), buffer[i]);
        }
    }

    private Thread startServer(final byte[] buffer, final int port) {
        final Thread server = new Thread(new Runnable() {
            public void run() {

                ServerSocket serverSocket = null;
                Socket accepted = null;
                try {
                    serverSocket = new ServerSocket(port);
                    accepted = serverSocket.accept();
                    read = accepted.getInputStream().read(buffer);
                } catch (Exception e) {
                    e.printStackTrace();
                    fail();
                } finally {
                    try {
                        accepted.close();
                        serverSocket.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        fail();
                    }
                }
            }
        });
        server.start();
        return server;
    }
}
