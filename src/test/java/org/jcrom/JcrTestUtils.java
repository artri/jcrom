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
package org.jcrom;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.Value;

public class JcrTestUtils {

	private JcrTestUtils() {}

	public static String printNode(Node node, String indentation) throws Exception {
    	return printNode(new StringBuilder(), node, indentation);
    }
	
    private static String printNode(StringBuilder sb, Node node, String indentation) throws Exception {
    	sb.append("\n");
    	sb.append(indentation).append("------- NODE -------");
    	sb.append(indentation).append("Path: ").append(node.getPath());
    	sb.append(indentation).append("------- Properties: ");
        PropertyIterator propertyIterator = node.getProperties();
        while (propertyIterator.hasNext()) {
            Property p = propertyIterator.nextProperty();
            if (!p.getName().equals("jcr:data") && !p.getName().equals("jcr:mixinTypes") && !p.getName().equals("fileBytes")) {
            	sb.append(indentation).append(p.getName()).append(": ");
                if (p.getDefinition().getRequiredType() == PropertyType.BINARY) {
                	sb.append("binary, (length: ").append(p.getLength()).append(") ");
                } else if (!p.getDefinition().isMultiple()) {
                    sb.append(p.getString());
                } else {
                    for (Value v : p.getValues()) {
                        sb.append(v.getString()).append(", ");
                    }
                }
                sb.append("\n");
            }

            if (p.getName().equals("jcr:childVersionHistory")) {
            	sb.append(indentation).append("------- CHILD VERSION HISTORY -------");
                printNode(sb, node.getSession().getNodeByIdentifier(p.getString()), indentation + "\t");
                sb.append(indentation).append("------- CHILD VERSION HISTORY ENDS -------");
            }
        }

        NodeIterator nodeIterator = node.getNodes();
        while (nodeIterator.hasNext()) {
            printNode(sb, nodeIterator.nextNode(), indentation + "\t");
        }
        
        return sb.toString();
    }	
}
