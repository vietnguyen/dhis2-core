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
package org.hisp.dhis.dataset;

import static org.hisp.dhis.test.utils.Assertions.assertContainsOnly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManagerFactory;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.id.enhanced.NoopOptimizer;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.ManyToOneType;
import org.hibernate.type.TimestampType;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.period.MonthlyPeriodType;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.period.PeriodService;
import org.hisp.dhis.period.PeriodType;
import org.hisp.dhis.test.integration.PostgresIntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(Lifecycle.PER_CLASS)
@Transactional
class LockExceptionStoreTest extends PostgresIntegrationTestBase {
  @Autowired private IdentifiableObjectManager idObjectManager;

  @Autowired private PeriodService periodService;

  @Autowired private LockExceptionStore store;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private EntityManagerFactory entityManagerFactory;

  private PeriodType pt;

  private Period pA;

  private OrganisationUnit ouA;

  private OrganisationUnit ouB;

  private DataSet dsA;

  private DataSet dsB;

  private DataSet dsC;

  @BeforeAll
  void setUp() {
    pt = periodService.getPeriodTypeByClass(MonthlyPeriodType.class);
    pA = createPeriod(pt, getDate(2021, 1, 1), getDate(2021, 1, 31));
    periodService.addPeriod(pA);

    ouA = createOrganisationUnit('A');
    ouB = createOrganisationUnit('B');
    idObjectManager.save(ouA);
    idObjectManager.save(ouB);

    dsA = createDataSet('A', pt);
    dsB = createDataSet('B', pt);
    dsC = createDataSet('C', pt);
    idObjectManager.save(dsA);
    idObjectManager.save(dsB);
    idObjectManager.save(dsC);
  }

  @Test
  void testGetByDataSets() {
    LockException leA = new LockException(pA, ouA, dsA);
    LockException leB = new LockException(pA, ouA, dsB);
    LockException leC = new LockException(pA, ouA, dsC);
    LockException leD = new LockException(pA, ouB, dsA);
    LockException leE = new LockException(pA, ouB, dsB);
    LockException leF = new LockException(pA, ouB, dsC);

    Stream.of(leA, leB, leC, leD, leE, leF).forEach(le -> store.save(le));

    List<LockException> lockExceptions = store.getLockExceptions(List.of(dsA, dsB));

    assertContainsOnly(List.of(leA, leB, leD, leE), lockExceptions);

    lockExceptions = store.getLockExceptions(List.of(dsA));

    assertContainsOnly(List.of(leA, leD), lockExceptions);
  }

  @Test
  void testDeleteByOrganisationUnit() {
    LockException leA = new LockException(pA, ouA, dsA);
    LockException leB = new LockException(pA, ouB, dsA);
    store.save(leA);
    store.save(leB);
    assertEquals(1, getLockExceptionCount(ouA));
    assertEquals(1, getLockExceptionCount(ouB));
    store.deleteLockExceptions(ouA);
    assertEquals(0, getLockExceptionCount(ouA));
    assertEquals(1, getLockExceptionCount(ouB));
    store.deleteLockExceptions(ouB);
    assertEquals(0, getLockExceptionCount(ouA));
    assertEquals(0, getLockExceptionCount(ouB));
  }

  @Test
  void testIdIsGeneratedFromHibernateSequenceOneValuePerId() {
    SequenceStyleGenerator generator =
        assertInstanceOf(SequenceStyleGenerator.class, persister().getIdentifierGenerator());

    assertEquals(
        "hibernate_sequence",
        generator.getDatabaseStructure().getPhysicalName().getObjectName().getText());
    assertEquals(1, generator.getDatabaseStructure().getIncrementSize());
    assertInstanceOf(NoopOptimizer.class, generator.getOptimizer());
  }

