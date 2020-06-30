/*******************************************************************************
 * Copyright (C) 2010 - 2016. TIBCO Software Inc. 
 * All Rights Reserved. Confidential & Proprietary.
 ******************************************************************************/
package com.jaspersoft.studio.property.section.widgets;

import java.util.List;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import com.jaspersoft.studio.messages.Messages;
import com.jaspersoft.studio.model.APropertyNode;
import com.jaspersoft.studio.property.combomenu.ComboItem;
import com.jaspersoft.studio.property.combomenu.ComboItemAction;
import com.jaspersoft.studio.property.combomenu.ComboMenuViewer;
import com.jaspersoft.studio.property.descriptors.NamedEnumPropertyDescriptor;
import com.jaspersoft.studio.property.section.AbstractSection;

import net.sf.jasperreports.eclipse.ui.util.UIUtils;
import net.sf.jasperreports.engine.type.LineStyleEnum;

/**
 * The widget for a popup combo box
 * 
 * @author Orlandin Marco
 * 
 */
public class SPRWPopUpCombo extends ASPropertyWidget<NamedEnumPropertyDescriptor<LineStyleEnum>> {

	/**
	 * The combo item
	 */
	protected ComboMenuViewer combo;

	/**
	 * The list of entry in the popup menu
	 */
	protected List<ComboItem> items = null;

	/**
	 * Create a new widget
	 * 
	 * @param parent
	 *          parent of the widget
	 * @param section
	 *          section where the command will be executed
	 * @param pDescriptor
	 *          descriptor of the property of this item
	 * @param items
	 *          List of entry in the popup menu
	 */
	public SPRWPopUpCombo(Composite parent, AbstractSection section,
			NamedEnumPropertyDescriptor<LineStyleEnum> pDescriptor, List<ComboItem> items) {
		super(parent, section, pDescriptor);
		this.items = items;
		createComponent(parent);
	}

	@Override
	public Control getControl() {
		if (combo != null)
			return combo.getControl();
		else
			return null;
	}

	public void setData(APropertyNode pnode, Object b) {
		combo.setEnabled(pnode.isEditable());
		int index = 0;
		for (ComboItem item : items) {
			if (item.getValue() == null ? b == null : item.getValue().equals(b)) {
				break;
			}
			index++;
		}
		combo.select(index);
	}
	
	@Override
	public void setData(APropertyNode pnode, Object resolvedValue, Object elementValue) {
		setData(pnode, resolvedValue);
		if (elementValue == null) {
			combo.setTextForeground(UIUtils.INHERITED_COLOR);
			combo.setToolTipText(Messages.common_inherited_attribute + pDescriptor.getDescription());
			if (getLabel() != null) {
				getLabel().setToolTipText(Messages.common_inherited_attribute + pDescriptor.getDescription());
				getLabel().setForeground(UIUtils.INHERITED_COLOR);
			}
		} else {
			combo.setTextForeground(ColorConstants.black);
			combo.setToolTipText(pDescriptor.getDescription());
			if (getLabel() != null) {
				getLabel().setToolTipText(pDescriptor.getDescription());
				getLabel().setForeground(ColorConstants.black);
			}
		}
	}

	/**
	 * Return the longest text in the list of entry
	 * 
	 * @param itemList
	 *          a list of entry
	 * @return the longest label
	 */
	public static String getLongest(List<ComboItem> itemList) {
		String longest = "";
		for (ComboItem item : itemList)
			if (longest.length() < item.getText().length()) {
				longest = item.getText();
			}
		return longest;
	}

	protected void createComponent(Composite parent) {
		if (items != null) {
			combo = new ComboMenuViewer(parent, SWT.NORMAL, getLongest(items));
			combo.addSelectionListener(new ComboItemAction() {
				@Override
				public void exec() {
					section.changeProperty(pDescriptor.getId(), combo.getSelectionValue());
				}
			});
			combo.setItems(items);
			combo.setToolTipText(pDescriptor.getDescription());
			getControl().addFocusListener(focusListener);
		}
	}

}
