/*******************************************************************************
 * Copyright (C) 2010 - 2016. TIBCO Software Inc. 
 * All Rights Reserved. Confidential & Proprietary.
 ******************************************************************************/
package com.jaspersoft.studio.editor.preview.input.array;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.jaspersoft.studio.editor.preview.input.ADataInput;
import com.jaspersoft.studio.editor.preview.input.IParameter;
import com.jaspersoft.studio.editor.preview.input.ParameterJasper;

import net.sf.jasperreports.eclipse.util.Misc;

public class CollectionInput extends ADataInput {
	private Button bbuton;
	private Text label;
	private boolean refreshing = false;

	public boolean isForType(Class<?> valueClass) {
		return Collection.class.isAssignableFrom(valueClass) || valueClass.isArray();
	}

	@Override
	public void createInput(Composite parent, final IParameter param, Map<String, Object> params) {
		super.createInput(parent, param, params);
		if (isForType(param.getValueClass())) {
			Composite cmp = new Composite(parent, SWT.NONE);
			GridLayout layout = new GridLayout(2, false);
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			cmp.setLayout(layout);
			cmp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			label = new Text(cmp, SWT.BORDER | SWT.WRAP);
			String tt = "You can write strings separated by comma. Exemple: Bob,Tom,Sara\n";
			tt += param.getDescription();
			label.setToolTipText(tt);
			label.setLayoutData(new GridData(GridData.FILL_BOTH));
			label.addTraverseListener(keyListener);
			label.addModifyListener(e -> {
				if (refreshing)
					return;
				String txt = label.getText();
				Object[] s = txt.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
				// now we could just replace values with values from this
				// string array
				// in case of string it's simple
				// in case of unknown type, all are strings
				// what if we have array of numbers?
				// we could parse one by one, what to dow with wrong values?
				// what if we have dates, times, timestamp?
				if (param instanceof ParameterJasper) {
					Class<?> c = ((ParameterJasper) param).getParam().getNestedType();
					if (c != null) {
						Object[] o = new Object[s.length];
						int nulls = 0;
						for (int i = 0; i < s.length; i++) {
							try {
								if (Misc.isNullOrEmpty((String) s[i])) {
									nulls++;
									continue;
								}

								if (c.isAssignableFrom(Integer.class))
									o[i] = Integer.parseInt((String) s[i]);
								else if (c.isAssignableFrom(Byte.class))
									o[i] = Byte.parseByte((String) s[i]);
								else if (c.isAssignableFrom(Short.class))
									o[i] = Short.parseShort((String) s[i]);
								else if (c.isAssignableFrom(BigInteger.class))
									o[i] = new BigInteger((String) s[i]);
								else if (c.isAssignableFrom(Long.class))
									o[i] = Long.parseLong((String) s[i]);
								else if (c.isAssignableFrom(Float.class))
									o[i] = Float.parseFloat((String) s[i]);
								else if (c.isAssignableFrom(Double.class))
									o[i] = Double.parseDouble((String) s[i]);
								else if (c.isAssignableFrom(BigDecimal.class))
									o[i] = new BigDecimal((String) s[i]);
								else if (c.isAssignableFrom(Boolean.class)) {
									o[i] = new Boolean((String) s[i]);
								}
							} catch (NumberFormatException nfe) {
								nulls++;
							}
						}
						if (nulls > 0) {
							s = new Object[o.length - nulls];
							for (int i = 0, j = 0; i < o.length; i++) {
								if (o[i] == null)
									continue;
								s[j] = o[i];
								j++;
							}
						} else
							s = o;
					}
				}
				Map<String, Object> p = CollectionInput.this.params;
				Object value = p.get(param.getName());
				if (value == null)
					return;
				if (value.getClass().isArray())
					value = s;
				else if (value instanceof Collection) {
					if (value instanceof List)
						value = new ArrayList();
					else
						((Collection<?>) value).clear();
					for (Object item : s)
						((Collection) value).add(item);
				}
				updateModel(value);
			});

			bbuton = new Button(cmp, SWT.PUSH);
			bbuton.setText("...");
			bbuton.setToolTipText(param.getDescription());
			bbuton.addFocusListener(focusListener);
			bbuton.addTraverseListener(keyListener);
			bbuton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					Map<String, Object> p = CollectionInput.this.params;
					Object value = p.get(param.getName());
					if (value == null) {
						try {
							value = param.getValueClass().newInstance();
						} catch (InstantiationException ex) {
							if (param.getValueClass().isArray() || param.getValueClass().isAssignableFrom(List.class))
								value = new ArrayList<Object>();
							else if (param.getValueClass().isAssignableFrom(Set.class))
								value = new HashSet<Object>();
						} catch (IllegalAccessException ex) {
						}
					} else if (value.getClass().isArray()) {
						value = Arrays.asList((Object[]) value);
					}
					TableDialog d = new TableDialog(bbuton.getShell(), value, param);
					if (d.open() == Dialog.OK) {
						Object val = d.getValue();
						if (param.getValueClass().isArray()) {
							List<?> list = (List<?>) val;
							val = list.toArray((Object[]) Array.newInstance(param.getValueClass().getComponentType(),
									list.size()));
						}
						updateModel(val);
						updateInput();
					}
				}
			});
			updateInput();
			setNullable(param, label);
		}
	}

	public void updateInput() {
		Object value = params.get(param.getName());
		String del = "";
		String lblText = "";
		if (value != null && value instanceof Collection)
			for (Object obj : (Collection<?>) value) {
				lblText += del + getLabelText(obj);
				del = ",";
			}
		else if (value != null && value.getClass().isArray())
			for (Object obj : (Object[]) value) {
				lblText += del + getLabelText(obj);
				del = ",";
			}
		if (lblText.isEmpty())
			lblText = "No elements";
		try {
			refreshing = true;
			label.setText(lblText);
		} finally {
			refreshing = false;
		}
	}

	private final int SHORTLENGHT = 15;

	private String getLabelText(Object element) {
		String t = element.toString();
		if (!(element instanceof String)) {
			if (t.length() > SHORTLENGHT)
				t = t.substring(0, SHORTLENGHT - 3) + "...";
			return t;
		}
		if (t.contains(","))
			t = "\"" + t + "\"";
		return t;
	}
}
