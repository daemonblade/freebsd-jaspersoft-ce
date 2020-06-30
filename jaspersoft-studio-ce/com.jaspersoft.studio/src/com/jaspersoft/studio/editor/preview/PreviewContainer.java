/*******************************************************************************
 * Copyright (C) 2010 - 2016. TIBCO Software Inc. 
 * All Rights Reserved. Confidential & Proprietary.
 ******************************************************************************/
package com.jaspersoft.studio.editor.preview;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.jaspersoft.studio.JaspersoftStudioPlugin;
import com.jaspersoft.studio.data.DataAdapterDescriptor;
import com.jaspersoft.studio.data.DataAdapterManager;
import com.jaspersoft.studio.data.empty.EmptyDataAdapterDescriptor;
import com.jaspersoft.studio.data.storage.JRDefaultDataAdapterStorage;
import com.jaspersoft.studio.data.widget.DataAdapterAction;
import com.jaspersoft.studio.data.widget.IDataAdapterRunnable;
import com.jaspersoft.studio.editor.preview.actions.RunStopAction;
import com.jaspersoft.studio.editor.preview.actions.SwitchViewsAction;
import com.jaspersoft.studio.editor.preview.stats.Statistics;
import com.jaspersoft.studio.editor.preview.toolbar.LeftToolBarManager;
import com.jaspersoft.studio.editor.preview.toolbar.PreviewTopToolBarManager;
import com.jaspersoft.studio.editor.preview.toolbar.TopToolBarManagerJRPrint;
import com.jaspersoft.studio.editor.preview.view.APreview;
import com.jaspersoft.studio.editor.preview.view.control.IReportRunner;
import com.jaspersoft.studio.editor.preview.view.control.ReportController;
import com.jaspersoft.studio.messages.Messages;
import com.jaspersoft.studio.messages.MessagesByKeys;
import com.jaspersoft.studio.preferences.util.PreferencesUtils;
import com.jaspersoft.studio.statistics.UsageStatisticsIDs;
import com.jaspersoft.studio.swt.toolbar.ToolItemContribution;
import com.jaspersoft.studio.swt.widgets.CSashForm;
import com.jaspersoft.studio.utils.JRXMLUtils;
import com.jaspersoft.studio.utils.jasper.JasperReportsConfiguration;

import net.sf.jasperreports.eclipse.ui.util.UIUtils;
import net.sf.jasperreports.eclipse.util.FileUtils;
import net.sf.jasperreports.eclipse.util.Misc;
import net.sf.jasperreports.eclipse.viewer.action.AReportAction;
import net.sf.jasperreports.eclipse.viewer.action.ZoomActualSizeAction;
import net.sf.jasperreports.eclipse.viewer.action.ZoomInAction;
import net.sf.jasperreports.eclipse.viewer.action.ZoomOutAction;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.design.JRDesignDataset;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.design.events.CollectionElementAddedEvent;
import net.sf.jasperreports.engine.design.events.CollectionElementRemovedEvent;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

public class PreviewContainer extends PreviewJRPrint implements IDataAdapterRunnable, IParametrable, IRunReport {

	/**
	 * Zoom in action
	 */
	private AReportAction zoomInAction = null;

	/**
	 * Zoom out action
	 */
	private AReportAction zoomOutAction = null;

	/**
	 * the zoom to 100% action
	 */
	private AReportAction zoomActualAction = null;

	private DataAdapterDescriptor dataAdapterDesc;

	/**
	 * Flag used to enable or disable the run of the report when the JasperDesign is
	 * set
	 */
	protected boolean runWhenInitilizing = true;

	private ReportController reportControler;

	protected boolean isParameterDirty = true;

	protected boolean isRunDirty = true;

	private MultiPageContainer leftContainer;

	private CSashForm sashform;

	private LeftToolBarManager leftToolbar;

	public PreviewContainer() {
		super(true);
	}

	public PreviewContainer(boolean listenResource, JasperReportsConfiguration jrContext) {
		super(listenResource);
		this.jrContext = jrContext;
	}

	@Override
	public boolean isSaveAsAllowed() {
		return true;
	}

