package org.kjkoster.zapcat.util;

import javax.management.ObjectName;

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

/**
 * A simple XML escape utility class.
 * 
 * This class is just intended for basic XML escaping and nothing complex
 * such as multi-byte characters.
 * 
 * @author Chris Kistner &lt;chris.kistner@gmail.com&gt;
 *
 */
public class XmlFormatter {

    public static String escape(final ObjectName objectname) {
        return escape(objectname == null ? null : objectname.toString());
    }

    public static String escape(final String value) {
        if (value == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '<':
                    sb.append("&lt;"); break;
                case '>':
                    sb.append("&gt;"); break;
                case '\"':
                    sb.append("&quot;"); break;
                case '&':
                    sb.append("&amp;"); break;
                case '\'':
                    sb.append("&apos;"); break;
                default:
                    if (c > 0x7e) {
                        sb.append("&#").append((int) c).append(";");
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
 
}
