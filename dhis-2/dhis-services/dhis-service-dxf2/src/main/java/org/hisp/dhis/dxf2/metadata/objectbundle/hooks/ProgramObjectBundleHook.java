package org.hisp.dhis.dxf2.metadata.objectbundle.hooks;

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

import org.hisp.dhis.attribute.Attribute;
import org.hisp.dhis.common.IdentifiableObject;
import org.hisp.dhis.dxf2.metadata.objectbundle.ObjectBundle;
import org.hisp.dhis.feedback.ErrorCode;
import org.hisp.dhis.feedback.ErrorReport;
import org.hisp.dhis.preheat.PreheatIdentifier;
import org.hisp.dhis.program.*;
import org.hisp.dhis.security.acl.AccessStringHelper;
import org.hisp.dhis.security.acl.AclService;
import org.hisp.dhis.trackedentity.TrackedEntityAttribute;
import org.hisp.dhis.trackedentity.TrackedEntityAttributeService;
import org.hisp.dhis.trackedentity.TrackedEntityAttributeStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Morten Olav Hansen <mortenoh@gmail.com>
 */
public class ProgramObjectBundleHook extends AbstractObjectBundleHook
{

    private final ProgramService programService;

    private final ProgramStageService programStageService;

    private final AclService aclService;

    private final TrackedEntityAttributeStore attributeStore;

    public ProgramObjectBundleHook( ProgramService programService, ProgramStageService programStageService, AclService aclService, TrackedEntityAttributeStore attributeStore )
    {
        this.programService = programService;
        this.programStageService = programStageService;
        this.aclService = aclService;
        this.attributeStore = attributeStore;
    }

    @Override
    public <T extends IdentifiableObject> List<ErrorReport> validate( T object, ObjectBundle bundle )
    {
        if ( !Program.class.isInstance( object ) )
        {
            return new ArrayList<>();
        }
        
        Program program = ( Program ) object;

        return validateAttributeSecurity( program, bundle );
    }

    @Override
    public void postCreate( IdentifiableObject object, ObjectBundle bundle )
    {
        if ( !Program.class.isInstance( object ) )
        {
            return;
        }

        Program program = ( Program ) object;
        syncSharingForEventProgram( program );
        updateProgramStage( program );
    }

    @Override
    public void postUpdate( IdentifiableObject object, ObjectBundle bundle )
    {
        if ( !Program.class.isInstance( object ) )
        {
            return;
        }

        syncSharingForEventProgram( (Program) object );
    }

    private void syncSharingForEventProgram( Program program )
    {
        if ( ProgramType.WITH_REGISTRATION == program.getProgramType()
            || program.getProgramStages().isEmpty() )
        {
            return;
        }

        ProgramStage programStage = program.getProgramStages().iterator().next();
        AccessStringHelper.copySharing( program, programStage );

        programStage.setUser( program.getUser() );
        sessionFactory.getCurrentSession().update( programStage );
    }

    private void updateProgramStage( Program program )
    {
        if ( program.getProgramStages().isEmpty() )
        {
            return;
        }

        program.getProgramStages().forEach( ps -> {

            if ( Objects.isNull( ps.getProgram() ) )
            {
                ps.setProgram( program );
            }

            programStageService.saveProgramStage( ps );
        });

        programService.updateProgram( program );
    }

    private List<ErrorReport> validateAttributeSecurity( Program program, ObjectBundle bundle )
    {
        if ( program.getProgramAttributes().isEmpty() )
        {
            return new ArrayList<>();
        }

        List<ErrorReport> errorReports = new ArrayList<>();

        PreheatIdentifier identifier = bundle.getPreheatIdentifier();

        program.getProgramAttributes().forEach( attr ->
            {
                TrackedEntityAttribute attribute = bundle.getPreheat().get( identifier, TrackedEntityAttribute.class,
                    attr.getAttribute().getUid() );

                if ( attribute == null )
                {
                    attribute = attributeStore.getByUidNoAcl( attr.getAttribute().getUid() );
                    bundle.getPreheat().put( identifier, attribute );
                }

                if ( ( bundle.getImportMode().isUpdate() || bundle.getImportMode().isCreateAndUpdate() )
                        && !aclService.canRead( bundle.getUser(), attribute ) )
                {
                    errorReports.add( new ErrorReport( TrackedEntityAttribute.class, ErrorCode.E3012, identifier.getIdentifiersWithName( bundle.getUser() ),
                            identifier.getIdentifiersWithName( attribute ) ) );
                }
            });

        return errorReports;
    }
}
