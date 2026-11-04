/*
 * Copyright (c) 2004-2024, University of Oslo
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
package org.hisp.dhis.analytics.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.support.CollectionNonStrictReadWriteAccess;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.id.enhanced.NoopOptimizer;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.ManyToOneType;
import org.hisp.dhis.analytics.CategoryDimensionStore;
import org.hisp.dhis.category.Category;
import org.hisp.dhis.category.CategoryDimension;
import org.hisp.dhis.category.CategoryOption;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.common.IdentifiableObject;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.dbms.DbmsManager;
import org.hisp.dhis.test.api.TestCategoryMetadata;
import org.hisp.dhis.test.integration.PostgresIntegrationTestBase;
import org.hisp.dhis.visualization.Visualization;
import org.hisp.dhis.visualization.VisualizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author david mackessy
 */
@Transactional
class CategoryDimensionStoreTest extends PostgresIntegrationTestBase {

  private static final String ITEMS_ROLE = CategoryDimension.class.getName() + ".items";

  @Autowired private CategoryDimensionStore categoryDimensionStore;
  @Autowired private IdentifiableObjectManager manager;
  @Autowired private DbmsManager dbmsManager;
  @Autowired private VisualizationService visualizationService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @Test
  @DisplayName("Retrieving CategoryDimensions by CategoryOptions returns the expected objects")
  void getCatDimensionsByCatOptionsTest() {
    // given 4 CategoryDimensions, each with a different CategoryOption
    CategoryOption co1 = createCategoryOption("1", CodeGenerator.generateUid());
    CategoryOption co2 = createCategoryOption("2", CodeGenerator.generateUid());
    CategoryOption co3 = createCategoryOption("3", CodeGenerator.generateUid());
    CategoryOption co4 = createCategoryOption("4", CodeGenerator.generateUid());
    manager.save(List.of(co1, co2, co3, co4));

    Category cat1 = createCategory('1', co1);
    Category cat2 = createCategory('2', co2);
    Category cat3 = createCategory('3', co3);
    Category cat4 = createCategory('4', co4);
    manager.save(List.of(cat1, cat2, cat3, cat4));

    CategoryDimension cd1 = createCategoryDimension(cat1);
    cd1.getItems().add(co1);

    CategoryDimension cd2 = createCategoryDimension(cat2);
    cd2.getItems().add(co2);

    CategoryDimension cd3 = createCategoryDimension(cat3);
    cd3.getItems().add(co3);

    CategoryDimension cd4 = createCategoryDimension(cat4);
    cd4.getItems().add(co4);

    categoryDimensionStore.save(cd1);
    categoryDimensionStore.save(cd2);
    categoryDimensionStore.save(cd3);
    categoryDimensionStore.save(cd4);

    // when retrieving CategoryDimensions by CategoryOption (3)
    List<CategoryDimension> cdsByCategoryOption =
        categoryDimensionStore.getByCategoryOption(
            List.of(co1.getUid(), co2.getUid(), co3.getUid()));

    // then
    assertEquals(3, cdsByCategoryOption.size(), "3 CategoryDimensions should be present");
    List<String> cos =
        cdsByCategoryOption.stream()
            .flatMap(cd -> cd.getItems().stream())
            .map(IdentifiableObject::getUid)
            .toList();

    assertEquals(3, cos.size(), "3 CategoryOptions should be present");
    assertTrue(
        cos.containsAll(List.of(co1.getUid(), co2.getUid(), co3.getUid())),
        "Retrieved CategoryOption UIDs should have expected UIDs");
  }

