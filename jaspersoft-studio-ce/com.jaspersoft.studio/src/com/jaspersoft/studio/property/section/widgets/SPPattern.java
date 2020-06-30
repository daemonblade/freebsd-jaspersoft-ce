/*******************************************************************************
 * Copyright (C) 2010 - 2016. TIBCO Software Inc. 
 * All Rights Reserved. Confidential & Proprietary.
 ******************************************************************************/
package com.jaspersoft.studio.property.section.widgets;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.views.properties.IPropertyDescriptor;

import com.jaspersoft.studio.messages.Messages;
import com.jaspersoft.studio.model.APropertyNode;
import com.jaspersoft.studio.property.descriptor.pattern.dialog.PatternEditor;
import com.jaspersoft.studio.property.section.AbstractSection;

import net.sf.jasperreports.eclipse.ui.util.UIUtils;

public class SPPattern extends SPText<IPropertyDescriptor> {

	private Button btn;

	public SPPattern(Composite parent, AbstractSection section, IPropertyDescriptor pDescriptor) {
		super(parent, section, pDescriptor);
	}

	@Override
	protected int getStyle() {
		return SWT.BORDER;
	}
	
	protected void createComponent(Composite parent) {
		parent = section.getWidgetFactory().createComposite(parent);
		GridLayout layout = new GridLayout(2, false);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.marginLeft = 1;
		layout.marginRight = 5;
		parent.setLayout(layout);
		parent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		super.createComponent(parent);

		btn = section.getWidgetFactory().createButton(parent, "...", SWT.PUSH);
		btn.setToolTipText(pDescriptor.getDescription());
		btn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				PatternEditor wizard = new PatternEditor();
				wizard.setValue(ftext.getText());
				WizardDialog dialog = new WizardDialog(ftext.getShell(), wizard);
				dialog.create();
				if (dialog.open() == Dialog.OK) {
					handleTextChanged(section, pDescriptor.getId(), wizard.getValue());
				}
			}
		});
	}

	@Override
	public void setData(APropertyNode pnode, Object b) {
		super.setData(pnode, b);
		btn.setEnabled(pnode.isEditable());
	}
	
	@Override
	public void setData(APropertyNode pnode, Object resolvedValue, Object elementValue) {
		setData(pnode, resolvedValue);
		if (ftext != null && !ftext.isDisposed()){
			if (elementValue != null){
				ftext.setForeground(ColorConstants.black);
				ftext.setToolTipText(pDescriptor.getDescription());
				if (getLabel() != null) {
					getLabel().setToolTipText(pDescriptor.getDescription());
					getLabel().setForeground(ColorConstants.black);
				}
			} else {
				ftext.setForeground(UIUtils.INHERITED_COLOR);
				ftext.setToolTipText(Messages.common_inherited_attribute + pDescriptor.getDescription());
				if (getLabel() != null) {
					getLabel().setToolTipText(Messages.common_inherited_attribute + pDescriptor.getDescription());
					getLabel().setForeground(UIUtils.INHERITED_COLOR);
				}
			}
		}
	}

	@Override
	public Control getControl() {
		return btn.getParent();
	}
}
