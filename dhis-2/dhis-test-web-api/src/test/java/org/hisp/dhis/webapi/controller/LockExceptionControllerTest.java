/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors 
 * may be used to endorse or promote products derived from this software without
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
package org.hisp.dhis.webapi.controller;

import static java.lang.String.format;
import static org.hisp.dhis.http.HttpAssertions.assertStatus;
import static org.hisp.dhis.test.webapi.Assertions.assertWebMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.hisp.dhis.dataset.LockException;
import org.hisp.dhis.http.HttpStatus;
import org.hisp.dhis.jsontree.JsonArray;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.schema.Property;
import org.hisp.dhis.schema.Schema;
import org.hisp.dhis.schema.SchemaService;
import org.hisp.dhis.test.webapi.PostgresControllerIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests the {@link LockExceptionController} using (mocked) REST requests.
 *
 * @author Jan Bernitt
 */
@Transactional
class LockExceptionControllerTest extends PostgresControllerIntegrationTestBase {

  @Autowired private SchemaService schemaService;

  private String ouId;

  private String dsId;

  @BeforeEach
  void setUp() {
    dsId =
        assertStatus(
            HttpStatus.CREATED,
            POST(
                "/dataSets/", "{'name':'My data set', 'shortName':'MDS', 'periodType':'Monthly'}"));
    ouId =
        assertStatus(
            HttpStatus.CREATED,
            POST(
                "/organisationUnits/",
                "{'name':'My Unit', 'shortName':'OU1', 'openingDate': '2020-01-01'}"));
    assertStatus(HttpStatus.OK, POST("/organisationUnits/{ou}/dataSets/{ds}", ouId, dsId));
  }

  @Test
  void testAddLockException() {
    assertWebMessage(
        "Created",
        201,
        "OK",
        "LockException created successfully.",
        POST("/lockExceptions/?ou={ou}&pe=2021-01&ds={ds}", ouId, dsId)
            .content(HttpStatus.CREATED));
  }

  @Test
  void testAddLockException_UnauthorizedUser() {
    switchToNewUser("guest", "F_DATASET_PUBLIC_ADD");

    assertWebMessage(
        "Forbidden",
        403,
        "ERROR",
        "You can only add a lock exceptions to your data capture organisation units.",
        POST("/lockExceptions/?ou={ou}&pe=2021-01&ds={ds}", ouId, dsId)
            .content(HttpStatus.FORBIDDEN));
  }

  @Test
  void testAddLockException_DataSetNotLinked() {
    String dsId2 =
        assertStatus(
            HttpStatus.CREATED,
            POST(
                "/dataSets/",
                "{'name':'My data set2', 'shortName':'MDS2', 'periodType':'Monthly'}"));
    assertWebMessage(
        "Conflict",
        409,
        "ERROR",
        format(
            "None of the target organisation unit(s) %s is linked to the specified data set: %s",
            ouId, dsId2),
        POST("/lockExceptions/?ou={ou}&pe=2021-01&ds={ds}", ouId, dsId2)
            .content(HttpStatus.CONFLICT));
  }

  @Test
  void testAddLockException_NoOrgUnit() {
    assertWebMessage(
        "Conflict",
        409,
        "ERROR",
        "OrganisationUnit ID is invalid.",
        POST("/lockExceptions/?ou=&pe=2021-01&ds=" + dsId).content(HttpStatus.CONFLICT));
  }

  @Test
  void testAddLockException_IllegalOrgUnit() {
    assertWebMessage(
        "Conflict",
        409,
        "ERROR",
        "Can't find OrganisationUnit with id =xyz",
        POST("/lockExceptions/?ou=xyz&pe=2021-01&ds=" + dsId).content(HttpStatus.CONFLICT));
  }

