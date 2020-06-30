/*******************************************************************************
 * Copyright (C) 2010 - 2016. TIBCO Software Inc. 
 * All Rights Reserved. Confidential & Proprietary.
 ******************************************************************************/
package com.jaspersoft.studio.data.jrdsprovider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.jaspersoft.studio.data.fields.IFieldsProvider;
import com.jaspersoft.studio.utils.jasper.JasperReportsConfiguration;

import net.sf.jasperreports.data.DataAdapterService;
import net.sf.jasperreports.data.provider.DataSourceProviderDataAdapterService;
import net.sf.jasperreports.eclipse.builder.JasperReportCompiler;
import net.sf.jasperreports.eclipse.util.StringUtils;
import net.sf.jasperreports.engine.JRDataSourceProvider;
import net.sf.jasperreports.engine.JRDataset;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JasperDesign;

public class JRDSProviderFieldsProvider implements IFieldsProvider {

	private JRDataSourceProvider jrdsp;

	public void setProvider(JRDataSourceProvider jrdsp) {
		this.jrdsp = jrdsp;
	}

	public boolean supportsGetFieldsOperation(JasperReportsConfiguration jConfig, JRDataset jDataset) {
		if (jrdsp != null)
			return jrdsp.supportsGetFieldsOperation();
		return false;
	}

	public List<JRDesignField> getFields(DataAdapterService con, JasperReportsConfiguration jConfig,
			JRDataset reportDataset) throws JRException, UnsupportedOperationException {
		jrdsp = ((DataSourceProviderDataAdapterService) con).getProvider();
		if (jrdsp != null) {
			JasperReport jr = null;
			try {
				JasperDesign jd = jConfig.getJasperDesign();
				if (jd != null) {
					JasperReportCompiler compiler = new JasperReportCompiler();
					jr = compiler.compileReport(jConfig, jConfig.getJasperDesign(), new NullProgressMonitor());
				}
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			JRField[] aray = jrdsp.getFields(jr);
			if (aray != null) {
				List<JRDesignField> fields = new ArrayList<JRDesignField>();
				for (JRField f : aray) {
					if (f instanceof JRDesignField)
						fields.add((JRDesignField) f);
					else {
						JRDesignField jdf = new JRDesignField();
						jdf.setName(StringUtils.xmlEncode(f.getName(), null));
						jdf.setValueClassName(f.getValueClassName());
						jdf.setDescription(StringUtils.xmlEncode(f.getDescription(), null));
						fields.add(jdf);
					}
				}
				return fields;
			}
		}
		return new ArrayList<JRDesignField>();
	}
}
