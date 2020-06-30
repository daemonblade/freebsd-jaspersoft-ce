/*******************************************************************************
 * Copyright (C) 2010 - 2016. TIBCO Software Inc. 
 * All Rights Reserved. Confidential & Proprietary.
 ******************************************************************************/
package com.jaspersoft.studio.widgets.framework.ui;

import java.math.BigDecimal;
import java.text.DecimalFormat;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.jaspersoft.studio.swt.widgets.NumericText;
import com.jaspersoft.studio.utils.ValidatedDecimalFormat;
import com.jaspersoft.studio.utils.jasper.JasperReportsConfiguration;
import com.jaspersoft.studio.widgets.framework.IWItemProperty;
import com.jaspersoft.studio.widgets.framework.model.WidgetPropertyDescriptor;
import com.jaspersoft.studio.widgets.framework.model.WidgetsDescriptor;
import com.jaspersoft.studio.widgets.framework.ui.widget.FallbackNumericText;

import net.sf.jasperreports.eclipse.util.Misc;

public class BigDecimalPropertyDescription extends NumberPropertyDescription<BigDecimal> {

	public BigDecimalPropertyDescription() {
	}

	public BigDecimalPropertyDescription(String name, String label, String description, boolean mandatory,
			BigDecimal defaultValue, BigDecimal min, BigDecimal max) {
		super(name, label, description, mandatory, defaultValue, min, max);
	}

	public BigDecimalPropertyDescription(String name, String label, String description, boolean mandatory,
			BigDecimal min, BigDecimal max) {
		super(name, label, description, mandatory, min, max);
	}

	@Override
	public Class<? extends Number> getType() {
		if (defaultValue != null)
			return defaultValue.getClass();
		return BigDecimal.class;
	}

	@Override
	public BigDecimalPropertyDescription clone() {
		BigDecimalPropertyDescription result = new BigDecimalPropertyDescription();
		result.defaultValue = defaultValue;
		result.description = description;
		result.jConfig = jConfig;
		result.label = label;
		result.mandatory = mandatory;
		result.name = name;
		result.readOnly = readOnly;
		result.min = min;
		result.max = max;
		result.fallbackValue = fallbackValue;
		return result;
	}

	@Override
	public BigDecimalPropertyDescription getInstance(WidgetsDescriptor cd, WidgetPropertyDescriptor cpd,
			JasperReportsConfiguration jConfig) {
		BigDecimal min = null;
		BigDecimal max = null;
		BigDecimal def = null;
		BigDecimal fallBack = null;

		// Setup the minimum
		if (cpd.getMin() != null) {
			min = new BigDecimal(cpd.getMin());
		}

		// Setup the maximum
		if (cpd.getMax() != null) {
			max = new BigDecimal(cpd.getMax());
		}
		// Setup the default value
		if (cpd.getDefaultValue() != null && !cpd.getDefaultValue().isEmpty()) {
			def = new BigDecimal(cpd.getDefaultValue());
		}

		// Setup the fallback value
		if (cpd.getFallbackValue() != null && !cpd.getFallbackValue().isEmpty()) {
			fallBack = new BigDecimal(cpd.getFallbackValue());
		}
		BigDecimalPropertyDescription intDesc = new BigDecimalPropertyDescription(cpd.getName(),
				cd.getLocalizedString(cpd.getLabel()), cd.getLocalizedString(cpd.getDescription()), cpd.isMandatory(),
				def, min, max);
		intDesc.setReadOnly(cpd.isReadOnly());
		intDesc.setFallbackValue(fallBack);
		return intDesc;
	}

	@Override
	protected FallbackNumericText createSimpleEditor(Composite parent) {
		FallbackNumericText text = new FallbackNumericText(parent, SWT.BORDER, 0, 20);
		text.setRemoveTrailZeroes(true);
		BigDecimal max = getMax();
		BigDecimal min = getMin();
		text.setMaximum(max != null ? max.doubleValue() : null);
		text.setMinimum(min != null ? min.doubleValue() : null);
		return text;
	}

	@Override
	public void handleEdit(Control txt, IWItemProperty wiProp) {
		if (wiProp == null)
			return;
		if (txt instanceof NumericText) {
			NumericText widget = (NumericText) txt;
			BigDecimal bigValue = widget.getValueAsBigDecimal();
			String tvalue = bigValue != null ? bigValue.toString() : null;
			if (tvalue != null && tvalue.isEmpty())
				tvalue = null;
			wiProp.setValue(tvalue, null);
			widget.setToolTipText(getToolTip(wiProp, ((NumericText) txt).getText()));
		} else
			super.handleEdit(txt, wiProp);
	}

	@Override
	protected BigDecimal convertValue(String v) throws NumberFormatException {
		if (v == null || v.isEmpty())
			return null;
		try {
			BigDecimal parsedBigDecimal = new BigDecimal(v);
			return parsedBigDecimal;
		} catch (Exception ex) {
			throw new NumberFormatException();
		}
	}

	@Override
	public String getToolTip() {
		String tt = Misc.nvl(getDescription());
		tt += "\n" + (isMandatory() ? "Mandatory" : "Optional");
		DecimalFormat formatter = new DecimalFormat("0.#######", ValidatedDecimalFormat.SYMBOLS);
		if (!Misc.isNullOrEmpty(getDefaultValueString()))
			tt += "\nDefault: " + formatter.format(getDefaultValue());
		if (getMin() != null || getMax() != null) {

			if (getMin() != null)
				tt += "\nmin: " + formatter.format(getMin());

			if (getMax() != null)
				tt += "\nmax: " + formatter.format(getMax());
		}
		return tt;
	}
}
