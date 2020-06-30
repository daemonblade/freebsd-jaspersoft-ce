/*******************************************************************************
 * Copyright (C) 2010 - 2016. TIBCO Software Inc. 
 * All Rights Reserved. Confidential & Proprietary.
 ******************************************************************************/
package com.jaspersoft.studio.data.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;

import com.jaspersoft.studio.data.AWizardDataEditorComposite;
import com.jaspersoft.studio.data.DataAdapterDescriptor;
import com.jaspersoft.studio.data.designer.UndoRedoImpl;
import com.jaspersoft.studio.data.fields.IFieldsProvider;
import com.jaspersoft.studio.messages.Messages;
import com.jaspersoft.studio.preferences.fonts.utils.FontUtils;
import com.jaspersoft.studio.utils.jasper.JasperReportsConfiguration;
import com.jaspersoft.studio.wizards.JSSWizardRunnablePage;

import net.sf.jasperreports.data.DataAdapterService;
import net.sf.jasperreports.data.DataAdapterServiceUtil;
import net.sf.jasperreports.eclipse.ui.util.UIUtils;
import net.sf.jasperreports.eclipse.util.Misc;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.ParameterContributorContext;
import net.sf.jasperreports.engine.design.JRDesignDataset;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import net.sf.jasperreports.engine.design.JRDesignQuery;

/**
 * This is an abstract implementation of the (almost) most simple editor that
 * can be provided by an adapter. The other type of editor could be just a
 * composite, made to provide information of what will happen while pressing
 * next in the wizard page in which it is displayed.
 * 
 * This abstract wizard creates just a composite in which there is a simple
 * label and and a textfield.
 * 
 * @author gtoffoli
 * 
 */
public class SimpleQueryWizardDataEditorComposite extends AWizardDataEditorComposite {

	protected String[] langs;

	/**
	 * Question return code. This variable is used across a thread UI and
	 * background process thread. We assume there will never pop up two
	 * identical questions at the same time.
	 */
	private int questionReturnCode = SWT.OK;

	private DataAdapterDescriptor dataAdapterDescriptor;

	/**
	 * Convenient object to be passed to the IFieldsProvider.getFields method
	 */
	protected JRDesignDataset dataset = null;

	private String queryString = ""; //$NON-NLS-1$

	/**
	 * UI component to display the title
	 */
	private Label lblTitle = null;

	private String queryLanguage = null;

	/**
	 * The styled text UI component, that can be configured by subclasses.
	 */
	protected StyledText styledText = null;

	/**
	 * A simple title to be used to say something like: "Write a query in
	 * SQL..."
	 */
	private String title = null;

	private DataAdapterService das;

	public SimpleQueryWizardDataEditorComposite(Composite parent, WizardPage page, String lang) {
		this(parent, page, null, lang, new String[] { lang });
	}

	public SimpleQueryWizardDataEditorComposite(Composite parent, WizardPage page,
			DataAdapterDescriptor dataAdapterDescriptor) {
		this(parent, page, dataAdapterDescriptor, "", new String[0]); //$NON-NLS-1$
	}

	public SimpleQueryWizardDataEditorComposite(Composite parent, WizardPage page,
			DataAdapterDescriptor dataAdapterDescriptor, String lang) {
		this(parent, page, dataAdapterDescriptor, lang, new String[] { lang });
	}

	public SimpleQueryWizardDataEditorComposite(Composite parent, WizardPage page,
			DataAdapterDescriptor dataAdapterDescriptor, String lang, String[] langs) {
		super(parent, page);
		this.langs = langs;
		setQueryLanguage(lang);
		this.dataAdapterDescriptor = dataAdapterDescriptor;
		init();
		createCompositeContent();
	}

	/**
	 * Initializes additional information that are supposed to be sub-class
	 * specific and executed in the constructor before the main composite
	 * content creation. This method is called before
	 * {@link #createCompositeContent()}.
	 */
	protected void init() {
		// do nothig - default behavior
	}

	/**
	 * Sets layout and creates the content of the main composite. Created
	 * widgets should use <code>this</code> as parent composite.
	 */
	protected void createCompositeContent() {
		setLayout(new FormLayout());

		lblTitle = new Label(this, SWT.NONE);
		FormData fdTitle = new FormData();
		fdTitle.top = new FormAttachment(0);
		fdTitle.left = new FormAttachment(0);
		fdTitle.right = new FormAttachment(100);
		lblTitle.setLayoutData(fdTitle);

		if (getTitle() != null) {
			lblTitle.setText(getTitle());
		}

		styledText = new StyledText(this, SWT.BORDER);
		new UndoRedoImpl(styledText);

		FormData fdSt = new FormData();
		fdSt.bottom = new FormAttachment(100);
		fdSt.right = new FormAttachment(100);
		fdSt.top = new FormAttachment(lblTitle, 6);
		fdSt.left = new FormAttachment(lblTitle, 0, SWT.LEFT);
		styledText.setLayoutData(fdSt);
		styledText.setFont(FontUtils.getEditorsFont(getJasperReportsConfiguration()));
		styledText.addModifyListener(e -> queryString = styledText.getText().trim());

		queryString = styledText.getText().trim();
	}