  @Test
  void testGetLockExceptionsServesTheStoredLockException() {
    addLockException("2021-01");

    JsonArray lockExceptions =
        GET("/lockExceptions").content(HttpStatus.OK).getArray("lockExceptions");

    assertEquals(1, lockExceptions.size());
    JsonObject lockException = lockExceptions.getObject(0);
    assertEquals(
        Set.of("id", "name", "created", "period", "organisationUnit", "dataSet"),
        Set.copyOf(lockException.names()));
    assertEquals("My data set (My Unit, 202101)", lockException.getString("name").string());
    assertEquals("Monthly", lockException.getString("period.periodType").string());
    assertEquals("2021-01-01T00:00:00.000", lockException.getString("period.startDate").string());
    assertEquals(ouId, lockException.getString("organisationUnit.id").string());
    assertEquals(dsId, lockException.getString("dataSet.id").string());
    assertTrue(lockException.getString("created").exists());
  }

  @Test
  void testGetLockExceptionByKey() {
    addLockException("2021-01");
    String key =
        GET("/lockExceptions")
            .content(HttpStatus.OK)
            .getArray("lockExceptions")
            .getObject(0)
            .getString("id")
            .string();

    JsonArray lockExceptions =
        GET("/lockExceptions?key={key}", key).content(HttpStatus.OK).getArray("lockExceptions");

    assertEquals(1, lockExceptions.size());
    assertEquals(key, lockExceptions.getObject(0).getString("id").string());
  }

  @Test
  void testGetLockExceptionCombinationsGroupsByDataSetAndPeriod() {
    addLockException("2021-01");
    addLockException("2021-02");
    // the combinations are read with plain JDBC
    dbmsManager.flushSession();

    JsonArray combinations =
        GET("/lockExceptions/combinations").content(HttpStatus.OK).getArray("lockExceptions");

    assertEquals(2, combinations.size());
    assertEquals("My data set (202101)", combinations.getObject(0).getString("name").string());
    assertEquals("My data set (202102)", combinations.getObject(1).getString("name").string());
    assertEquals(dsId, combinations.getObject(0).getString("dataSet.id").string());
    assertFalse(combinations.getObject(0).getString("organisationUnit").exists());
  }

  @Test
  void testDeleteLockExceptionOfOrganisationUnitKeepsOtherPeriods() {
    addLockException("2021-01");
    addLockException("2021-02");

    assertStatus(
        HttpStatus.NO_CONTENT, DELETE("/lockExceptions?ou={ou}&pe=2021-01&ds={ds}", ouId, dsId));
    clearSession();

    JsonArray lockExceptions =
        GET("/lockExceptions").content(HttpStatus.OK).getArray("lockExceptions");
    assertEquals(1, lockExceptions.size());
    assertEquals(
        "My data set (My Unit, 202102)", lockExceptions.getObject(0).getString("name").string());
  }

  @Test
  void testDeleteLockExceptionCombinationRemovesAllOrganisationUnits() {
    addLockException("2021-01");

    assertStatus(HttpStatus.NO_CONTENT, DELETE("/lockExceptions?pe=2021-01&ds=" + dsId));
    clearSession();

    assertTrue(GET("/lockExceptions").content(HttpStatus.OK).getArray("lockExceptions").isEmpty());
  }

  /** LockException has no SchemaDescriptor; its dynamic schema is built from the mapping. */
  @Test
  void testDynamicSchemaProperties() {
    Schema schema = schemaService.getSchema(LockException.class);

    assertEquals(
        Set.of("id", "name", "created", "period", "organisationUnit", "dataSet"),
        schema.getPropertyMap().keySet());
    assertFalse(schema.getProperty("name").isPersisted());
    for (String name : Set.of("period", "organisationUnit", "dataSet")) {
      Property property = schema.getProperty(name);
      assertTrue(property.isPersisted(), name);
      assertTrue(property.isManyToOne(), name);
      assertTrue(property.isOwner(), name);
    }
    assertTrue(schema.getProperty("created").isPersisted());
    assertTrue(schema.getProperty("created").isRequired());
  }

  private void addLockException(String period) {
    assertStatus(
        HttpStatus.CREATED, POST("/lockExceptions/?ou={ou}&pe={pe}&ds={ds}", ouId, period, dsId));
  }

  private void clearSession() {
    dbmsManager.flushSession();
    dbmsManager.clearSession();
  }
}
