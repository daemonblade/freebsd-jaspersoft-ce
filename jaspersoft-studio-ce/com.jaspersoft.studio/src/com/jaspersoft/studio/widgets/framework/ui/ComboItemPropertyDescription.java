/*******************************************************************************
 * Copyright (C) 2010 - 2016. TIBCO Software Inc. 
 * All Rights Reserved. Confidential & Proprietary.
 ******************************************************************************/
package com.jaspersoft.studio.widgets.framework.ui;

import org.eclipse.jface.util.Util;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;

import com.jaspersoft.studio.swt.widgets.CustomReadOnlyCombo;
import com.jaspersoft.studio.utils.ModelUtils;
import com.jaspersoft.studio.utils.jasper.JasperReportsConfiguration;
import com.jaspersoft.studio.widgets.framework.IWItemProperty;
import com.jaspersoft.studio.widgets.framework.manager.DoubleControlComposite;
import com.jaspersoft.studio.widgets.framework.model.WidgetPropertyDescriptor;
import com.jaspersoft.studio.widgets.framework.model.WidgetsDescriptor;

import net.sf.jasperreports.eclipse.util.Misc;

public class ComboItemPropertyDescription<T> extends AbstractExpressionPropertyDescription<T> {

	/**
	 * On MacOS seems the contextual menu is not opened on combo, this lister
	 * will force it to open when a right click is found
	 */
	protected static MouseAdapter macComboMenuOpener = new MouseAdapter() {

		@Override
		public void mouseUp(MouseEvent e) {
			if (e.button == 3 && ((Control) e.widget).getMenu() != null) {
				Menu menu = ((Control) e.widget).getMenu();
				if (menu != null && !menu.isDisposed() && !menu.isVisible()) {
					Point location = e.widget.getDisplay().getCursorLocation();
					menu.setLocation(location.x, location.y);
					menu.setVisible(true);
				}
			}
		}
	};

	/**
	 * Matrix of n*2 for n elements, the first column of the matrix is the key
	 * of the element, the second the label
	 */
	protected String[][] keyValues;

	public ComboItemPropertyDescription() {
		super();
	}

	public ComboItemPropertyDescription(String name, String label, String description, boolean mandatory,
			T defaultValue, String[] values) {
		super(name, label, description, mandatory, defaultValue);
		keyValues = convert2KeyValue(values);
	}

	public ComboItemPropertyDescription(String name, String label, String description, boolean mandatory,
			String[] values) {
		super(name, label, description, mandatory);
		keyValues = convert2KeyValue(values);
	}

	public ComboItemPropertyDescription(String name, String label, String description, boolean mandatory,
			T defaultValue, String[][] keyValues) {
		super(name, label, description, mandatory, defaultValue);
		this.keyValues = keyValues;
	}

	public static String[][] convert2KeyValue(String[] values) {
		String[][] kv = new String[values.length][2];
		for (int i = 0; i < values.length; i++) {
			kv[i][0] = values[i];
			kv[i][1] = values[i];
		}
		return kv;
	}

	protected String[] convert2Value(String[][] keyValues) {
		String[] v = new String[keyValues.length];
		for (int i = 0; i < keyValues.length; i++)
			v[i] = keyValues[i][1];
		return v;
	}

	@Override
	public void handleEdit(Control txt, IWItemProperty wProp) {
		super.handleEdit(txt, wProp);
		if (txt instanceof Combo) {
			Combo combo = (Combo) txt;
			int indx = combo.getSelectionIndex();
			String text = combo.getText();
			if (indx == -1 && text != null && !text.trim().isEmpty()) {
				// case where the value is typed directly
				wProp.setValue(text, null);
			} else {
				String tvalue = indx >= 0 && indx < keyValues.length ? keyValues[indx][0] : null;
				if (tvalue != null && tvalue.isEmpty())
					tvalue = null;
				wProp.setValue(tvalue, null);
			}
		}
	}

