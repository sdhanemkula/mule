/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.launcher;

import org.mule.api.MuleRuntimeException;
import org.mule.config.i18n.MessageFactory;
import org.mule.module.launcher.descriptor.ApplicationDescriptor;
import org.mule.module.launcher.descriptor.DescriptorParser;
import org.mule.module.launcher.descriptor.EmptyApplicationDescriptor;
import org.mule.module.launcher.descriptor.Preferred;
import org.mule.module.launcher.descriptor.PropertiesDescriptorParser;
import org.mule.module.reboot.MuleContainerBootstrapUtils;
import org.mule.util.FileUtils;
import org.mule.util.FilenameUtils;
import org.mule.util.PropertiesUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.imageio.spi.ServiceRegistry;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.io.filefilter.WildcardFileFilter;

/**
 *
 */
public class DefaultAppBloodhound implements AppBloodhound
{

    // file extension -> parser implementation
    protected Map<String, DescriptorParser> parserRegistry = new HashMap<String, DescriptorParser>();

    public DefaultAppBloodhound()
    {
        // defaults first
        parserRegistry.put("properties", new PropertiesDescriptorParser());

        final Iterator<DescriptorParser> it = ServiceRegistry.lookupProviders(DescriptorParser.class);

        MultiMap overrides = new MultiValueMap();
        while (it.hasNext())
        {
            final DescriptorParser parser = it.next();
            overrides.put(parser.getSupportedFormat(), parser);
        }
        mergeParserOverrides(overrides);
    }

    public ApplicationDescriptor fetch(String appName) throws IOException
    {
        final File appsDir = MuleContainerBootstrapUtils.getMuleAppsDir();
        File appDir = new File(appsDir, appName);
        if (!appDir.exists())
        {
            throw new MuleRuntimeException(
                    MessageFactory.createStaticMessage(
                            String.format("Application directory does not exist: '%s'", appDir)));
        }
        // list mule-deploy.* files
        @SuppressWarnings("unchecked")
        Collection<File> deployFiles = FileUtils.listFiles(appDir, new WildcardFileFilter("mule-deploy.*"), null);
        if (deployFiles.size() > 1)
        {
            // TODO need some kind of an InvalidAppFormatException
            throw new MuleRuntimeException(
                    MessageFactory.createStaticMessage(
                            String.format("More than one mule-deploy descriptors found in application '%s'", appName)));
        }

        ApplicationDescriptor desc;

        // none found, return defaults
        if (deployFiles.isEmpty())
        {
            desc = new EmptyApplicationDescriptor(appName);
        }
        else
        {
            // lookup the implementation by extension
            final File descriptorFile = deployFiles.iterator().next();
            final String ext = FilenameUtils.getExtension(descriptorFile.getName());
            final DescriptorParser descriptorParser = parserRegistry.get(ext);

            if (descriptorParser == null)
            {
                // TODO need some kind of an InvalidAppFormatException
                throw new MuleRuntimeException(
                        MessageFactory.createStaticMessage(
                                String.format("Unsupported deployment descriptor format for app '%s': %s", appName, ext)));
            }

            desc = descriptorParser.parse(descriptorFile);
            // app name is external to the deployment descriptor
            desc.setAppName(appName);
        }

        // get a ref to an optional app props file (right next to the descriptor)
        final File appPropsFile = new File(appDir, ApplicationDescriptor.DEFAULT_APP_PROPERTIES_RESOURCE);
        if (appPropsFile.exists() && appPropsFile.canRead())
        {
            final Properties props = PropertiesUtils.loadProperties(appPropsFile.toURI().toURL());
            // ugh, no straightforward way to convert to a map
            Map<String, String> m = new HashMap<String, String>(props.size());
            for (Object key : props.keySet())
            {
                m.put(key.toString(), props.getProperty(key.toString()));
            }
            desc.setAppProperties(m);
        }

        return desc;

    }

    /**
     * Merge default and discovered overrides for descriptor parsers, taking weight into account
     *
     * @param overrides discovered parser overrides
     */
    protected void mergeParserOverrides(MultiMap overrides)
    {
        // for each key in default parser registry
        for (Map.Entry<String, DescriptorParser> entry : parserRegistry.entrySet())
        {
            @SuppressWarnings("unchecked")
            final Collection<DescriptorParser> candidates = (Collection<DescriptorParser>) overrides.get(entry.getKey());

            if (candidates == null)
            {
                continue;
            }
            // if any override candidates found, sort by weight reverse
            final ArrayList<DescriptorParser> sorted = new ArrayList<DescriptorParser>(candidates);
            final Comparator<DescriptorParser> annotationComparator = new Comparator<DescriptorParser>()
            {
                public int compare(DescriptorParser p1, DescriptorParser p2)
                {
                    final Preferred ann1 = p1.getClass().getAnnotation(Preferred.class);
                    final Preferred ann2 = p2.getClass().getAnnotation(Preferred.class);

                    if (ann1 == null && ann2 == null)
                    {
                        return 0;
                    }

                    if (ann1 != null && ann2 == null)
                    {
                        return 1;
                    }

                    if (ann1 == null)
                    {
                        return -1;
                    }

                    // else compare annotation weights
                    return new Integer(ann1.weight()).compareTo(ann2.weight());
                }
            };
            Collections.sort(sorted, Collections.reverseOrder(annotationComparator));

            // put the top one in the registry
            parserRegistry.put(entry.getKey(), sorted.get(0));
        }

    }
}