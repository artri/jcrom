/*
 * This file is part of the Weedow jcrom (R) project.
 * Copyright (c) 2010-2014 Weedow Software Corp.
 * Authors: Nicolas Dos Santos
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY Weedow, 
 * Weedow DISCLAIMS THE WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA, or download the license from the following URL:
 * http://www.weedow.com/terms-of-use/
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving this program without disclosing
 * the source code of your own applications.
 * These activities include: offering paid services to customers as an ASP, 
 * serving this program in a web application, shipping this program with a closed
 * source product.
 *
 * For more information, please contact Weedow Software Corp. at this
 * address: nicolas.dossantos@gmail.com
 */
package org.jcrom.converter;

import java.awt.Color;


/**
 * 
 * @author Nicolas Dos Santos
 */
public class ColorConverter implements Converter<Color, String> {

    private static final String SEPARATOR = ":";

    /**
     * Convert Color object to a String with format red:green:blue:alpha
     */
    @Override
    public String convertToJcrProperty(Color color) {
        StringBuilder sb = new StringBuilder();
        sb.append(color.getRed());
        sb.append(SEPARATOR);
        sb.append(color.getGreen());
        sb.append(SEPARATOR);
        sb.append(color.getBlue());
        sb.append(SEPARATOR);
        sb.append(color.getAlpha());
        return sb.toString();
    }

    /**
     * Convert a String with format red:green:blue:alpha to a Color object
     */
    @Override
    public Color convertToEntityAttribute(String colorString) {
        String[] rgb = colorString.split(SEPARATOR);
        return new Color(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]), Integer.parseInt(rgb[3]));
    }

}