  @Test
  void testColumnsAreWrittenAndReadBack() {
    LockException lockException = new LockException(pA, ouA, dsB);
    store.save(lockException);
    entityManager.flush();

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "select periodid, organisationunitid, datasetid, created from lockexception"
                + " where lockexceptionid = ?",
            lockException.getId());
    assertEquals(pA.getId(), ((Number) row.get("periodid")).longValue());
    assertEquals(ouA.getId(), ((Number) row.get("organisationunitid")).longValue());
    assertEquals(dsB.getId(), ((Number) row.get("datasetid")).longValue());
    assertEquals(lockException.getCreated().getTime(), ((Date) row.get("created")).getTime());

    clearSession();

    LockException reloaded = store.get(lockException.getId());
    assertEquals(pA.getIsoDate(), reloaded.getPeriod().getIsoDate());
    assertEquals(ouA.getUid(), reloaded.getOrganisationUnit().getUid());
    assertEquals(dsB.getUid(), reloaded.getDataSet().getUid());
    assertEquals(lockException.getCreated().getTime(), reloaded.getCreated().getTime());
  }

  @Test
  void testOrganisationUnitIsOptional() {
    LockException lockException = new LockException(pA, null, dsC);
    store.save(lockException);
    entityManager.flush();
    clearSession();

    assertNull(store.get(lockException.getId()).getOrganisationUnit());
  }

  @Test
  void testOnlyCreatedIsNotNull() {
    EntityPersister persister = persister();
    boolean[] nullability = persister.getPropertyNullability();

    assertFalse(nullability[propertyIndex("created")]);
    assertTrue(nullability[propertyIndex("period")]);
    assertTrue(nullability[propertyIndex("organisationUnit")]);
    assertTrue(nullability[propertyIndex("dataSet")]);
  }

  @Test
  void testCreatedIsMappedAsTimestamp() {
    assertInstanceOf(TimestampType.class, persister().getPropertyType("created"));
  }

  @Test
  void testReferencesAreLazy() {
    for (String property : List.of("period", "organisationUnit", "dataSet")) {
      ManyToOneType type =
          assertInstanceOf(ManyToOneType.class, persister().getPropertyType(property));
      assertFalse(type.isEager(null), property);
    }
  }

  @Test
  void testIsNotSecondLevelCached() {
    assertFalse(persister().canReadFromCache());
  }

  @Test
  void testDeleteByDataSetAndPeriodKeepsOtherDataSets() {
    store.save(new LockException(pA, ouA, dsA));
    store.save(new LockException(pA, ouB, dsA));
    store.save(new LockException(pA, ouA, dsB));

    store.deleteLockExceptions(dsA, pA);

    assertEquals(0, store.getCount(dsA, pA, ouA));
    assertEquals(0, store.getCount(dsA, pA, ouB));
    assertEquals(1, store.getCount(dsB, pA, ouA));
  }

  @Test
  void testDeleteByDataSetPeriodAndOrganisationUnitKeepsOtherOrganisationUnits() {
    store.save(new LockException(pA, ouA, dsC));
    store.save(new LockException(pA, ouB, dsC));

    store.deleteLockExceptions(dsC, pA, ouA);

    assertEquals(0, store.getCount(dsC, pA, ouA));
    assertEquals(1, store.getCount(dsC, pA, ouB));
  }

  /**
   * Test HQL delete statement with an HQL select statement to ensure the deleted rows are visible
   * by the current transaction.
   *
   * @param target the {@link OrganisationUnit}
   * @return the count of interpretations.
   */
  private long getLockExceptionCount(OrganisationUnit target) {
    return (Long)
        entityManager
            .createQuery(
                "select count(*) from LockException le where le.organisationUnit = :target")
            .setParameter("target", target)
            .getSingleResult();
  }

  private EntityPersister persister() {
    return entityManagerFactory
        .unwrap(SessionFactoryImplementor.class)
        .getMetamodel()
        .entityPersister(LockException.class);
  }

  private int propertyIndex(String property) {
    return persister().getEntityMetamodel().getPropertyIndex(property);
  }
}