	/**
	 * Retrieve the action from the contribution items. If the action were already
	 * retrieved it dosen't do nothing
	 */
	private void setActions() {
		for (IContributionItem item : actionToolBarManager.getContributions()) {
			if (zoomInAction != null && zoomOutAction != null && zoomActualAction != null)
				return;
			if (ZoomInAction.ID.equals(item.getId()) && item instanceof ActionContributionItem) {
				zoomInAction = (AReportAction) ((ActionContributionItem) item).getAction();
			} else if (ZoomOutAction.ID.equals(item.getId()) && item instanceof ActionContributionItem) {
				zoomOutAction = (AReportAction) ((ActionContributionItem) item).getAction();
			} else if (ZoomActualSizeAction.ID.equals(item.getId()) && item instanceof ActionContributionItem) {
				zoomActualAction = (AReportAction) ((ActionContributionItem) item).getAction();
			}
		}
	}

	public AReportAction getZoomInAction() {
		setActions();
		return zoomInAction;
	}

	public AReportAction getZoomOutAction() {
		setActions();
		return zoomOutAction;
	}

	public AReportAction getZoomActualAction() {
		setActions();
		return zoomActualAction;
	}

	@Override
	protected void loadJRPrint(IEditorInput input) throws PartInitException {
		setJasperPrint(null, null);
		if (listenResource) {
			InputStream in = null;
			IFile file = null;
			try {
				if (input instanceof IFileEditorInput) {
					file = ((IFileEditorInput) input).getFile();
					in = file.getContents();
				} else {
					throw new PartInitException("Invalid Input: Must be IFileEditorInput or FileStoreEditorInput"); //$NON-NLS-1$
				}
				in = JRXMLUtils.getXML(jrContext, input, file.getCharset(true), in, null);
				initJRContext(file);
				jrContext.setJasperDesign(JRXmlLoader.load(jrContext, in));
				setJasperDesign(jrContext);
			} catch (Exception e) {
				throw new PartInitException(e.getMessage(), e);
			} finally {
				if (in != null)
					try {
						in.close();
					} catch (IOException e) {
						throw new PartInitException("error closing input stream", e); //$NON-NLS-1$
					}
			}
		}
	}

	public void refreshLeftContainer() {
		leftToolbar.refresh();
	}

	public MultiPageContainer getLeftContainer() {
		if (leftContainer == null)
			leftContainer = new MultiPageContainer() {
				@Override
				public void switchView(Statistics stats, APreview view) {
					super.switchView(stats, view);
					for (Map.Entry<String, APreview> entry : pmap.entrySet()) {
						if (entry.getValue() == view) {
							leftToolbar.setLabelText(MessagesByKeys.getString(entry.getKey()));
							break;
						}
					}
				}
			};
		return leftContainer;
	}

	/**
	 * When disposed the mouse wheel filter is removed
	 */
	@Override
	public void dispose() {
		if (dataDapterToolBarManager instanceof PreviewTopToolBarManager)
			((PreviewTopToolBarManager) dataDapterToolBarManager).dispose();
		super.dispose();
	}

