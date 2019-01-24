/**
 * This file is part of the JCROM project.
 * Copyright (C) 2008-2019 - All rights reserved.
 * Authors: Olafur Gauti Gudmundsson, Nicolas Dos Santos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jcrom.util;

public class PathUtils {

	public PathUtils() { }

    /**
     * Creates a valid JCR node name from the String supplied, by
     * replacing all non-alphanumeric chars.
     * 
     * @param str the input String
     * @return a valid JCR node name for the String
     */
    public static String createValidName(String str) {
        return replaceNonAlphanumeric(str, '_');
    }
    
    public static boolean isRootPath(String path) {
    	return "/".equals(path);
    }
    
    public static String relativePath(String absolutePath) {
        if (absolutePath.charAt(0) == '/') {
            return absolutePath.substring(1);
        } else {
            return absolutePath;
        }
    }
    
    /**
     * Replaces occurences of non-alphanumeric characters with a
     * supplied char. A non-alphanumeric character at the beginning or end
     * is replaced with ''.
     */
    public static String replaceNonAlphanumeric(String str, char subst) {
        StringBuilder sb = new StringBuilder(str.length());
        char[] testChars = str.toCharArray();
        char lastChar = 'A';
        for (int i = 0; i < testChars.length; i++) {
            if (Character.isLetterOrDigit(testChars[i]) || testChars[i] == '.' || testChars[i] == ':') {
                sb.append(testChars[i]);
                lastChar = testChars[i];
            } else if (i > 0 && (i + 1) != testChars.length && lastChar != subst) {
                sb.append(subst);
                lastChar = subst;
            }
        }
        return sb.toString();
    }
	
}
