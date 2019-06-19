package org.hisp.dhis.hibernate.jsonb.type;

/*
 * Copyright (c) 2004-2017, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.hibernate.HibernateException;
import org.hisp.dhis.attribute.AttributeValue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonAttributeValueBinaryType
    extends JsonBinaryType
{
    static final ObjectMapper MAPPER = new ObjectMapper();

    public JsonAttributeValueBinaryType()
    {
        super();
        writer = MAPPER.writerFor( new TypeReference<Map<String, AttributeValue>>() {} ).withoutAttribute( "attribute" );;
        reader = MAPPER.readerFor( new TypeReference<Map<String, AttributeValue>>() {} ).withoutAttribute( "attribute" );;
        returnedClass = AttributeValue.class;
    }

    static
    {
        MAPPER.setSerializationInclusion( JsonInclude.Include.NON_NULL );
        MAPPER.disable( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES );
    }

    @Override
    protected void init( Class<?> klass )
    {
        returnedClass = klass;
        reader = MAPPER.readerFor( new TypeReference<Map<String, AttributeValue>>() {} );
        writer = MAPPER.writerFor( new TypeReference<Map<String, AttributeValue>>() {} );
    }

    @Override
    protected String convertObjectToJson( Object object )
    {
        try
        {
            Set<AttributeValue> attributeValues = object == null ? Collections.emptySet()
                : (Set<AttributeValue>) object;

            Map<String, AttributeValue> attrValueMap = new HashMap<>();

            for ( AttributeValue attributeValue : attributeValues )
            {
                attrValueMap.put( attributeValue.getAttributeUid(), attributeValue );
            }

            return writer.writeValueAsString( attrValueMap );

        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    @Override
    public Object deepCopy( Object value ) throws HibernateException
    {
        String json = convertObjectToJson( value );
        return convertJsonToObject( json );
    }

    @Override
    protected Object convertJsonToObject( String content )
    {
        try
        {
            Map<String, AttributeValue> data = reader.readValue( content );

            return convertAttributeValueMapIntoSet( data );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    private static Set<AttributeValue> convertAttributeValueMapIntoSet( Map<String, AttributeValue> data )
    {
        Set<AttributeValue> attributeValues = new HashSet<>();

        for ( Map.Entry<String, AttributeValue> entry : data.entrySet() )
        {
            AttributeValue attributeValue = entry.getValue();
            attributeValue.setAttributeUid( entry.getKey() );
            attributeValues.add( attributeValue );
        }

        return attributeValues;
    }
}