  @Test
  @DisplayName("Updating CategoryDimensions by Categories returns the expected count")
  void updateCatDimensionCategoryRefsByCategoriesTest() {
    // given 3 CategoryDimensions exist, each with a different Category
    TestCategoryMetadata categoryMetadata1 = setupCategoryMetadata("1");
    TestCategoryMetadata categoryMetadata2 = setupCategoryMetadata("2");
    setupCategoryMetadata("3");

    CategoryDimension cd1 = createCategoryDimension(categoryMetadata1.c1());
    cd1.getItems().add(categoryMetadata1.co1());

    CategoryDimension cd2 = createCategoryDimension(categoryMetadata1.c2());
    cd2.getItems().add(categoryMetadata1.co3());

    CategoryDimension cd3 = createCategoryDimension(categoryMetadata2.c1());
    cd3.getItems().add(categoryMetadata2.co1());

    CategoryDimension cd4 = createCategoryDimension(categoryMetadata2.c2());
    cd4.getItems().add(categoryMetadata2.co1());

    categoryDimensionStore.save(cd1);
    categoryDimensionStore.save(cd2);
    categoryDimensionStore.save(cd3);
    categoryDimensionStore.save(cd4);

    // when updating 2 CategoryDimensions
    int updatedCatDimensions =
        categoryDimensionStore.updateCatDimensionCategoryRefs(
            Set.of(categoryMetadata1.c1().getId(), categoryMetadata1.c2().getId()),
            categoryMetadata2.c1().getId());
    dbmsManager.clearSession();

    // then the expected number of CategoryDimensions should be updated
    assertEquals(2, updatedCatDimensions, "2 CategoryDimensions have been updated");

    // and these CategoryDimensions should not be affected
    assertEquals(
        categoryMetadata2.c1().getUid(),
        cd3.getDimension().getUid(),
        "This Dimension Category UID should not be affected");

    assertEquals(
        categoryMetadata2.c2().getUid(),
        cd4.getDimension().getUid(),
        "This Dimension Category UID should not be affected");

    // and these CategoryDimensions should have new refs
    assertEquals(
        categoryMetadata1.c1().getUid(),
        cd1.getDimension().getUid(),
        "Dimension Category UID should be updated");

    assertEquals(
        categoryMetadata1.c2().getUid(),
        cd2.getDimension().getUid(),
        "Dimension Category UID should be updated");
  }

  @Test
  @DisplayName(
      "the id is generated by a sequence generator on hibernate_sequence, one nextval per id")
  void testIdGeneratorUsesHibernateSequence() {
    SequenceStyleGenerator generator =
        assertInstanceOf(SequenceStyleGenerator.class, persister().getIdentifierGenerator());

    assertEquals(
        "hibernate_sequence",
        generator.getDatabaseStructure().getPhysicalName().getObjectName().getText());
    assertEquals(1, generator.getDatabaseStructure().getIncrementSize());
    assertInstanceOf(NoopOptimizer.class, generator.getOptimizer());
  }

  @Test
  @DisplayName("the category is written to categoryid and read back from it")
  void testDimensionRoundTrip() {
    TestCategoryMetadata metadata = setupCategoryMetadata("R");
    CategoryDimension dimension = persist(dimension(metadata.c1(), metadata.co1()));

    assertEquals(
        metadata.c1().getId(),
        jdbcTemplate.queryForObject(
            "select categoryid from categorydimension where categorydimensionid = ?",
            Long.class,
            dimension.getId()));

    clearSessionAndSecondLevelCache();

    CategoryDimension reloaded = reload(dimension);
    assertEquals(dimension.getId(), reloaded.getId());
    assertEquals(metadata.c1().getUid(), reloaded.getDimension().getUid());
  }

  @Test
  @DisplayName(
      "the items are written to categorydimension_items with a 0-based sort_order and read back"
          + " in that order")
  void testItemsKeepTheirOrder() {
    TestCategoryMetadata metadata = setupCategoryMetadata("O");
    CategoryDimension dimension = persist(dimension(metadata.c1(), metadata.co2(), metadata.co1()));

    assertEquals(
        List.of(metadata.co2().getId() + ":0", metadata.co1().getId() + ":1"), itemRows(dimension));

    clearSessionAndSecondLevelCache();

    assertEquals(
        List.of(metadata.co2().getUid(), metadata.co1().getUid()), itemUids(reload(dimension)));
  }

