package org.hisp.dhis.attribute;

/*
 * Copyright (c) 2004-2018, University of Oslo
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

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.hisp.dhis.DhisSpringTest;
import org.hisp.dhis.attribute.exception.NonUniqueAttributeValueException;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.common.ValueType;
import org.hisp.dhis.dataelement.DataElement;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Morten Olav Hansen <mortenoh@gmail.com>
 */
public class AttributeValueServiceTest
        extends DhisSpringTest
{
    @Autowired
    private AttributeService attributeService;

    @Autowired
    private IdentifiableObjectManager manager;

    private AttributeValue avA;
    private AttributeValue avB;
    private DataElement dataElementA;
    private DataElement dataElementB;
    private Attribute attribute1;
    private Attribute attribute2;

    @Override
    protected void setUpTest() throws NonUniqueAttributeValueException
    {
        avA = new AttributeValue( "value 1" );
        avB = new AttributeValue( "value 2" );

        attribute1 = new Attribute( "attribute 1", ValueType.TEXT );
        attribute1.setDataElementAttribute( true );
        attribute2 = new Attribute( "attribute 2", ValueType.TEXT );
        attribute2.setDataElementAttribute( true );

        attributeService.addAttribute( attribute1 );
        attributeService.addAttribute( attribute2 );

        avA.setAttribute( attribute1 );
        avB.setAttribute( attribute2 );

        dataElementA = createDataElement( 'A' );
        dataElementB = createDataElement( 'B' );

        attributeService.addAttributeValue( dataElementA, avA );
        attributeService.addAttributeValue( dataElementB, avB );

        dataElementA = createDataElement( 'A' );
        manager.save( dataElementA );

    }

    @Test
    public void testAddAttributeValue()
    {
        AttributeValue avA = new AttributeValue( "valueA", attribute1 );
        avA.setAutoFields();
        AttributeValue avB = new AttributeValue( "valueB", attribute2 );
        avB.setAutoFields();

        attributeService.updateAttributeValue( dataElementA, avA );
        attributeService.updateAttributeValue( dataElementB, avB );

        DataElement deA = manager.get( DataElement.class, dataElementA.getUid() );
        DataElement deB = manager.get( DataElement.class, dataElementB.getUid() );

        assertEquals( deA.getJsonAttributeValues().size(), 1 );
        assertNotNull( deA.getAttributeValue( attribute1 ) );
        assertEquals( deB.getJsonAttributeValues().size(), 1 );
        assertNotNull( deB.getAttributeValue( attribute2 ) );
    }

    @Test
    public void testUpdateAttributeValue() throws NonUniqueAttributeValueException
    {
        avA.setValue( "updated value 1" );
        avB.setValue( "updated value 2" );

        attributeService.updateAttributeValue( dataElementA, avA );
        attributeService.updateAttributeValue( dataElementB, avB );

        avA = dataElementA.getAttributeValue( attribute1 );
        avB = dataElementB.getAttributeValue( attribute2 );

        assertNotNull( avA );
        assertNotNull( avB );

        assertEquals( "updated value 1", avA.getValue() );
        assertEquals( "updated value 2", avB.getValue() );
    }

//    @Test
    public void testDeleteAttributeValue()
    {
//        long attributeValueId1 = avA.getId();
//        long attributeValueId2 = avB.getId();

//        attributeService.deleteAttributeValue( avA );
//        attributeService.deleteAttributeValue( avB );

//        avA = attributeService.getAttributeValue( attributeValueId1 );
//        avB = attributeService.getAttributeValue( attributeValueId2 );

//        assertNull( avA );
//        assertNull( avB );
    }

    @Test
    public void testGetAttributeValue()
    {
//        avA = attributeService.getAttributeValue( avA.getId() );
//        avB = attributeService.getAttributeValue( avB.getId() );

        assertNotNull( avA );
        assertNotNull( avB );
    }

    @Test
    public void testAddNonUniqueAttributeValue() throws NonUniqueAttributeValueException
    {
        Attribute attribute = new Attribute( "ID", ValueType.TEXT );
        attribute.setUnique( true );
        attribute.setDataElementAttribute( true );

        attributeService.addAttribute( attribute );

        AttributeValue attributeValueA = new AttributeValue( "A", attribute );
        attributeService.addAttributeValue( dataElementA, attributeValueA );
        manager.update( dataElementA );

        AttributeValue attributeValueB = new AttributeValue( "B", attribute );
        attributeService.addAttributeValue( dataElementB, attributeValueB );
        manager.update( dataElementB );
    }

    @Test( expected = NonUniqueAttributeValueException.class )
    public void testAddUniqueAttributeValue() throws NonUniqueAttributeValueException
    {
        Attribute attribute = new Attribute( "ID", ValueType.TEXT );
        attribute.setUnique( true );
        attribute.setDataElementAttribute( true );

        attributeService.addAttribute( attribute );

        AttributeValue attributeValueA = new AttributeValue( "A", attribute );
        attributeService.addAttributeValue( dataElementA, attributeValueA );
        manager.update( dataElementA );

        AttributeValue attributeValueB = new AttributeValue( "A", attribute );
        attributeService.addAttributeValue( dataElementB, attributeValueB );
        manager.update( dataElementB );
    }

    @Test( expected = NonUniqueAttributeValueException.class )
    public void testUpdateNonUniqueAttributeValue() throws NonUniqueAttributeValueException
    {
        Attribute attribute = new Attribute( "ID", ValueType.TEXT );
        attribute.setUnique( true );
        attribute.setDataElementAttribute( true );

        attributeService.addAttribute( attribute );

        AttributeValue attributeValueA = new AttributeValue( "A", attribute );
        attributeService.addAttributeValue( dataElementA, attributeValueA );
        manager.update( dataElementA );

        AttributeValue attributeValueB = new AttributeValue( "B", attribute );
        attributeService.addAttributeValue( dataElementB, attributeValueB );
        manager.update( dataElementB );

        attributeValueB.setValue( "A" );
        attributeService.updateAttributeValue( dataElementB, attributeValueB );
        manager.update( dataElementB );
    }

    @Test
    public void testGetJsonAttributeValues() throws Exception
    {

        Attribute attribute1 = new Attribute( "attribute1", ValueType.TEXT );
        attribute1.setDataElementAttribute( true );
        attributeService.addAttribute( attribute1 );

        AttributeValue av = new AttributeValue( "value1", attribute1 );
        attributeService.addAttributeValue( dataElementA, av );
        manager.update( dataElementA );

        ObjectMapper mapper = new ObjectMapper();

        ObjectNode node = mapper.createObjectNode();
        node.put( "id",attribute1.getId() );
        node.put( "value", "updatedvalue1" );

        List<String> jsonValues  = new ArrayList<>();
        jsonValues.add( node.toString() );

        attributeService.updateAttributeValues( dataElementA, jsonValues );

//        av = attributeService.getAttributeValue( av.getId() );
        assertEquals( "updatedvalue1", av.getValue() );

    }
}
