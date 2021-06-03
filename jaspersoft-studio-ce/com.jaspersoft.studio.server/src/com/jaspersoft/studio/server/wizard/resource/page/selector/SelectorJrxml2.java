/*******************************************************************************
 * Copyright (C) 2010 - 2016. TIBCO Software Inc. 
 * All Rights Reserved. Confidential & Proprietary.
 ******************************************************************************/
package com.jaspersoft.studio.server.wizard.resource.page.selector;

import com.jaspersoft.jasperserver.api.metadata.xml.domain.impl.ResourceDescriptor;
import com.jaspersoft.jasperserver.dto.resources.ClientFile.FileType;
import com.jaspersoft.jasperserver.dto.resources.ResourceMediaType;
import com.jaspersoft.studio.server.model.AMResource;
import com.jaspersoft.studio.server.model.MJrxml;
import com.jaspersoft.studio.server.preferences.JRSPreferencesPage;
import com.jaspersoft.studio.utils.jasper.JasperReportsConfiguration;

public class SelectorJrxml2 extends ASelector {
	@Override
	protected ResourceDescriptor createLocal(AMResource res) {
		ResourceDescriptor rd = MJrxml.createDescriptor(res);
		rd.setName(MJrxml.getIconDescriptor().getTitle());
		rd.setLabel(rd.getName());
		return rd;
	}

	@Override
	protected void setupResource(ResourceDescriptor rd) {
		rd.setMainReport(true);
		JasperReportsConfiguration jConfig = res.getJasperConfiguration();
		if (jConfig == null) {
			jConfig = JasperReportsConfiguration.getDefaultInstance();
			res.setJasperConfiguration(jConfig);
		}
		rd.setName(JRSPreferencesPage.getDefaultMainReportName(jConfig));
		rd.setLabel(JRSPreferencesPage.getDefaultMainReportLabel(jConfig));
	}

	@Override
	protected boolean isResCompatible(AMResource r) {
		return r instanceof MJrxml && !r.getValue().getParentFolder().endsWith("_files");
	}

	protected ResourceDescriptor getResourceDescriptor(ResourceDescriptor ru) {
		for (Object obj : ru.getChildren()) {
			ResourceDescriptor r = (ResourceDescriptor) obj;
			ResourceDescriptor tmp = checkReference(r);
			if (tmp != null)
				r = tmp;
			if (r.getIsReference() && r.getReferenceType() != null
					&& r.getReferenceType().equals(ResourceDescriptor.TYPE_JRXML))
				return r;
			if (r.getWsType().equals(ResourceDescriptor.TYPE_JRXML) && r.isMainReport())
				return r;
		}
		return null;
	}

	@Override
	protected String[] getIncludeTypes() {
		boolean sv = res.getWsClient().getServerInfo().getVersion().compareTo("5.5") >= 0;
		return new String[] { sv ? FileType.jrxml.name() : ResourceMediaType.FILE_CLIENT_TYPE };
	}

	@Override
	protected String[] getExcludeTypes() {
		return null;
	}

}
