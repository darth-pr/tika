/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.detect;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;


/**
 * A detector that works on a Zip document
 *  to figure out exactly what the file is
 */
public class ZipContainerDetector implements Detector {
    public MediaType detect(InputStream input, Metadata metadata)
             throws IOException {
	if(input instanceof TikaInputStream) {
	    return detect((TikaInputStream)input, metadata);
	}
	return detect( TikaInputStream.get(input), metadata );
    }
    public MediaType detect(TikaInputStream input, Metadata metadata)
             throws IOException {
        ZipInputStream zip = new ZipInputStream(input);
        ZipEntry entry = zip.getNextEntry();
        while (entry != null) {
            // Is it an Open Document file?
            if (entry.getName().equals("mimetype")) {
                String type = IOUtils.toString(zip, "UTF-8");
                return fromString(type);
            } else if (entry.getName().equals("_rels/.rels") || 
        	    entry.getName().equals("[Content_Types].xml")) {
                // Office Open XML File
        	// As POI to open and investigate it for us
        	try {
        	    input.reset();
        	    
        	    OPCPackage pkg = OPCPackage.open(input);
        	    input.setOpenContainer(pkg);
        	    
                    PackageRelationshipCollection core = 
                         pkg.getRelationshipsByType(ExtractorFactory.CORE_DOCUMENT_REL);
                    if(core.size() != 1) {
                       throw new IOException("Invalid OOXML Package received - expected 1 core document, found " + core.size());
                    }

                    // Get the type of the core document part
                    PackagePart corePart = pkg.getPart(core.getRelationship(0));
                    String coreType = corePart.getContentType();
                    
                    // Turn that into the type of the overall document
                    String docType = coreType.substring(0, coreType.lastIndexOf('.'));
                    return fromString(docType);
                } catch(InvalidFormatException e) {
                    throw new IOException("Office Open XML File detected, but corrupted", e);
                }
            }
            entry = zip.getNextEntry();
        }
        
        return MediaType.APPLICATION_ZIP;
    }
    
    private static MediaType fromString(String type) {
        int splitAt = type.indexOf('/');
        if(splitAt > -1) {
            return new MediaType(
        	    type.substring(0,splitAt), 
        	    type.substring(splitAt+1)
            );
        }
        return MediaType.APPLICATION_ZIP;
    }
}

