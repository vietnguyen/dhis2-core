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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.hisp.dhis.http.HttpStatus;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.test.webapi.PostgresControllerIntegrationTestBase;
import org.hisp.dhis.test.webapi.json.domain.JsonProperty;
import org.hisp.dhis.test.webapi.json.domain.JsonSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests the metadata import, merge and Jackson path of the category dimensions ({@code
 * CategoryDimension}) embedded in visualizations.
 */
@Transactional
class VisualizationCategoryDimensionsControllerTest extends PostgresControllerIntegrationTestBase {

  private static final String VISUALIZATION_UID = "VizCatDim01";

  private static final String CATEGORY_UID = "CatDimCat01";

  private static final String OPTION_A_UID = "CatDimOptnA";

  private static final String OPTION_B_UID = "CatDimOptnB";

  private static final String OPTION_C_UID = "CatDimOptnC";

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private EntityManagerFactory entityManagerFactory;

  @BeforeEach
  void setUp() {
    importMetadata(
        """
        {'categoryOptions':[%s,%s,%s],
         'categories':[{'id':'%s','name':'Category dimension category',
           'shortName':'Category dimension category','dataDimensionType':'DISAGGREGATION',
           'dataDimension':true,
           'categoryOptions':[{'id':'%s'},{'id':'%s'},{'id':'%s'}]}]}
        """
            .formatted(
                option(OPTION_A_UID),
                option(OPTION_B_UID),
                option(OPTION_C_UID),
                CATEGORY_UID,
                OPTION_A_UID,
                OPTION_B_UID,
                OPTION_C_UID));
  }

  @Test
  void testImportPersistsDimensionAndItemsInOrder() {
    importMetadata(visualization(OPTION_C_UID, OPTION_A_UID, OPTION_B_UID));

    assertEquals(List.of(dimension(OPTION_C_UID, OPTION_A_UID, OPTION_B_UID)), dimensions());
  }

  @Test
  void testReimportReplacesItems() {
    importMetadata(visualization(OPTION_C_UID, OPTION_A_UID, OPTION_B_UID));

    importMetadata(visualization(OPTION_B_UID));

    assertEquals(List.of(dimension(OPTION_B_UID)), dimensions());
  }

  /**
   * Exports as clients do (the filter with its items), changes the filter items and PUTs the export
   * back: the controller rebuilds the category dimension from the filter.
   */
  @Test
  void testPutOfExportedVisualizationUpdatesItems() throws Exception {
    importMetadata(visualization(OPTION_C_UID, OPTION_A_UID, OPTION_B_UID));
    ObjectNode exported =
        (ObjectNode)
            JSON.readTree(
                GET("/visualizations/" + VISUALIZATION_UID + "?fields=*,filters[id,items[id]]")
                    .content(HttpStatus.OK)
                    .toJson());
    ArrayNode filterItems = (ArrayNode) exported.withArray("filters").get(0).withArray("items");
    filterItems.removeAll();
    filterItems.addObject().put("id", OPTION_B_UID);
    filterItems.addObject().put("id", OPTION_C_UID);

    assertStatus(
        HttpStatus.OK,
        PUT("/visualizations/" + VISUALIZATION_UID, JSON.writeValueAsString(exported)));

    assertEquals(List.of(dimension(OPTION_B_UID, OPTION_C_UID)), dimensions());
  }

  /**
   * JSON patch serializes the whole visualization with plain Jackson, including the category of
   * each category dimension.
   */
  @Test
  void testJsonPatchOfVisualizationKeepsDimension() {
    importMetadata(visualization(OPTION_C_UID, OPTION_A_UID));
    startNewRequestSession();

    assertStatus(
        HttpStatus.OK,
        PATCH(
            "/visualizations/" + VISUALIZATION_UID,
            "[{'op':'replace','path':'/name','value':'Patched visualization'}]"));

    assertEquals(List.of(dimension(OPTION_C_UID, OPTION_A_UID)), dimensions());
    assertEquals(
        "Patched visualization",
        text(GET("/visualizations/" + VISUALIZATION_UID + "?fields=name").content(), "name"));
  }

  /** The mapping-derived flags of the categoryDimension schema and its properties. */
  @Test
  void testCategoryDimensionSchema() {
    JsonSchema schema =
        GET("/schemas/categoryDimension").content(HttpStatus.OK).as(JsonSchema.class);
    assertTrue(schema.isPersisted());
    assertTrue(schema.isEmbeddedObject());

    JsonProperty category = property(schema, "category");
    assertTrue(category.isPersisted());
    assertTrue(category.isManyToOne());
    assertTrue(category.isOwner());

    JsonProperty options = property(schema, "categoryOption");
    assertTrue(options.isPersisted());
    assertTrue(options.isOrdered());
    assertTrue(options.isManyToMany());
    assertTrue(options.isOwner());
  }

  private static String option(String uid) {
    return """
        {'id':'%s','name':'Option %s','shortName':'Option %s'}
        """
        .formatted(uid, uid, uid);
  }

  /**
   * A visualization filtered by the category. The filter dimension is what the visualization
   * controller rebuilds the category dimensions from on PUT.
   */
  private static String visualization(String... optionUids) {
    String items =
        Arrays.stream(optionUids).map("{'id':'%s'}"::formatted).collect(Collectors.joining(","));
    return """
        {'visualizations':[{'id':'%s','name':'Category dimension visualization','type':'COLUMN',
          'filterDimensions':['%s'],
          'categoryDimensions':[{'category':{'id':'%s'},'categoryOptions':[%s]}]}]}
        """
        .formatted(VISUALIZATION_UID, CATEGORY_UID, CATEGORY_UID, items);
  }

  private static String dimension(String... optionUids) {
    return CATEGORY_UID + ":" + String.join(",", optionUids);
  }

  /**
   * The persisted dimensions as {@code category:option,option,...}, in list order. The session is
   * flushed and cleared and the second-level cache evicted first, so they are read from the
   * database.
   */
  private List<String> dimensions() {
    startNewRequestSession();
    entityManagerFactory.getCache().evictAll();
    return GET(
            "/visualizations/"
                + VISUALIZATION_UID
                + "?fields=categoryDimensions[category[id],categoryOptions[id]]")
        .content(HttpStatus.OK)
        .getArray("categoryDimensions")
        .asList(JsonObject.class)
        .stream()
        .map(
            dimension ->
                text(dimension.getObject("category"), "id")
                    + ":"
                    + dimension.getArray("categoryOptions").asList(JsonObject.class).stream()
                        .map(option -> text(option, "id"))
                        .collect(Collectors.joining(",")))
        .toList();
  }

  private static JsonProperty property(JsonSchema schema, String name) {
    return schema.getProperties().stream()
        .filter(property -> name.equals(property.getName()))
        .findFirst()
        .orElseGet(() -> fail("no schema property " + name));
  }

  private static String text(JsonObject object, String key) {
    return object.getString(key).string();
  }

  private void importMetadata(String metadata) {
    HttpResponse response = POST("/metadata", metadata);
    assertEquals(HttpStatus.OK, response.status(), () -> response.contentUnchecked().toJson());
  }
}
