/*
 * $Header$
 * $Revision$
 * $Date$
 * ------------------------------------------------------------------------------------------------------
 *
 * Copyright (c) SymphonySoft Limited. All rights reserved.
 * http://www.symphonysoft.com
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 */
package org.mule.transformers.simple;

import org.mule.transformers.AbstractTransformer;
import org.mule.umo.transformer.TransformerException;
import org.mule.util.StringMessageUtils;

/**
 * <code>StringToByteArray</code> converts a string into a byte array
 * 
 * @author <a href="mailto:ross.mason@symphonysoft.com">Ross Mason</a>
 * @version $Revision$
 */

public class StringToByteArray extends AbstractTransformer
{
    public StringToByteArray()
    {
        registerSourceType(String.class);
        registerSourceType(byte[].class);
        setReturnClass(byte[].class);
    }

    public Object doTransform(Object src, String encoding) throws TransformerException
    {
        if (src instanceof byte[]) {
            return src;
        }
        return StringMessageUtils.getBytes((String) src);
    }
}