	@Override
	public void createPartControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);

		container.setLayout(new GridLayout(1, false));
		PlatformUI.getWorkbench().getHelpSystem().setHelp(container, "com.jaspersoft.studio.doc.editor_preview"); //$NON-NLS-1$

		Composite toolbarContainer = new Composite(container, SWT.NONE);
		GridData additionalToolbarGD = new GridData(SWT.FILL, SWT.TOP, true, false);
		toolbarContainer.setLayoutData(additionalToolbarGD);
		getDataAdapterToolBarManager(toolbarContainer);
		getActionToolBarManager(toolbarContainer);

		final Button lbutton = new Button(toolbarContainer, SWT.PUSH);
		lbutton.setImage(JaspersoftStudioPlugin.getInstance().getImage("icons/application-sidebar-expand.png")); //$NON-NLS-1$
		lbutton.setToolTipText(Messages.PreviewContainer_buttonText);
		lbutton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				sashform.upRestore();
			}
		});

		// The toolbar container uses a custom layout to have always the data adapter
		// toolbar at full size on
		// the start, the parameter button on the end and to give the remaining space to
		// the action toolbar,
		// that eventually will be able to resize and go down. The action toolbar will
		// be always 100 at least
		toolbarContainer.setLayout(new Layout() {

			@Override
			protected void layout(Composite composite, boolean flushCache) {
				Control[] children = composite.getChildren();
				int spacing = 5;
				Point daToolbarSize = children[0].computeSize(SWT.DEFAULT, SWT.DEFAULT, flushCache);
				Point buttonSize = children[2].computeSize(SWT.DEFAULT, SWT.DEFAULT, flushCache);
				Rectangle parentSize = composite.getClientArea();
				int actionToolbarWidth = Math.max(100, parentSize.width - daToolbarSize.x - buttonSize.x - spacing);
				Point actionToolbarSize = children[1].computeSize(actionToolbarWidth, SWT.DEFAULT, flushCache);
				int offestX = 0;
				children[0].setBounds(0, 0, daToolbarSize.x, daToolbarSize.y);
				offestX += daToolbarSize.x + spacing;
				children[1].setBounds(offestX, 0, actionToolbarWidth, actionToolbarSize.y);
				int buttonStart = parentSize.width - buttonSize.x;
				int remainingSpace = parentSize.width - (actionToolbarWidth + offestX);
				if (remainingSpace < buttonSize.x) {
					buttonStart = actionToolbarWidth + offestX + spacing;
				}
				children[2].setBounds(buttonStart, 0, buttonSize.x, buttonSize.y);
			}

			@Override
			protected Point computeSize(Composite composite, int wHint, int hHint, boolean flushCache) {
				Control[] children = composite.getChildren();
				Point daToolbarSize = children[0].computeSize(SWT.DEFAULT, SWT.DEFAULT, flushCache);
				Point buttonSize = children[2].computeSize(SWT.DEFAULT, SWT.DEFAULT, flushCache);
				Rectangle parentSize = composite.getClientArea();
				int width = Math.max(100, parentSize.width - daToolbarSize.x - buttonSize.x);
				Point actionToolbarSize = children[1].computeSize(width, SWT.DEFAULT, flushCache);
				int height = Math.max(daToolbarSize.y, Math.max(buttonSize.y, actionToolbarSize.y));
				return new Point(width + daToolbarSize.x + buttonSize.x, height);
			}
		});

		sashform = new CSashForm(container, SWT.HORIZONTAL);
		GridData gd = new GridData(GridData.FILL_BOTH);
		sashform.setLayoutData(gd);

		createLeft(parent, sashform);

		createRight(sashform);

		sashform.setWeights(new int[] { 40, 60 });
	}

	@Override
	protected PreviewTopToolBarManager getDataAdapterToolBarManager(Composite container) {
		if (dataDapterToolBarManager == null) {
			IFile file = null;
			IProject project = null;
			IEditorInput editorInput = getEditorInput();
			if (editorInput instanceof IFileEditorInput) {
				file = ((IFileEditorInput) editorInput).getFile();
			}
			if (jrContext != null) {
				project = (IProject) jrContext.get(FileUtils.KEY_IPROJECT);
			}
			dataDapterToolBarManager = new PreviewTopToolBarManager(this, container,
					DataAdapterManager.getDataAdapter(file, project, jrContext));
		}
		return (PreviewTopToolBarManager) dataDapterToolBarManager;
	}

	@Override
	protected TopToolBarManagerJRPrint getActionToolBarManager(Composite container) {
		if (actionToolBarManager == null) {
			actionToolBarManager = new TopToolBarManagerJRPrint(this, container) {
				@Override
				protected void fillToolbar(IToolBarManager tbManager) {
					if (getMode().equals(RunStopAction.MODERUN_LOCAL)) {
						if (pvModeAction == null)
							pvModeAction = new SwitchViewsAction(container.getRightContainer(),
									Messages.PreviewContainer_javatitle, true, getViewFactory());
						tbManager.add(pvModeAction);
					}
					tbManager.add(new Separator());
				}
			};
		}
		return actionToolBarManager;
	}

	/**
	 * Set the current preview type
	 * 
	 * @param viewerKey
	 *            key of the type to show
	 * @param refresh
	 *            flag to set if the preview should also be refreshed
	 */
	@Override
	public void setCurrentViewer(String viewerKey, boolean refresh) {
		super.setCurrentViewer(viewerKey, refresh);
		// Set the name of the action to align the showed name with the actual type
		actionToolBarManager.setActionText(viewerKey);
	}

	protected void createLeft(Composite parent, SashForm sf) {
		Composite leftComposite = new Composite(sf, SWT.BORDER);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		leftComposite.setLayout(layout);

		leftToolbar = new LeftToolBarManager(this, leftComposite);
		setupDataAdapter();

		final Composite cleftcompo = new Composite(leftComposite, SWT.NONE);
		cleftcompo.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
		cleftcompo.setLayoutData(new GridData(GridData.FILL_BOTH));
		cleftcompo.setLayout(new StackLayout());

		Composite bottom = new Composite(leftComposite, SWT.NONE);
		bottom.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_CENTER));
		bottom.setLayout(new GridLayout(2, false));

		ToolBar tb = new ToolBar(bottom, SWT.FLAT | SWT.WRAP | SWT.RIGHT);
		ToolBarManager tbm = new ToolBarManager(tb);
		tbm.add(new RunStopAction(this));
		ToolItemContribution tireset = new ToolItemContribution("", SWT.PUSH); //$NON-NLS-1$
		tbm.add(tireset);
		tbm.update(true);
		ToolItem toolItem = tireset.getToolItem();
		toolItem.setText(Messages.PreviewContainer_resetactiontitle);
		toolItem.setToolTipText(Messages.PreviewContainer_resetactiontooltip);
		toolItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				reportControler.resetParametersToDefault();
			}

		});
		tbm.update(true);

		getLeftContainer().populate(cleftcompo, getReportControler().createControls(cleftcompo));
		getLeftContainer().switchView(null, ReportController.FORM_PARAMETERS);
	}

	@Override
	protected Composite createRight(Composite parent) {
		super.createRight(parent);
		return rightComposite;
	}

	/**
	 * Log the report language and preview type (different from java) when the
	 * report is executed
	 */
	protected void auditPreview() {
		// Log the preview if not Java
		if (currentViewer != null && !currentViewer.equals("Java")) { //$NON-NLS-1$
			JaspersoftStudioPlugin.getInstance().getUsageManager().audit(currentViewer,
					UsageStatisticsIDs.CATEGORY_PREVIEW_FORMAT);
			// Log the language used by the report
			if (jrContext != null && jrContext.getJasperDesign() != null) {
				String reportLanguage = jrContext.getJasperDesign().getLanguage();
				if (reportLanguage != null) {
					JaspersoftStudioPlugin.getInstance().getUsageManager().audit("ReportLanguage" + reportLanguage, //$NON-NLS-1$
							UsageStatisticsIDs.CATEGORY_REPORT);
				}
			}
		}
	}

	@Override
	public boolean switchRightView(APreview view, Statistics stats, MultiPageContainer container) {
		reportControler.viewerChanged(view);
		return super.switchRightView(view, stats, container);
	}

	public boolean runReport(final DataAdapterDescriptor myDataAdapter, boolean daAction) {
		return runReport(myDataAdapter, isParameterDirty, daAction);
	}

	public boolean runReport(final DataAdapterDescriptor myDataAdapter, boolean prmDirty, boolean daAction) {
		if (isNotRunning()) {
			// check if we can run the report
			actionToolBarManager.setEnabled(false);
			dataDapterToolBarManager.setEnabled(false);
			leftToolbar.setEnabled(false);
			getLeftContainer().setEnabled(false);
			getLeftContainer().switchView(null, ReportController.FORM_PARAMETERS);

			// Cache the DataAdapter used for this report only if it is not null.
			if (myDataAdapter != null) {
				// TODO should we save the reference in the JRXML ?
				dataAdapterDesc = myDataAdapter;
				setParameterDirty(prmDirty);
			} else {
				DataAdapterAction daWidget = ((PreviewTopToolBarManager) dataDapterToolBarManager)
						.getDataSourceWidget();
				dataAdapterDesc = daWidget.isDefaultDASelected() ? null : daWidget.getSelected();
			}

			addPreviewModeContributeProperties();
			reportControler.runReport();
			auditPreview();
		}
		return true;
	}

	private void addPreviewModeContributeProperties() {
		List<PreviewModeDetails> previewDetails = JaspersoftStudioPlugin.getExtensionManager()
				.getAllPreviewModeDetails(Misc.nvl(getMode()));
		for (PreviewModeDetails d : previewDetails) {
			Map<String, String> previewModeProperties = d.getPreviewModeProperties();
			for (String pKey : previewModeProperties.keySet()) {
				String pValue = previewModeProperties.get(pKey);
				PreferencesUtils.storeJasperReportsProperty(pKey, pValue);
				DefaultJasperReportsContext.getInstance().setProperty(pKey, pValue);
			}
		}
		APreview view = null;
		if (ReportController.getRunners().containsKey(getMode())) {
			view = getRunnerViewer(ReportController.getRunners().get(getMode()));
			getRightContainer().switchView(null, view);
		} else if (RunStopAction.MODERUN_LOCAL.equals(getMode())) {
			getRightContainer().switchView(null, getDefaultViewerKey());
			view = getDefaultViewer();
		}
		refreshToolbars(view);
	}

	protected void refreshToolbars(final APreview view) {
		Display.getDefault().syncExec(() -> {
			if (actionToolBarManager != null)
				actionToolBarManager.contributeItems(view);
			actionToolBarManager.getTopToolBar().getParent().layout(true, true);
		});
	}

	@Override
	public void setNotRunning(boolean stoprun) {
		super.setNotRunning(stoprun);
		if (stoprun) {
			getLeftContainer().setEnabled(true);
			leftToolbar.setEnabled(true);
		}
	}

	public void showParameters(boolean showprm) {
		if (sashform.isDisposed())
			return;
		if (showprm)
			sashform.upRestore();
		else
			sashform.upHide();
	}

	public ReportController getReportControler() {
		if (reportControler == null)
			reportControler = new ReportController(this, jrContext);
		return reportControler;
	}

	public boolean isRunDirty() {
		return isParameterDirty;
	}

	public void setParameterDirty(boolean isParameterDirty) {
		this.isParameterDirty = isParameterDirty;
	}

	public void setRunDirty(boolean isRunDirty) {
		this.isRunDirty = isRunDirty;
		isParameterDirty = isRunDirty;
	}

	private PropertyChangeListener propChangeListener;

	public void setJasperDesign(final JasperReportsConfiguration jConfig) {
		UIUtils.getDisplay().asyncExec(() -> {
			ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(jrContext.getClassLoader());
				getReportControler().setJrContext(jConfig);
				setupDataAdapter();

				if ((isRunDirty || getJasperPrint() == null) && runWhenInitilizing)
					runReport(dataAdapterDesc, false);
				if (propChangeListener == null)
					propChangeListener = evt -> {
						String pname = evt.getPropertyName();
						if (pname.equals(JRDesignDataset.PROPERTY_PARAMETERS)) {
							if (evt instanceof CollectionElementAddedEvent)
								((JRDesignParameter) ((CollectionElementAddedEvent) evt).getAddedValue())
										.getEventSupport().addPropertyChangeListener(propChangeListener);
							else if (evt instanceof CollectionElementRemovedEvent)
								((JRDesignParameter) ((CollectionElementRemovedEvent) evt).getRemovedValue())
										.getEventSupport().removePropertyChangeListener(propChangeListener);
						}
						if (evt.getSource() instanceof JRParameter)
							jConfig.getJRParameters().remove(((JRParameter) evt.getSource()).getName());
						if (isParameterDirty)
							return;
						if (evt.getSource() instanceof JRParameter || pname.equals(JRDesignDataset.PROPERTY_PARAMETERS)
								|| pname.equals(JRDesignDataset.PROPERTY_SCRIPTLETS))
							isParameterDirty = true;
					};
				JRDesignDataset mds = jrContext.getJasperDesign().getMainDesignDataset();
				mds.getEventSupport().removePropertyChangeListener(propChangeListener);
				mds.getEventSupport().addPropertyChangeListener(propChangeListener);

				for (JRParameter p : mds.getParametersList()) {
					((JRDesignParameter) p).getEventSupport().removePropertyChangeListener(propChangeListener);
					((JRDesignParameter) p).getEventSupport().addPropertyChangeListener(propChangeListener);
				}
			} finally {
				Thread.currentThread().setContextClassLoader(oldCL);
			}
		});
	}

	private void setupDataAdapter() {
		JasperDesign jd = jrContext.getJasperDesign();
		PreviewTopToolBarManager pt = (PreviewTopToolBarManager) dataDapterToolBarManager;
		if (pt != null && jd != null) {
			String strda = jd.getProperty(jrContext.getEditorContext().getDataAdapterProperty());
			DataAdapterAction daWidget = ((PreviewTopToolBarManager) dataDapterToolBarManager).getDataSourceWidget();
			pt.refreshDataAdapters();

			if (strda != null) {
				pt.setDataAdapters(strda);
				dataAdapterDesc = daWidget.isDefaultDASelected() ? null : daWidget.getSelected();
			} else {
				// If there is not a default JSS Da but it is defined a JR default da then
				// select it on the preview
				JRDefaultDataAdapterStorage defaultStorage = DataAdapterManager.getJRDefaultStorage(getConfiguration());
				DataAdapterDescriptor defaultDA = defaultStorage.getDefaultJRDataAdapter(jd.getMainDesignDataset());
				if (defaultDA != null) {
					pt.setDataAdapters(defaultDA.getName());
				} else {
					// If no data adapter is available select the default one
					pt.setDataAdapters(EmptyDataAdapterDescriptor.EMPTY_ADAPTER_NAME);
				}
			}
		}
	}

	public DataAdapterDescriptor getDataAdapterDesc() {
		return dataAdapterDesc;
	}

	private String runMode;

	public void setMode(String mode) {
		this.runMode = mode;
		if (ReportController.getRunners().containsKey(mode))
			getRightContainer().switchView(null, getRunnerViewer(ReportController.getRunners().get(mode)));
		else if (mode.equals(RunStopAction.MODERUN_LOCAL))
			getRightContainer().switchView(null, getDefaultViewerKey());
		refreshLeftContainer();
	}

	public String getMode() {
		if (runMode == null)
			runMode = getJrContext().getEditorContext().getDefaultRunMode();
		return runMode;
	}

	@Override
	public APreview getDefaultViewer() {
		String m = getMode();
		if (ReportController.getRunners().containsKey(m))
			return getRunnerViewer(ReportController.getRunners().get(m));
		return super.getDefaultViewer();
	}

	private Map<IReportRunner, APreview> runPreview = new HashMap<>();

	public APreview getRunnerViewer(IReportRunner runner) {
		return runPreview.computeIfAbsent(runner, k -> runner.getPreview(rightComposite, jrContext));
	}

	@Override
	public void runReport() {
		DataAdapterAction daWidget = ((PreviewTopToolBarManager) dataDapterToolBarManager).getDataSourceWidget();
		daWidget.refreshDA();
		dataAdapterDesc = daWidget.isDefaultDASelected() ? null : daWidget.getSelected();
		runReport(dataAdapterDesc, false);
	}

	/**
	 * Set the dirty flag of the preview area
	 */
	public void setDirty(boolean dirty) {
		this.isDirty = dirty;
		if (dirty)
			setRunDirty(true);
	}

	/**
	 * Return the JasperReportsConfiguration of the loaded report
	 * 
	 * @return a JasperReportsConfiguration
	 */
	@Override
	public JasperReportsConfiguration getConfiguration() {
		return jrContext;
	}

	/**
	 * Flag used to enable or disable the run of the report when the JasperDesign is
	 * set trough setJasperDesign(final JasperReportsConfiguration jConfig)
	 * 
	 * @param value
	 *            true if the report should be run when the JasperDesign is set,
	 *            false otherwise
	 */
	public void setRunWhenInitilizing(boolean value) {
		runWhenInitilizing = value;
	}
}
