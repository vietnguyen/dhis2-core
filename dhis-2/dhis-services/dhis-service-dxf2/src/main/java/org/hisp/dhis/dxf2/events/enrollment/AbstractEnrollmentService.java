package org.hisp.dhis.dxf2.events.enrollment;

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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.GeometryFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.common.IdSchemes;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.common.IllegalQueryException;
import org.hisp.dhis.common.OrganisationUnitSelectionMode;
import org.hisp.dhis.common.Pager;
import org.hisp.dhis.common.exception.InvalidIdentifierReferenceException;
import org.hisp.dhis.commons.collection.CachingMap;
import org.hisp.dhis.commons.util.DebugUtils;
import org.hisp.dhis.dbms.DbmsManager;
import org.hisp.dhis.dxf2.common.ImportOptions;
import org.hisp.dhis.dxf2.events.RelationshipParams;
import org.hisp.dhis.dxf2.events.TrackedEntityInstanceParams;
import org.hisp.dhis.dxf2.events.TrackerAccessManager;
import org.hisp.dhis.dxf2.events.event.Coordinate;
import org.hisp.dhis.dxf2.events.event.Event;
import org.hisp.dhis.dxf2.events.event.EventService;
import org.hisp.dhis.dxf2.events.event.Note;
import org.hisp.dhis.dxf2.events.relationship.RelationshipService;
import org.hisp.dhis.dxf2.events.trackedentity.Attribute;
import org.hisp.dhis.dxf2.events.trackedentity.TrackedEntityInstance;
import org.hisp.dhis.dxf2.events.trackedentity.TrackedEntityInstanceService;
import org.hisp.dhis.dxf2.importsummary.ImportConflict;
import org.hisp.dhis.dxf2.importsummary.ImportStatus;
import org.hisp.dhis.dxf2.importsummary.ImportSummaries;
import org.hisp.dhis.dxf2.importsummary.ImportSummary;
import org.hisp.dhis.i18n.I18nManager;
import org.hisp.dhis.organisationunit.FeatureType;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramInstance;
import org.hisp.dhis.program.ProgramInstanceQueryParams;
import org.hisp.dhis.program.ProgramInstanceService;
import org.hisp.dhis.program.ProgramService;
import org.hisp.dhis.program.ProgramStageInstance;
import org.hisp.dhis.program.ProgramStageInstanceService;
import org.hisp.dhis.program.ProgramStatus;
import org.hisp.dhis.program.ProgramTrackedEntityAttribute;
import org.hisp.dhis.query.Query;
import org.hisp.dhis.query.QueryService;
import org.hisp.dhis.query.Restrictions;
import org.hisp.dhis.relationship.RelationshipItem;
import org.hisp.dhis.scheduling.JobConfiguration;
import org.hisp.dhis.schema.SchemaService;
import org.hisp.dhis.security.Authorities;
import org.hisp.dhis.system.callable.IdentifiableObjectCallable;
import org.hisp.dhis.system.notification.NotificationLevel;
import org.hisp.dhis.system.notification.Notifier;
import org.hisp.dhis.system.util.DateUtils;
import org.hisp.dhis.trackedentity.TrackedEntityAttribute;
import org.hisp.dhis.trackedentity.TrackedEntityAttributeService;
import org.hisp.dhis.trackedentity.TrackerOwnershipAccessManager;
import org.hisp.dhis.trackedentityattributevalue.TrackedEntityAttributeValue;
import org.hisp.dhis.trackedentityattributevalue.TrackedEntityAttributeValueService;
import org.hisp.dhis.trackedentitycomment.TrackedEntityComment;
import org.hisp.dhis.trackedentitycomment.TrackedEntityCommentService;
import org.hisp.dhis.user.CurrentUserService;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hisp.dhis.system.notification.NotificationLevel.ERROR;

/**
 * @author Morten Olav Hansen <mortenoh@gmail.com>
 */