	protected Combo createComboControl(Composite parent) {
		CustomReadOnlyCombo result = new CustomReadOnlyCombo(parent);
		// MacOS fix, the combo on MacOS doesn't have a contextual menu, so we
		// need to handle this listener manually
		boolean handleComboListener = Util.isMac();
		if (handleComboListener) {
			result.addMouseListener(macComboMenuOpener);
		}
		return result;
	}

	@Override
	public Control createControl(final IWItemProperty wiProp, Composite parent) {
		DoubleControlComposite cmp = new DoubleControlComposite(parent, SWT.NONE);
		cmp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		lazyCreateExpressionControl(wiProp, cmp);

		final Combo simpleControl = createComboControl(cmp.getSecondContainer());
		cmp.getSecondContainer().setData(simpleControl);
		cmp.setSimpleControlToHighlight(simpleControl);

		GridData comboData = new GridData(GridData.FILL_HORIZONTAL);
		comboData.verticalAlignment = SWT.CENTER;
		comboData.grabExcessVerticalSpace = true;
		simpleControl.setLayoutData(comboData);

		simpleControl.setItems(convert2Value(keyValues));
		simpleControl.addModifyListener(e -> {
			if (wiProp.isRefresh())
				return;
			Point p = simpleControl.getSelection();
			handleEdit(simpleControl, wiProp);
			simpleControl.setSelection(p);
		});

		if (isReadOnly()) {
			simpleControl.setEnabled(false);
		} else {
			setupContextMenu(simpleControl, wiProp);
		}

		cmp.switchToSecondContainer();
		return cmp;
	}

	@Override
	public void update(Control c, IWItemProperty wip) {
		DoubleControlComposite cmp = (DoubleControlComposite) wip.getControl();
		boolean isFallback = false;
		if (wip.isExpressionMode()) {
			lazyCreateExpressionControl(wip, cmp);
			Text txt = (Text) cmp.getFirstContainer().getData();
			super.update(txt, wip);
			cmp.switchToFirstContainer();
			txt.setToolTipText(getToolTip(wip, txt.getText()));
		} else {
			Combo combo = (Combo) cmp.getSecondContainer().getData();
			String v = wip.getStaticValue();
			if (v == null && wip.getFallbackValue() != null) {
				v = wip.getFallbackValue().toString();
				isFallback = true;
			}
			String textualValue = Misc.nvl(v);
			for (String[] keyValuePairs : keyValues) {
				String key = keyValuePairs[0];
				String value = keyValuePairs[1];
				if (ModelUtils.safeEquals(key, v)) {
					textualValue = value;
					break;
				}
			}
			combo.setText(textualValue);
			combo.setToolTipText(getToolTip(wip, combo.getText()));
			changeFallbackForeground(isFallback, combo);
			cmp.switchToSecondContainer();
		}
	}

	@Override
	public ItemPropertyDescription<T> clone() {
		ComboItemPropertyDescription<T> result = new ComboItemPropertyDescription<>();
		result.defaultValue = defaultValue;
		result.description = description;
		result.jConfig = jConfig;
		result.label = label;
		result.mandatory = mandatory;
		result.name = name;
		result.keyValues = keyValues;
		result.fallbackValue = fallbackValue;
		return result;
	}

	@Override
	public ItemPropertyDescription<?> getInstance(WidgetsDescriptor cd, WidgetPropertyDescriptor cpd,
			JasperReportsConfiguration jConfig) {
		if (cpd.getComboOptions() != null) {
			String[][] opts = cpd.getComboOptions();
			String[][] i18nOpts = new String[opts.length][2];
			for (int i = 0; i < opts.length; i++) {
				i18nOpts[i][0] = opts[i][0];
				i18nOpts[i][1] = cd.getLocalizedString(opts[i][1]);
			}
			ComboItemPropertyDescription<String> result = new ComboItemPropertyDescription<>(cpd.getName(),
					cd.getLocalizedString(cpd.getLabel()), cd.getLocalizedString(cpd.getDescription()),
					cpd.isMandatory(), cpd.getDefaultValue(), i18nOpts);
			result.setReadOnly(cpd.isReadOnly());
			result.setFallbackValue(cpd.getFallbackValue());
			return result;
		}
		return null;
	}
}
