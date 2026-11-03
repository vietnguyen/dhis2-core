/*
 * Copyright (c) 2004-2026, University of Oslo
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

import static org.hisp.dhis.http.HttpAssertions.assertStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hisp.dhis.http.HttpStatus;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.schema.Property;
import org.hisp.dhis.schema.PropertyType;
import org.hisp.dhis.schema.Schema;
import org.hisp.dhis.schema.SchemaService;
import org.hisp.dhis.test.webapi.PostgresControllerIntegrationTestBase;
import org.hisp.dhis.trackedentity.TrackedEntityAttributeDimension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests the metadata import, merge and Jackson path of the attribute dimensions ({@link
 * TrackedEntityAttributeDimension}) embedded in event visualizations.
 */
@Transactional
class EventVisualizationAttributeDimensionsControllerTest
    extends PostgresControllerIntegrationTestBase {

  private static final String EVENT_VISUALIZATION_UID = "EvAtDimVis1";

  private static final String PROGRAM_UID = "EvAtDimPrg1";

  private static final String PROGRAM_STAGE_UID = "EvAtDimStg1";

  private static final String ATTRIBUTE_UID = "EvAtDimTea1";

  private static final String LEGEND_SET_A_UID = "EvAtDimLsA1";

  private static final String LEGEND_SET_B_UID = "EvAtDimLsB1";

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private EntityManagerFactory entityManagerFactory;

  @Autowired private SchemaService schemaService;

  @BeforeEach
  void setUp() {
    importMetadata(
        """
        {'trackedEntityAttributes':[{'id':'%s','name':'EvAtDim attribute',
           'shortName':'EvAtDim TEA','valueType':'INTEGER','aggregationType':'SUM'}],
         'legendSets':[{'id':'%s','name':'EvAtDim legend set A'},
           {'id':'%s','name':'EvAtDim legend set B'}],
         'programs':[{'id':'%s','name':'EvAtDim program','shortName':'EvAtDim program',
           'programType':'WITHOUT_REGISTRATION'}],
         'programStages':[{'id':'%s','name':'EvAtDim stage','program':{'id':'%s'}}]}
        """
            .formatted(
                ATTRIBUTE_UID,
                LEGEND_SET_A_UID,
                LEGEND_SET_B_UID,
                PROGRAM_UID,
                PROGRAM_STAGE_UID,
                PROGRAM_UID));
  }

  /**
   * Imports a dimension through /api/metadata, then exports as clients do (the column with its
   * legend set and filter), changes the legend set and filter and PUTs the export back: the
   * controller rebuilds the attribute dimension from the column.
   */
  @Test
  void testPutOfExportedEventVisualizationUpdatesDimension() throws Exception {
    importMetadata(eventVisualization(LEGEND_SET_A_UID, "GT:5"));
    assertEquals(List.of(dimension(LEGEND_SET_A_UID, "GT:5")), dimensions());
    startNewRequestSession();
    ObjectNode exported =
        (ObjectNode)
            JSON.readTree(
                GET("/eventVisualizations/"
                        + EVENT_VISUALIZATION_UID
                        + "?fields=*,columns[dimension,filter,legendSet[id],items[id]]")
                    .content(HttpStatus.OK)
                    .toJson());
    ObjectNode column = (ObjectNode) exported.withArray("columns").get(0);
    assertEquals(ATTRIBUTE_UID, column.get("dimension").asText());
    column.put("filter", "LT:10");
    column.putObject("legendSet").put("id", LEGEND_SET_B_UID);

    assertStatus(
        HttpStatus.OK,
        PUT("/eventVisualizations/" + EVENT_VISUALIZATION_UID, JSON.writeValueAsString(exported)));

    assertEquals(List.of(dimension(LEGEND_SET_B_UID, "LT:10")), dimensions());
  }

  /**
   * The properties of the dynamic schema of the attribute dimension (it has no schema descriptor)
   * and their mapping flags.
   */
  @Test
  void testTrackedEntityAttributeDimensionSchema() {
    Schema schema = schemaService.getSchema(TrackedEntityAttributeDimension.class);
    assertEquals("attributeDimension", schema.getName());

    Map<String, Property> properties = schema.getPropertyMap();
    assertEquals(Set.of("attribute", "legendSet", "filter"), properties.keySet());

    for (String reference : List.of("attribute", "legendSet")) {
      Property property = properties.get(reference);
      assertTrue(property.isPersisted(), reference);
      assertTrue(property.isManyToOne(), reference);
      assertTrue(property.isOwner(), reference);
      assertFalse(property.isRequired(), reference);
    }

    Property filter = properties.get("filter");
    assertTrue(filter.isPersisted());
    assertEquals(PropertyType.TEXT, filter.getPropertyType());
    assertEquals(Integer.MAX_VALUE, filter.getLength().intValue());
    assertFalse(filter.isRequired());
  }

  /**
   * A line list of the program stage with the attribute as a column. The column is what the event
   * visualization controller rebuilds the attribute dimensions from on PUT.
   */
  private static String eventVisualization(String legendSetUid, String filter) {
    return """
        {'eventVisualizations':[{'id':'%s','name':'EvAtDim line list','type':'LINE_LIST',
          'outputType':'EVENT','program':{'id':'%s'},'programStage':{'id':'%s'},
          'columnDimensions':['%s'],
          'attributeDimensions':[{'attribute':{'id':'%s'},'legendSet':{'id':'%s'},
            'filter':'%s'}]}]}
        """
        .formatted(
            EVENT_VISUALIZATION_UID,
            PROGRAM_UID,
            PROGRAM_STAGE_UID,
            ATTRIBUTE_UID,
            ATTRIBUTE_UID,
            legendSetUid,
            filter);
  }

  private static String dimension(String legendSetUid, String filter) {
    return String.join(":", ATTRIBUTE_UID, legendSetUid, filter);
  }

  /**
   * The persisted dimensions as {@code attribute:legendSet:filter}, in list order. The session is
   * flushed and cleared and the second-level cache evicted first, so they are read from the
   * database.
   */
  private List<String> dimensions() {
    startNewRequestSession();
    entityManagerFactory.getCache().evictAll();
    return GET(
            "/eventVisualizations/"
                + EVENT_VISUALIZATION_UID
                + "?fields=attributeDimensions[attribute[id],legendSet[id],filter]")
        .content(HttpStatus.OK)
        .getArray("attributeDimensions")
        .asList(JsonObject.class)
        .stream()
        .map(
            dimension ->
                String.join(
                    ":",
                    text(dimension.getObject("attribute"), "id"),
                    text(dimension.getObject("legendSet"), "id"),
                    text(dimension, "filter")))
        .toList();
  }

  private static String text(JsonObject object, String key) {
    return object.getString(key).string();
  }

  private void importMetadata(String metadata) {
    HttpResponse response = POST("/metadata", metadata);
    assertEquals(HttpStatus.OK, response.status(), () -> response.contentUnchecked().toJson());
  }
}
