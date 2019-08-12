package org.hisp.dhis.webapi.controller;

/*
 * Copyright (c) 2004-2019, University of Oslo
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
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.hisp.dhis.appstore2.AppStoreService;
import org.hisp.dhis.appstore2.WebApp;
import org.hisp.dhis.common.DhisApiVersion;
import org.hisp.dhis.dxf2.webmessage.WebMessageException;
import org.hisp.dhis.dxf2.webmessage.WebMessageUtils;
import org.hisp.dhis.appmanager.AppStatus;
import org.hisp.dhis.i18n.I18nManager;
import org.hisp.dhis.webapi.mvc.annotation.ApiVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Created by zubair@dhis2.org on 07.09.17.
 */
@Controller
@RequestMapping( AppStoreController.RESOURCE_PATH )
@ApiVersion( { DhisApiVersion.DEFAULT, DhisApiVersion.V28, DhisApiVersion.V29, DhisApiVersion.V30, DhisApiVersion.V31, DhisApiVersion.V32, DhisApiVersion.V33 } )
public class AppStoreController
{
    public static final String RESOURCE_PATH = "/appStore";

    @Autowired
    private AppStoreService appStoreService;
    
    @Autowired
    private I18nManager i18nManager;


    @RequestMapping( method = RequestMethod.GET, produces = "application/json" )
    public @ResponseBody List<WebApp> listAppStore( HttpServletResponse response )
        throws IOException
    {
        return appStoreService.getAppStore();
    }

    @RequestMapping( value = "/{versionId}", method = RequestMethod.POST )
    @PreAuthorize( "hasRole('ALL') or hasRole('M_dhis-web-maintenance-appmanager')" )
    @ResponseStatus( HttpStatus.NO_CONTENT )
    public void installAppFromStore( @PathVariable String versionId ) throws WebMessageException
    {
        AppStatus status = appStoreService.installAppFromAppStore( versionId );
        if ( !status.ok() )
        {
            String message = i18nManager.getI18n().getString( status.getMessage() );

            throw new WebMessageException( WebMessageUtils.conflict( message ) );
        }
    }
}