  @Test
  @DisplayName("setItems replaces the persisted items")
  void testSetItemsReplacesItems() {
    TestCategoryMetadata metadata = setupCategoryMetadata("S");
    CategoryDimension dimension = persist(dimension(metadata.c1(), metadata.co1(), metadata.co2()));
    clearSessionAndSecondLevelCache();

    CategoryDimension loaded = reload(dimension);
    loaded.setItems(new ArrayList<>(List.of(metadata.co2())));
    entityManager.flush();
    clearSessionAndSecondLevelCache();

    assertEquals(List.of(metadata.co2().getUid()), itemUids(reload(dimension)));
  }

  @Test
  @DisplayName("the category and the items are loaded lazily")
  void testAssociationsAreLazy() {
    ManyToOneType dimensionType =
        assertInstanceOf(ManyToOneType.class, persister().getPropertyType("dimension"));

    assertFalse(dimensionType.isEager(null));
    assertTrue(itemsPersister().isLazy());
  }

  @Test
  @DisplayName(
      "the entity and its items are second-level cached with the nonstrict-read-write strategy")
  void testNonstrictReadWriteCaches() {
    EntityPersister persister = persister();
    CollectionPersister itemsPersister = itemsPersister();

    assertTrue(persister.canReadFromCache());
    assertEquals(
        AccessType.NONSTRICT_READ_WRITE, persister.getCacheAccessStrategy().getAccessType());
    // a nonstrict-read-write collection access reports READ_WRITE from getAccessType() in
    // Hibernate 5.6, so the strategy is proven by the access implementation instead
    assertTrue(itemsPersister.hasCache());
    assertInstanceOf(
        CollectionNonStrictReadWriteAccess.class, itemsPersister.getCacheAccessStrategy());
  }

  @Test
  @DisplayName("a visualization saves its category dimensions by cascade and keeps their order")
  void testVisualizationOwnsOrderedDimensions() {
    TestCategoryMetadata metadata = setupCategoryMetadata("V");
    Visualization visualization = createVisualization('V');
    visualization.addCategoryDimension(dimension(metadata.c2(), metadata.co4(), metadata.co3()));
    visualization.addCategoryDimension(dimension(metadata.c1(), metadata.co1()));
    long visualizationId = visualizationService.save(visualization);
    clearSessionAndSecondLevelCache();

    List<CategoryDimension> reloaded =
        visualizationService.getVisualization(visualizationId).getCategoryDimensions();
    assertEquals(
        List.of(
            List.of(metadata.co4().getUid(), metadata.co3().getUid()),
            List.of(metadata.co1().getUid())),
        reloaded.stream().map(CategoryDimensionStoreTest::itemUids).toList());
  }

  /** Reloads must hit the database, not the second-level cache of the dimension or its items. */
  private void clearSessionAndSecondLevelCache() {
    clearSession();
    entityManagerFactory.getCache().evictAll();
  }

  private SessionFactoryImplementor sessionFactory() {
    return entityManagerFactory.unwrap(SessionFactoryImplementor.class);
  }

  private EntityPersister persister() {
    return sessionFactory().getMetamodel().entityPersister(CategoryDimension.class);
  }

  private CollectionPersister itemsPersister() {
    return sessionFactory().getMetamodel().collectionPersister(ITEMS_ROLE);
  }

  private static CategoryDimension dimension(Category category, CategoryOption... items) {
    CategoryDimension dimension = createCategoryDimension(category);
    dimension.setItems(new ArrayList<>(List.of(items)));
    return dimension;
  }

  private CategoryDimension persist(CategoryDimension dimension) {
    entityManager.persist(dimension);
    entityManager.flush();
    return dimension;
  }

  private CategoryDimension reload(CategoryDimension dimension) {
    return entityManager.find(CategoryDimension.class, dimension.getId());
  }

  /** The dimension's item rows as {@code categoryoptionid:sort_order}, by sort_order. */
  private List<String> itemRows(CategoryDimension dimension) {
    return jdbcTemplate.queryForList(
        "select categoryoptionid || ':' || sort_order from categorydimension_items"
            + " where categorydimensionid = ? order by sort_order",
        String.class,
        dimension.getId());
  }

  private static List<String> itemUids(CategoryDimension dimension) {
    return dimension.getItems().stream().map(CategoryOption::getUid).toList();
  }
}