	public String getQueryString() {
		return queryString;
	}

	public void setQueryString(String querystring) {
		this.queryString = querystring;
	}

	/**
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * @param title the title to set
	 */
	public void setTitle(String title) {
		this.title = title;
		if (lblTitle != null)
			lblTitle.setText(title);
	}

	/**
	 * The query language
	 * 
	 * @return the query language or null if the language has not been set.
	 */
	@Override
	public String getQueryLanguage() {
		return this.queryLanguage;
	}

	/**
	 * @param queryLanguage the queryLanguage to set
	 */
	public void setQueryLanguage(String queryLanguage) {
		this.queryLanguage = queryLanguage;
	}

	/**
	 * Return the fields.
	 * 
	 * If the dataAdapterDescriptor implements IFieldsProvider, this interface
	 * is used to get the fields automatically.
	 * 
	 * This method is invoked on a thread which is not in the UI event thread,
	 * so no UI update should be performed without using a proper async thread.
	 * 
	 * return the result of IFieldsProvider.getFields() or an empty list of
	 * JRField is the DataAdapterDescriptor does not implement the
	 * IFieldsProvider interface.
	 */
	public List<JRDesignField> readFields() throws Exception {
		List<JRDesignField> fields = null;
		DataAdapterDescriptor dad = getDataAdapterDescriptor();
		if (dad != null && dad instanceof IFieldsProvider) {
			questionReturnCode = SWT.OK;
			JasperReportsConfiguration jContext = getJasperReportsConfiguration();
			das = DataAdapterServiceUtil.getInstance(new ParameterContributorContext(jContext, null, null))
					.getService(dad.getDataAdapter());

			try {
				JRDesignDataset tmpDataset = getDataset();
				if (tmpDataset.getQuery().getText() == null || tmpDataset.getQuery().getText().trim().length() == 0) {
					Display.getDefault().syncExec(() -> {
						MessageBox dialog = new MessageBox(UIUtils.getShell(), SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
						dialog.setText(Messages.SimpleQueryWizardDataEditorComposite_noQueryProvidedTitle);
						dialog.setMessage(Messages.SimpleQueryWizardDataEditorComposite_noQueryProvidedText);
						questionReturnCode = dialog.open();
					});

					if (questionReturnCode != SWT.OK) {
						throw JSSWizardRunnablePage.USER_CANCEL_EXCEPTION;
					}
				} else {
					fields = ((IFieldsProvider) dad).getFields(das, jContext, dataset);
				}
			} catch (final JRException ex) {
				Display.getDefault().syncExec(() -> {
					// Cleanup of the error. JRException are a very low
					// meaningful exception when working
					// with data, what the user is interested into is the
					// underline error (i.e. an SQL error).
					// That's why we rise the real cause, if any instead of
					// rising the high-level exception...
					String errorMsg = ex.getMessage();
					if (ex.getCause() != null && ex.getCause() instanceof Exception) {
						errorMsg = ex.getCause().getMessage();
					}
					getPage().setErrorMessage(errorMsg);
					boolean answer = MessageDialog.openQuestion(UIUtils.getShell(),
							Messages.SimpleQueryWizardDataEditorComposite_QueryErrorTitle,
							NLS.bind(Messages.SimpleQueryWizardDataEditorComposite_QueryErrorMsg, errorMsg));
					questionReturnCode = (answer) ? SWT.OK : SWT.CANCEL;
				});
				if (questionReturnCode != SWT.OK) {
					throw JSSWizardRunnablePage.USER_CANCEL_EXCEPTION;
				}
			} finally {
				das.dispose();
				das = null;
			}
		}
		return Misc.nvl(fields, new ArrayList<JRDesignField>());

	}

	@Override
	public void abortOperationOccured() {
		if (das != null) {
			das.dispose();
		}
	}

	/**
	 * Convenient way to crate a dataset object to be passed to the
	 * IFieldsProvider.getFields method
	 * 
	 * @return JRDesignDataset return a dataset with the proper query and
	 * language set...
	 */
	public JRDesignDataset getDataset() {
		if (dataset == null) {
			dataset = new JRDesignDataset(getJasperReportsConfiguration(), true);
			JRDesignQuery query = new JRDesignQuery();
			query.setLanguage(getQueryLanguage());
			dataset.setQuery(query);
		}

		((JRDesignQuery) dataset.getQuery()).setText(getQueryString());
		return dataset;
	}

	/**
	 * @return the dataAdapterDescriptor
	 */
	public DataAdapterDescriptor getDataAdapterDescriptor() {
		return dataAdapterDescriptor;
	}

	/**
	 * @param dataAdapterDescriptor the dataAdapterDescriptor to set
	 */
	public void setDataAdapterDescriptor(DataAdapterDescriptor dataAdapterDescriptor) {
		this.dataAdapterDescriptor = dataAdapterDescriptor;
	}

	@Override
	public List<JRDesignParameter> readParameters() throws Exception {
		if (dataset != null) {
			List<JRDesignParameter> prms = new ArrayList<>();
			for (JRParameter p : dataset.getParametersList())
				if (!p.isSystemDefined())
					prms.add((JRDesignParameter) p);
			return prms;
		}
		return null;
	}
}