public abstract class AbstractEnrollmentService
    implements EnrollmentService
{
    private static final Log log = LogFactory.getLog( AbstractEnrollmentService.class );

    @Autowired
    protected ProgramInstanceService programInstanceService;

    @Autowired
    protected ProgramStageInstanceService programStageInstanceService;

    @Autowired
    protected ProgramService programService;

    @Autowired
    protected TrackedEntityInstanceService trackedEntityInstanceService;

    @Autowired
    protected TrackerOwnershipAccessManager trackerOwnershipAccessManager;

    @Autowired
    protected RelationshipService relationshipService;

    @Autowired
    protected org.hisp.dhis.trackedentity.TrackedEntityInstanceService teiService;

    @Autowired
    protected TrackedEntityAttributeService trackedEntityAttributeService;

    @Autowired
    protected TrackedEntityAttributeValueService trackedEntityAttributeValueService;

    @Autowired
    protected CurrentUserService currentUserService;

    @Autowired
    protected TrackedEntityCommentService commentService;

    @Autowired
    protected IdentifiableObjectManager manager;

    @Autowired
    protected I18nManager i18nManager;

    @Autowired
    protected UserService userService;

    @Autowired
    protected DbmsManager dbmsManager;

    @Autowired
    protected EventService eventService;

    @Autowired
    protected TrackerAccessManager trackerAccessManager;

    @Autowired
    protected SchemaService schemaService;

    @Autowired
    protected QueryService queryService;

    @Autowired
    protected Notifier notifier;

    private CachingMap<String, OrganisationUnit> organisationUnitCache = new CachingMap<>();

    private CachingMap<String, Program> programCache = new CachingMap<>();

    private CachingMap<String, TrackedEntityAttribute> trackedEntityAttributeCache = new CachingMap<>();

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    @Override
    public Enrollments getEnrollments( ProgramInstanceQueryParams params )
    {
        Enrollments enrollments = new Enrollments();

        if ( !params.isPaging() && !params.isSkipPaging() )
        {
            params.setDefaultPaging();
        }

        if ( params.isPaging() )
        {
            int count = 0;

            if ( params.isTotalPages() )
            {
                count = programInstanceService.countProgramInstances( params );
            }

            Pager pager = new Pager( params.getPageWithDefault(), count, params.getPageSizeWithDefault() );

            enrollments.setPager( pager );
        }

        List<ProgramInstance> programInstances = programInstanceService.getProgramInstances( params );
        enrollments.setEnrollments( getEnrollments( programInstances ) );

        return enrollments;
    }

    @Override
    public List<Enrollment> getEnrollments( Iterable<ProgramInstance> programInstances )
    {
        List<Enrollment> enrollments = new ArrayList<>();
        User user = currentUserService.getCurrentUser();

        for ( ProgramInstance programInstance : programInstances )
        {
            if ( programInstance != null && programInstance.getEntityInstance() != null
                && trackerAccessManager.canRead( user, programInstance ).isEmpty() )
            {
                enrollments.add( getEnrollment( user, programInstance, TrackedEntityInstanceParams.FALSE ) );
            }
        }

        return enrollments;
    }

    @Override
    public Enrollment getEnrollment( String id )
    {
        ProgramInstance programInstance = programInstanceService.getProgramInstance( id );
        return programInstance != null ? getEnrollment( programInstance ) : null;
    }

    @Override
    public Enrollment getEnrollment( ProgramInstance programInstance )
    {
        return getEnrollment( currentUserService.getCurrentUser(), programInstance, TrackedEntityInstanceParams.FALSE );
    }

    @Override
    public Enrollment getEnrollment( ProgramInstance programInstance, TrackedEntityInstanceParams params )
    {
        return getEnrollment( currentUserService.getCurrentUser(), programInstance, params );
    }

    @Override
    public Enrollment getEnrollment( User user, ProgramInstance programInstance, TrackedEntityInstanceParams params )
    {
        Enrollment enrollment = new Enrollment();
        enrollment.setEnrollment( programInstance.getUid() );
        List<String> errors = trackerAccessManager.canRead( user, programInstance );

        if ( !errors.isEmpty() )
        {
            throw new IllegalQueryException( errors.toString() );
        }

        if ( programInstance.getEntityInstance() != null )
        {
            enrollment.setTrackedEntityType( programInstance.getEntityInstance().getTrackedEntityType().getUid() );
            enrollment.setTrackedEntityInstance( programInstance.getEntityInstance().getUid() );
        }

        if ( programInstance.getOrganisationUnit() != null )
        {
            enrollment.setOrgUnit( programInstance.getOrganisationUnit().getUid() );
            enrollment.setOrgUnitName( programInstance.getOrganisationUnit().getName() );
        }

        if ( programInstance.getGeometry() != null )
        {
            enrollment.setGeometry( programInstance.getGeometry() );

            if ( programInstance.getProgram().getFeatureType().equals( FeatureType.POINT ) )
            {
                com.vividsolutions.jts.geom.Coordinate co = programInstance.getGeometry().getCoordinate();
                enrollment.setCoordinate( new Coordinate( co.x, co.y ) );
            }
        }

        enrollment.setCreated( DateUtils.getIso8601NoTz( programInstance.getCreated() ) );
        enrollment.setCreatedAtClient( DateUtils.getIso8601NoTz( programInstance.getCreatedAtClient() ) );
        enrollment.setLastUpdated( DateUtils.getIso8601NoTz( programInstance.getLastUpdated() ) );
        enrollment.setLastUpdatedAtClient( DateUtils.getIso8601NoTz( programInstance.getLastUpdatedAtClient() ) );
        enrollment.setProgram( programInstance.getProgram().getUid() );
        enrollment.setStatus( EnrollmentStatus.fromProgramStatus( programInstance.getStatus() ) );
        enrollment.setEnrollmentDate( programInstance.getEnrollmentDate() );
        enrollment.setIncidentDate( programInstance.getIncidentDate() );
        enrollment.setFollowup( programInstance.getFollowup() );
        enrollment.setCompletedDate( programInstance.getEndDate() );
        enrollment.setCompletedBy( programInstance.getCompletedBy() );
        enrollment.setStoredBy( programInstance.getStoredBy() );
        enrollment.setDeleted( programInstance.isDeleted() );

        List<TrackedEntityComment> comments = programInstance.getComments();

        for ( TrackedEntityComment comment : comments )
        {
            Note note = new Note();

            note.setNote( comment.getUid() );
            note.setValue( comment.getCommentText() );
            note.setStoredBy( comment.getCreator() );
            note.setStoredDate( DateUtils.getIso8601NoTz( comment.getCreated() ) );

            enrollment.getNotes().add( note );
        }

        if ( params.isIncludeEvents() )
        {
            for ( ProgramStageInstance programStageInstance : programInstance.getProgramStageInstances() )
            {
                if ( (params.isIncludeDeleted() || !programStageInstance.isDeleted()) && trackerAccessManager.canRead( user, programStageInstance ).isEmpty() )
                {
                    enrollment.getEvents().add( eventService.getEvent( programStageInstance, params.isDataSynchronizationQuery() ) );
                }
            }
        }

        if ( params.isIncludeRelationships() )
        {
            for ( RelationshipItem relationshipItem : programInstance.getRelationshipItems() )
            {
                enrollment.getRelationships().add( relationshipService.getRelationship( relationshipItem.getRelationship(),
                    RelationshipParams.FALSE, user ) );
            }
        }

        return enrollment;
    }

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    @Override
    public ImportSummaries addEnrollments( List<Enrollment> enrollments, ImportOptions importOptions, boolean clearSession )
    {
        return addEnrollments( enrollments, importOptions, null, clearSession );
    }

    @Override
    public ImportSummaries addEnrollments( List<Enrollment> enrollments, ImportOptions importOptions, JobConfiguration jobId )
    {
        notifier.clear( jobId ).notify( jobId, "Importing enrollments" );
        importOptions = updateImportOptions( importOptions );

        try
        {
            ImportSummaries importSummaries = addEnrollments( enrollments, importOptions, true );

            if ( jobId != null )
            {
                notifier.notify( jobId, NotificationLevel.INFO, "Import done", true ).addJobSummary( jobId, importSummaries, ImportSummaries.class );
            }

            return importSummaries;
        }
        catch ( RuntimeException ex )
        {
            log.error( DebugUtils.getStackTrace( ex ) );
            notifier.notify( jobId, ERROR, "Process failed: " + ex.getMessage(), true );
            return new ImportSummaries().addImportSummary( new ImportSummary( ImportStatus.ERROR, "The import process failed: " + ex.getMessage() ) );
        }
    }

    @Override
    public ImportSummaries addEnrollments( List<Enrollment> enrollments, ImportOptions importOptions, org.hisp.dhis.trackedentity.TrackedEntityInstance daoTrackedEntityInstance, boolean clearSession )
    {
        List<List<Enrollment>> partitions = Lists.partition( enrollments, FLUSH_FREQUENCY );
        importOptions = updateImportOptions( importOptions );
        ImportSummaries importSummaries = new ImportSummaries();

        for ( List<Enrollment> _enrollments : partitions )
        {
            reloadUser( importOptions );
            prepareCaches( _enrollments, importOptions.getUser() );

            for ( Enrollment enrollment : _enrollments )
            {
                importSummaries.addImportSummary( addEnrollment( enrollment, importOptions, daoTrackedEntityInstance ) );
            }

            if ( clearSession && enrollments.size() >= FLUSH_FREQUENCY )
            {
                clearSession();
            }
        }

        return importSummaries;
    }

    @Override
    public ImportSummary addEnrollment( Enrollment enrollment, ImportOptions importOptions )
    {
        return addEnrollment( enrollment, importOptions, null );
    }

    @Override
    public ImportSummary addEnrollment( Enrollment enrollment, ImportOptions importOptions, org.hisp.dhis.trackedentity.TrackedEntityInstance daoTrackedEntityInstance )
    {
        importOptions = updateImportOptions( importOptions );

        String storedBy = !StringUtils.isEmpty( enrollment.getStoredBy() ) && enrollment.getStoredBy().length() < 31 ?
            enrollment.getStoredBy() :
            (importOptions.getUser() == null || StringUtils.isEmpty( importOptions.getUser().getUsername() ) ? "system-process" : importOptions.getUser().getUsername());

        if ( programInstanceService.programInstanceExistsIncludingDeleted( enrollment.getEnrollment() ) )
        {
            return new ImportSummary( ImportStatus.ERROR,
                "Enrollment " + enrollment.getEnrollment() + " already exists or was deleted earlier" ).setReference( enrollment.getEnrollment() ).incrementIgnored();
        }

        if ( daoTrackedEntityInstance == null )
        {
            daoTrackedEntityInstance = getTrackedEntityInstance( enrollment.getTrackedEntityInstance() );
        }

        Program program = getProgram( importOptions.getIdSchemes(), enrollment.getProgram() );

        ImportSummary importSummary = validateRequest( program, daoTrackedEntityInstance, enrollment, importOptions );

        if ( importSummary.getStatus() != ImportStatus.SUCCESS )
        {
            return importSummary;
        }

        OrganisationUnit organisationUnit = getOrganisationUnit( importOptions.getIdSchemes(), enrollment.getOrgUnit() );

        List<String> errors = trackerAccessManager.canWrite( importOptions.getUser(),
            new ProgramInstance( program, daoTrackedEntityInstance, organisationUnit ) );

        if ( !errors.isEmpty() )
        {
            return new ImportSummary( ImportStatus.ERROR, errors.toString() )
                .incrementIgnored();
        }

        if ( enrollment.getStatus() == null )
        {
            enrollment.setStatus( EnrollmentStatus.ACTIVE );
        }

        ProgramStatus programStatus = enrollment.getStatus() == EnrollmentStatus.ACTIVE ? ProgramStatus.ACTIVE :
            enrollment.getStatus() == EnrollmentStatus.COMPLETED ? ProgramStatus.COMPLETED : ProgramStatus.CANCELLED;

        ProgramInstance programInstance = programInstanceService.prepareProgramInstance( daoTrackedEntityInstance, program, programStatus,
            enrollment.getEnrollmentDate(), enrollment.getIncidentDate(), organisationUnit, enrollment.getEnrollment() );

        programInstanceService.addProgramInstance( programInstance );

        importSummary = validateProgramInstance( program, programInstance, enrollment );

        if ( importSummary.getStatus() != ImportStatus.SUCCESS )
        {
            return importSummary;
        }

        updateFeatureType( program, enrollment, programInstance );
        updateAttributeValues( enrollment, importOptions );
        updateDateFields( enrollment, programInstance );
        programInstance.setFollowup( enrollment.getFollowup() );
        programInstance.setStoredBy( storedBy );

        programInstanceService.updateProgramInstance( programInstance );

        trackerOwnershipAccessManager.assignOwnership( daoTrackedEntityInstance, program, organisationUnit, true, true );

        saveTrackedEntityComment( programInstance, enrollment, importOptions.getUser() != null ? importOptions.getUser().getUsername() : "[Unknown]" );

        importSummary.setReference( programInstance.getUid() );
        importSummary.getImportCount().incrementImported();

        importSummary.setEvents( handleEvents( enrollment, programInstance, importOptions ) );

        return importSummary;
    }

    private ImportSummary validateProgramInstance( Program program, ProgramInstance programInstance, Enrollment enrollment )
    {
        ImportSummary importSummary = new ImportSummary( enrollment.getEnrollment() );

        if ( programInstance == null )
        {
            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.setDescription( "Could not enroll tracked entity instance "
                + enrollment.getTrackedEntityInstance() + " into program " + enrollment.getProgram() );
            importSummary.incrementIgnored();

            return importSummary;
        }

        if ( program.getDisplayIncidentDate() && programInstance.getIncidentDate() == null )
        {
            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.setDescription( "DisplayIncidentDate is true but IncidentDate is null " );
            importSummary.incrementIgnored();
        }

        return importSummary;
    }

    private ImportSummary validateRequest( Program program, org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance,
        Enrollment enrollment, ImportOptions importOptions )
    {
        ImportSummary importSummary = new ImportSummary( enrollment.getEnrollment() );

        if ( !program.isRegistration() )
        {
            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.setDescription( "Provided program " + program.getUid() +
                " is a program without registration. An enrollment cannot be created into program without registration." );
            importSummary.incrementIgnored();

            return importSummary;
        }

        ProgramInstanceQueryParams params = new ProgramInstanceQueryParams();
        params.setOrganisationUnitMode( OrganisationUnitSelectionMode.ALL );
        params.setSkipPaging( true );
        params.setProgram( program );
        params.setTrackedEntityInstance( entityInstance );

        // When imported enrollment has status CANCELLED, it is safe to import it, otherwise do additional checks
        // We allow import of CANCELLED and COMPLETED enrollments because the endpoint is used for bulk import and sync purposes as well
        if ( enrollment.getStatus() != EnrollmentStatus.CANCELLED )
        {
            List<Enrollment> enrollments = getEnrollments( programInstanceService.getProgramInstances( params ) );

            Set<Enrollment> activeEnrollments = enrollments.stream()
                .filter( e -> e.getStatus() == EnrollmentStatus.ACTIVE )
                .collect( Collectors.toSet());

            // When an enrollment with status COMPLETED or CANCELLED is being imported, no check whether there is already some ACTIVE one is needed
            if ( !activeEnrollments.isEmpty() && enrollment.getStatus() == EnrollmentStatus.ACTIVE )
            {
                importSummary.setStatus( ImportStatus.ERROR );
                importSummary.setDescription( "TrackedEntityInstance " + entityInstance.getUid()
                    + " already has an active enrollment in program " + program.getUid() );
                importSummary.incrementIgnored();

                return importSummary;
            }

            // The error of enrolling more than once is possible only if the imported enrollment has a state other than CANCELLED
            if ( program.getOnlyEnrollOnce() )
            {

                Set<Enrollment> activeOrCompletedEnrollments = enrollments.stream()
                    .filter( e -> e.getStatus() == EnrollmentStatus.ACTIVE || e.getStatus() == EnrollmentStatus.COMPLETED )
                    .collect( Collectors.toSet());

                if ( !activeOrCompletedEnrollments.isEmpty() )
                {
                    importSummary.setStatus( ImportStatus.ERROR );
                    importSummary.setDescription( "TrackedEntityInstance " + entityInstance.getUid()
                        + " already has an active or completed enrollment in program " + program.getUid() +
                        ", and this program only allows enrolling one time" );
                    importSummary.incrementIgnored();

                    return importSummary;
                }
            }
        }

        Set<ImportConflict> importConflicts = new HashSet<>( checkAttributes( enrollment, importOptions ) );

        if ( !importConflicts.isEmpty() )
        {
            importSummary.setConflicts( importConflicts );
            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.incrementIgnored();
        }

        return importSummary;
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    @Override
    public ImportSummaries updateEnrollments( List<Enrollment> enrollments, ImportOptions importOptions, boolean clearSession )
    {
        List<List<Enrollment>> partitions = Lists.partition( enrollments, FLUSH_FREQUENCY );
        importOptions = updateImportOptions( importOptions );
        ImportSummaries importSummaries = new ImportSummaries();

        for ( List<Enrollment> _enrollments : partitions )
        {
            reloadUser( importOptions );
            prepareCaches( _enrollments, importOptions.getUser() );

            for ( Enrollment enrollment : _enrollments )
            {
                importSummaries.addImportSummary( updateEnrollment( enrollment, importOptions ) );
            }

            if ( clearSession && enrollments.size() >= FLUSH_FREQUENCY )
            {
                clearSession();
            }
        }

        return importSummaries;
    }

    @Override
    public ImportSummary updateEnrollment( Enrollment enrollment, ImportOptions importOptions )
    {
        importOptions = updateImportOptions( importOptions );

        if ( enrollment == null || StringUtils.isEmpty( enrollment.getEnrollment() ) )
        {
            return new ImportSummary( ImportStatus.ERROR, "No enrollment or enrollment ID was supplied" ).incrementIgnored();
        }

        ProgramInstance programInstance = programInstanceService.getProgramInstance( enrollment.getEnrollment() );
        List<String> errors = trackerAccessManager.canWrite( importOptions.getUser(), programInstance );

        if ( programInstance == null )
        {
            return new ImportSummary( ImportStatus.ERROR, "ID " + enrollment.getEnrollment() + " doesn't point to a valid enrollment." )
                .incrementIgnored();
        }

        if ( !errors.isEmpty() )
        {
            return new ImportSummary( ImportStatus.ERROR, errors.toString() )
                .incrementIgnored();
        }

        Set<ImportConflict> importConflicts = new HashSet<>( checkAttributes( enrollment, importOptions ) );

        if ( !importConflicts.isEmpty() )
        {
            ImportSummary importSummary = new ImportSummary( ImportStatus.ERROR ).incrementIgnored();
            importSummary.setConflicts( importConflicts );

            return importSummary;
        }

        Program program = getProgram( importOptions.getIdSchemes(), enrollment.getProgram() );

        if ( !program.isRegistration() )
        {
            String descMsg = "Provided program " + program.getUid() +
                " is a program without registration. An enrollment cannot be created into program without registration.";

            return new ImportSummary( ImportStatus.ERROR, descMsg ).incrementIgnored();
        }

        programInstance.setProgram( program );

        if ( enrollment.getIncidentDate() != null )
        {
            programInstance.setIncidentDate( enrollment.getIncidentDate() );
        }

        if ( enrollment.getEnrollmentDate() != null )
        {
            programInstance.setEnrollmentDate( enrollment.getEnrollmentDate() );
        }

        if ( enrollment.getOrgUnit() != null )
        {
            OrganisationUnit organisationUnit = getOrganisationUnit( importOptions.getIdSchemes(), enrollment.getOrgUnit() );
            programInstance.setOrganisationUnit( organisationUnit );
        }

        programInstance.setFollowup( enrollment.getFollowup() );

        if ( program.getDisplayIncidentDate() && programInstance.getIncidentDate() == null )
        {
            return new ImportSummary( ImportStatus.ERROR, "DisplayIncidentDate is true but IncidentDate is null" ).incrementIgnored();
        }

        updateFeatureType( program, enrollment, programInstance );

        if ( EnrollmentStatus.fromProgramStatus( programInstance.getStatus() ) != enrollment.getStatus() )
        {
            if ( EnrollmentStatus.CANCELLED == enrollment.getStatus() )
            {
                programInstanceService.cancelProgramInstanceStatus( programInstance );
            }
            else if ( EnrollmentStatus.COMPLETED == enrollment.getStatus() )
            {
                programInstanceService.completeProgramInstanceStatus( programInstance );
            }
            else if ( EnrollmentStatus.ACTIVE == enrollment.getStatus() )
            {
                programInstanceService.incompleteProgramInstanceStatus( programInstance );
            }
        }

        updateAttributeValues( enrollment, importOptions );
        updateDateFields( enrollment, programInstance );

        programInstanceService.updateProgramInstance( programInstance );
        teiService.updateTrackedEntityInstance( programInstance.getEntityInstance() );

        saveTrackedEntityComment( programInstance, enrollment, importOptions.getUser() != null ? importOptions.getUser().getUsername() : "[Unknown]" );

        ImportSummary importSummary = new ImportSummary( enrollment.getEnrollment() ).incrementUpdated();
        importSummary.setReference( enrollment.getEnrollment() );

        importSummary.setEvents( handleEvents( enrollment, programInstance, importOptions ) );

        return importSummary;
    }

    @Override
    public ImportSummary updateEnrollmentForNote( Enrollment enrollment )
    {
        if ( enrollment == null || enrollment.getEnrollment() == null )
        {
            return new ImportSummary( ImportStatus.ERROR, "No enrollment or enrollment ID was supplied" ).incrementIgnored();
        }

        ImportSummary importSummary = new ImportSummary( enrollment.getEnrollment() );

        ProgramInstance programInstance = programInstanceService.getProgramInstance( enrollment.getEnrollment() );

        if ( programInstance == null )
        {
            return new ImportSummary( ImportStatus.ERROR, "Enrollment ID was not valid." ).incrementIgnored();
        }

        saveTrackedEntityComment( programInstance, enrollment, currentUserService.getCurrentUsername() );

        importSummary.setReference( enrollment.getEnrollment() );
        importSummary.getImportCount().incrementUpdated();

        return importSummary;
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @Override
    public ImportSummary deleteEnrollment( String uid )
    {
        return deleteEnrollment( uid, null, null );
    }

    private ImportSummary deleteEnrollment( String uid, Enrollment enrollment, ImportOptions importOptions )
    {
        ImportSummary importSummary = new ImportSummary();
        importOptions = updateImportOptions( importOptions );

        boolean existsEnrollment = programInstanceService.programInstanceExists( uid );

        if ( existsEnrollment )
        {
            ProgramInstance programInstance = programInstanceService.getProgramInstance( uid );

            if ( enrollment != null )
            {
                importSummary.setReference( uid );
                importSummary.setEvents( handleEvents( enrollment, programInstance, importOptions ) );
            }

            if ( importOptions.getUser() != null )
            {
                List<ImportConflict> importConflicts = isAllowedToDelete( importOptions.getUser(), programInstance );

                if ( !importConflicts.isEmpty() )
                {
                    importSummary.setStatus( ImportStatus.ERROR );
                    importSummary.setReference( programInstance.getUid() );
                    importSummary.getConflicts().addAll( importConflicts );
                    importSummary.incrementIgnored();
                    return importSummary;
                }
            }

            programInstanceService.deleteProgramInstance( programInstance );
            teiService.updateTrackedEntityInstance( programInstance.getEntityInstance() );

            importSummary.setStatus( ImportStatus.SUCCESS );
            importSummary.setDescription( "Deletion of enrollment " + uid + " was successful" );

            return importSummary.incrementDeleted();
        }
        else
        {
            //If I am here, it means that the item is either already deleted or it is not present in the system at all.
            importSummary.setStatus( ImportStatus.SUCCESS );
            importSummary.setDescription( "Enrollment " + uid + " cannot be deleted as it is not present in the system" );
            return importSummary.incrementIgnored();
        }
    }

    @Override
    public ImportSummaries deleteEnrollments( List<Enrollment> enrollments, ImportOptions importOptions, boolean clearSession )
    {
        ImportSummaries importSummaries = new ImportSummaries();
        importOptions = updateImportOptions( importOptions );
        int counter = 0;

        for ( Enrollment enrollment : enrollments )
        {
            importSummaries.addImportSummary( deleteEnrollment( enrollment.getEnrollment(), enrollment, importOptions ) );

            if ( clearSession && counter % FLUSH_FREQUENCY == 0 )
            {
                clearSession();
            }

            counter++;
        }

        return importSummaries;
    }

    @Override
    public void cancelEnrollment( String uid )
    {
        ProgramInstance programInstance = programInstanceService.getProgramInstance( uid );
        programInstanceService.cancelProgramInstanceStatus( programInstance );
        teiService.updateTrackedEntityInstance( programInstance.getEntityInstance() );
    }

    @Override
    public void completeEnrollment( String uid )
    {
        ProgramInstance programInstance = programInstanceService.getProgramInstance( uid );
        programInstanceService.completeProgramInstanceStatus( programInstance );
        teiService.updateTrackedEntityInstance( programInstance.getEntityInstance() );
    }

    @Override
    public void incompleteEnrollment( String uid )
    {
        ProgramInstance programInstance = programInstanceService.getProgramInstance( uid );
        programInstanceService.incompleteProgramInstanceStatus( programInstance );
        teiService.updateTrackedEntityInstance( programInstance.getEntityInstance() );
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private ImportSummaries handleEvents( Enrollment enrollment, ProgramInstance programInstance, ImportOptions importOptions )
    {
        List<Event> create = new ArrayList<>();
        List<Event> update = new ArrayList<>();
        List<String> delete = new ArrayList<>();

        for ( Event event : enrollment.getEvents() )
        {
            event.setEnrollment( enrollment.getEnrollment() );
            event.setProgram( programInstance.getProgram().getUid() );
            event.setTrackedEntityInstance( enrollment.getTrackedEntityInstance() );

            if ( importOptions.getImportStrategy().isSync() && event.isDeleted() )
            {
                delete.add( event.getEvent() );
            }
            else if ( !programStageInstanceService.programStageInstanceExists( event.getEvent() ) )
            {
                create.add( event );
            }
            else
            {
                update.add( event );
            }
        }

        ImportSummaries importSummaries = new ImportSummaries();
        importSummaries.addImportSummaries( eventService.addEvents( create, importOptions, false ) );
        importSummaries.addImportSummaries( eventService.updateEvents( update, importOptions, false, false ) );
        importSummaries.addImportSummaries( eventService.deleteEvents( delete, false ) );

        return importSummaries;
    }

    private void prepareCaches( List<Enrollment> enrollments, User user )
    {
        Collection<String> orgUnits = enrollments.stream().map( Enrollment::getOrgUnit ).collect( Collectors.toSet() );

        if ( !orgUnits.isEmpty() )
        {
            Query query = Query.from( schemaService.getDynamicSchema( OrganisationUnit.class ) );
            query.setUser( user );
            query.add( Restrictions.in( "id", orgUnits ) );
            queryService.query( query ).forEach( ou -> organisationUnitCache.put( ou.getUid(), (OrganisationUnit) ou ) );
        }

        Collection<String> programs = enrollments.stream().map( Enrollment::getProgram ).collect( Collectors.toSet() );

        if ( !programs.isEmpty() )
        {
            Query query = Query.from( schemaService.getDynamicSchema( Program.class ) );
            query.setUser( user );
            query.add( Restrictions.in( "id", programs ) );
            queryService.query( query ).forEach( pr -> programCache.put( pr.getUid(), (Program) pr ) );
        }

        Collection<String> trackedEntityAttributes = new HashSet<>();
        enrollments.forEach( e -> e.getAttributes().forEach( at -> trackedEntityAttributes.add( at.getAttribute() ) ) );

        if ( !trackedEntityAttributes.isEmpty() )
        {
            Query query = Query.from( schemaService.getDynamicSchema( TrackedEntityAttribute.class ) );
            query.setUser( user );
            query.add( Restrictions.in( "id", trackedEntityAttributes ) );
            queryService.query( query ).forEach( tea -> trackedEntityAttributeCache.put( tea.getUid(), (TrackedEntityAttribute) tea ) );
        }
    }

    private void updateFeatureType( Program program, Enrollment enrollment, ProgramInstance programInstance )
    {
        if ( program.getFeatureType() != null )
        {
            if ( enrollment.getGeometry() != null && !program.getFeatureType().equals( FeatureType.NONE ) )
            {
                programInstance.setGeometry( enrollment.getGeometry() );
            }
            else if ( program.getFeatureType().equals( FeatureType.POINT ) && enrollment.getCoordinate() != null && enrollment.getCoordinate().isValid() )
            {
                GeometryFactory gf = new GeometryFactory();
                com.vividsolutions.jts.geom.Coordinate co = new com.vividsolutions.jts.geom.Coordinate( enrollment.getCoordinate().getLongitude(), enrollment.getCoordinate().getLatitude() );
                programInstance.setGeometry( gf.createPoint( co ) );
            }
            else
            {
                programInstance.setGeometry( null );
            }
        }
    }

    private boolean doValidationOfMandatoryAttributes( User user )
    {
        return user == null || !user.isAuthorized( Authorities.F_IGNORE_TRACKER_REQUIRED_VALUE_VALIDATION.getAuthority() );
    }

    private List<ImportConflict> checkAttributes( Enrollment enrollment, ImportOptions importOptions )
    {
        List<ImportConflict> importConflicts = new ArrayList<>();

        Program program = getProgram( importOptions.getIdSchemes(), enrollment.getProgram() );
        org.hisp.dhis.trackedentity.TrackedEntityInstance trackedEntityInstance = teiService.getTrackedEntityInstance(
            enrollment.getTrackedEntityInstance() );

        Map<TrackedEntityAttribute, Boolean> mandatoryMap = Maps.newHashMap();
        Map<String, String> attributeValueMap = Maps.newHashMap();

        for ( ProgramTrackedEntityAttribute programTrackedEntityAttribute : program.getProgramAttributes() )
        {
            mandatoryMap.put( programTrackedEntityAttribute.getAttribute(), programTrackedEntityAttribute.isMandatory() );
        }

        // ignore attributes which do not belong to this program
        trackedEntityInstance.getTrackedEntityAttributeValues().stream().
            filter( value -> mandatoryMap.containsKey( value.getAttribute() ) ).
            forEach( value -> attributeValueMap.put( value.getAttribute().getUid(), value.getValue() ) );

        for ( Attribute attribute : enrollment.getAttributes() )
        {
            attributeValueMap.put( attribute.getAttribute(), attribute.getValue() );
            importConflicts.addAll( validateAttributeType( attribute, importOptions ) );
        }

        TrackedEntityInstance instance = trackedEntityInstanceService.getTrackedEntityInstance( enrollment.getTrackedEntityInstance() );

        for ( TrackedEntityAttribute trackedEntityAttribute : mandatoryMap.keySet() )
        {
            Boolean mandatory = mandatoryMap.get( trackedEntityAttribute );

            if ( mandatory && doValidationOfMandatoryAttributes( importOptions.getUser() ) && !attributeValueMap.containsKey( trackedEntityAttribute.getUid() ) )
            {
                importConflicts.add( new ImportConflict( "Attribute.attribute", "Missing mandatory attribute "
                    + trackedEntityAttribute.getUid() ) );
                continue;
            }

            if ( trackedEntityAttribute.isUnique() )
            {
                OrganisationUnit organisationUnit = manager.get( OrganisationUnit.class, instance.getOrgUnit() );

                importConflicts.addAll( checkScope( trackedEntityInstance, trackedEntityAttribute,
                    attributeValueMap.get( trackedEntityAttribute.getUid() ), organisationUnit, program ) );
            }

            attributeValueMap.remove( trackedEntityAttribute.getUid() );
        }

        if ( !attributeValueMap.isEmpty() )
        {
            importConflicts.add( new ImportConflict( "Attribute.attribute",
                "Only program attributes is allowed for enrollment " + attributeValueMap ) );
        }

        if ( !program.getSelectEnrollmentDatesInFuture() )
        {
            if ( Objects.nonNull( enrollment.getEnrollmentDate() ) && enrollment.getEnrollmentDate().after( new Date() ) )
            {
                importConflicts.add( new ImportConflict( "Enrollment.date", "Enrollment Date can't be future date :" + enrollment
                    .getEnrollmentDate() ) );
            }
        }

        if ( !program.getSelectIncidentDatesInFuture() )
        {
            if ( Objects.nonNull( enrollment.getIncidentDate() ) && enrollment.getIncidentDate().after( new Date() ) )
            {
                importConflicts.add( new ImportConflict( "Enrollment.incidentDate", "Incident Date can't be future date :" + enrollment
                    .getIncidentDate() ) );
            }
        }

        return importConflicts;
    }

    private List<ImportConflict> checkScope( org.hisp.dhis.trackedentity.TrackedEntityInstance trackedEntityInstance,
        TrackedEntityAttribute trackedEntityAttribute, String value, OrganisationUnit organisationUnit, Program program )
    {
        List<ImportConflict> importConflicts = new ArrayList<>();

        if ( trackedEntityAttribute == null || value == null )
        {
            return importConflicts;
        }

        String errorMessage = trackedEntityAttributeService.validateScope( trackedEntityAttribute, value, trackedEntityInstance,
            organisationUnit, program );

        if ( errorMessage != null )
        {
            importConflicts.add( new ImportConflict( "Attribute.value", errorMessage ) );
        }

        return importConflicts;
    }

    private void updateAttributeValues( Enrollment enrollment, ImportOptions importOptions )
    {
        org.hisp.dhis.trackedentity.TrackedEntityInstance trackedEntityInstance = teiService.
            getTrackedEntityInstance( enrollment.getTrackedEntityInstance() );
        Map<String, String> attributeValueMap = Maps.newHashMap();

        for ( Attribute attribute : enrollment.getAttributes() )
        {
            attributeValueMap.put( attribute.getAttribute(), attribute.getValue() );
        }

        trackedEntityInstance.getTrackedEntityAttributeValues().stream().
            filter( value -> attributeValueMap.containsKey( value.getAttribute().getUid() ) ).
            forEach( value ->
            {
                String newValue = attributeValueMap.get( value.getAttribute().getUid() );
                value.setValue( newValue );

                trackedEntityAttributeValueService.updateTrackedEntityAttributeValue( value );

                attributeValueMap.remove( value.getAttribute().getUid() );
            } );

        for ( String key : attributeValueMap.keySet() )
        {
            TrackedEntityAttribute attribute = getTrackedEntityAttribute( importOptions.getIdSchemes(), key );

            if ( attribute != null )
            {
                TrackedEntityAttributeValue value = new TrackedEntityAttributeValue();
                value.setValue( attributeValueMap.get( key ) );
                value.setAttribute( attribute );

                trackedEntityAttributeValueService.addTrackedEntityAttributeValue( value );
                trackedEntityInstance.addAttributeValue( value );
            }
        }
    }

    private org.hisp.dhis.trackedentity.TrackedEntityInstance getTrackedEntityInstance( String teiUID )
    {
        org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance = teiService.
            getTrackedEntityInstance( teiUID );

        if ( entityInstance == null )
        {
            throw new InvalidIdentifierReferenceException( "TrackedEntityInstance does not exist." );
        }

        return entityInstance;
    }

    private List<ImportConflict> validateAttributeType( Attribute attribute, ImportOptions importOptions )
    {
        List<ImportConflict> importConflicts = Lists.newArrayList();
        TrackedEntityAttribute teAttribute = getTrackedEntityAttribute( importOptions.getIdSchemes(), attribute.getAttribute() );

        if ( teAttribute == null )
        {
            importConflicts.add( new ImportConflict( "Attribute.attribute", "Does not point to a valid attribute." ) );
            return importConflicts;
        }

        String errorMessage = trackedEntityAttributeService.validateValueType( teAttribute, attribute.getValue() );

        if ( errorMessage != null )
        {
            importConflicts.add( new ImportConflict( "Attribute.value", errorMessage ) );
        }

        return importConflicts;
    }

    private void saveTrackedEntityComment( ProgramInstance programInstance, Enrollment enrollment, String storedBy )
    {
        for ( Note note : enrollment.getNotes() )
        {
            String noteUid = CodeGenerator.isValidUid( note.getNote() ) ? note.getNote() : CodeGenerator.generateUid();

            if ( !commentService.trackedEntityCommentExists( noteUid ) && !StringUtils.isEmpty( note.getValue() ) )
            {
                TrackedEntityComment comment = new TrackedEntityComment();
                comment.setUid( noteUid );
                comment.setCommentText( note.getValue() );
                comment.setCreator( StringUtils.isEmpty( note.getStoredBy() ) ? User.getSafeUsername( storedBy ) : note.getStoredBy() );

                Date created = DateUtils.parseDate( note.getStoredDate() );
                comment.setCreated( created );

                commentService.addTrackedEntityComment( comment );

                programInstance.getComments().add( comment );

                programInstanceService.updateProgramInstance( programInstance );
                teiService.updateTrackedEntityInstance( programInstance.getEntityInstance() );
            }
        }
    }

    private OrganisationUnit getOrganisationUnit( IdSchemes idSchemes, String id )
    {
        return organisationUnitCache.get( id, new IdentifiableObjectCallable<>( manager, OrganisationUnit.class, idSchemes.getOrgUnitIdScheme(), id ) );
    }

    private Program getProgram( IdSchemes idSchemes, String id )
    {
        return programCache.get( id, new IdentifiableObjectCallable<>( manager, Program.class, idSchemes.getProgramIdScheme(), id ) );
    }

    private TrackedEntityAttribute getTrackedEntityAttribute( IdSchemes idSchemes, String id )
    {
        return trackedEntityAttributeCache.get( id, new IdentifiableObjectCallable<>( manager, TrackedEntityAttribute.class, idSchemes.getTrackedEntityAttributeIdScheme(), id ) );
    }

    private void clearSession()
    {
        organisationUnitCache.clear();
        programCache.clear();
        trackedEntityAttributeCache.clear();

        dbmsManager.clearSession();
    }

    private void updateDateFields( Enrollment enrollment, ProgramInstance programInstance )
    {
        programInstance.setAutoFields();

        Date createdAtClient = DateUtils.parseDate( enrollment.getCreatedAtClient() );

        if ( createdAtClient != null )
        {
            programInstance.setCreatedAtClient( createdAtClient );
        }

        String lastUpdatedAtClient = enrollment.getLastUpdatedAtClient();

        if ( lastUpdatedAtClient != null )
        {
            programInstance.setLastUpdatedAtClient( DateUtils.parseDate( lastUpdatedAtClient ) );
        }
    }

    protected ImportOptions updateImportOptions( ImportOptions importOptions )
    {
        if ( importOptions == null )
        {
            importOptions = new ImportOptions();
        }

        if ( importOptions.getUser() == null )
        {
            importOptions.setUser( currentUserService.getCurrentUser() );
        }

        return importOptions;
    }

    protected void reloadUser( ImportOptions importOptions )
    {
        if ( importOptions == null || importOptions.getUser() == null )
        {
            return;
        }

        importOptions.setUser( userService.getUser( importOptions.getUser().getUid() ) );
    }

    private List<ImportConflict> isAllowedToDelete( User user, ProgramInstance pi )
    {
        List<ImportConflict> importConflicts = new ArrayList<>();

        Set<ProgramStageInstance> notDeletedProgramStageInstances = pi.getProgramStageInstances().stream()
            .filter( psi -> !psi.isDeleted() )
            .collect( Collectors.toSet() );

        if ( !notDeletedProgramStageInstances.isEmpty() && !user.isAuthorized( Authorities.F_ENROLLMENT_CASCADE_DELETE.getAuthority() ) )
        {
            importConflicts.add( new ImportConflict( pi.getUid(), "Enrollment " + pi.getUid() + " cannot be deleted as it has associated events and user does not have authority: " + Authorities.F_ENROLLMENT_CASCADE_DELETE.getAuthority() ) );
        }

        List<String> errors = trackerAccessManager.canWrite( user, pi );

        if ( !errors.isEmpty() )
        {
            errors.forEach( error -> importConflicts.add( new ImportConflict( pi.getUid(), error ) ) );
        }

        return importConflicts;
    }
}
