/*******************************************************************************
 * Copyright (C) 2010 - 2016. TIBCO Software Inc. 
 * All Rights Reserved. Confidential & Proprietary.
 ******************************************************************************/
package com.jaspersoft.studio.editor.jrexpressions.ui.support.java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.progress.WorkbenchJob;
import org.eclipse.wb.swt.ResourceManager;
import org.eclipse.wb.swt.SWTResourceManager;
import org.eclipse.xtext.validation.Issue;

import com.google.inject.Injector;
import com.jaspersoft.studio.data.designer.UndoRedoImpl;
import com.jaspersoft.studio.editor.expression.ExpressionContext;
import com.jaspersoft.studio.editor.expression.ExpressionContextUtils;
import com.jaspersoft.studio.editor.expression.ExpressionEditorComposite;
import com.jaspersoft.studio.editor.expression.ExpressionEditorSupportUtil;
import com.jaspersoft.studio.editor.expression.ExpressionPersistentWizardDialog;
import com.jaspersoft.studio.editor.expression.ExpressionStatus;
import com.jaspersoft.studio.editor.expression.FunctionsLibraryUtil;
import com.jaspersoft.studio.editor.expression.IExpressionStatusChangeListener;
import com.jaspersoft.studio.editor.jrexpressions.functions.AdditionalStaticFunctions;
import com.jaspersoft.studio.editor.jrexpressions.ui.JRExpressionsActivator;
import com.jaspersoft.studio.editor.jrexpressions.ui.JRExpressionsUIPlugin;
import com.jaspersoft.studio.editor.jrexpressions.ui.messages.Messages;
import com.jaspersoft.studio.editor.jrexpressions.ui.support.ObjectCategoryItem;
import com.jaspersoft.studio.editor.jrexpressions.ui.support.ObjectCategoryItem.Category;
import com.jaspersoft.studio.editor.jrexpressions.ui.support.ObjectsNavigatorContentProvider;
import com.jaspersoft.studio.editor.jrexpressions.ui.support.ObjectsNavigatorLabelProvider;
import com.jaspersoft.studio.editor.jrexpressions.ui.support.StyledTextXtextAdapter2;
import com.jaspersoft.studio.preferences.ExpressionEditorPreferencePage;
import com.jaspersoft.studio.swt.widgets.ClassType;
import com.jaspersoft.studio.utils.UIUtil;

import net.sf.jasperreports.crosstabs.design.JRDesignCrosstab;
import net.sf.jasperreports.eclipse.JasperReportsPlugin;
import net.sf.jasperreports.eclipse.ui.util.UIUtils;
import net.sf.jasperreports.eclipse.util.BundleCommonUtils;
import net.sf.jasperreports.engine.JRExpression;
import net.sf.jasperreports.engine.design.JRDesignDataset;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.expressions.annotations.JRExprFunctionCategoryBean;

/**
 * Standard implementation of the main editing area for JasperReports
 * expressions provided by Jaspersoft Studio for Java language expressions.
 * 
 * <p>
 * The composite is made of a {@link StyledText} widget that contains the
 * expression text. A tree containing the main categories of items that can be
 * used (i.e: parameters, fields, etc.) and an additional details panel that is
 * populated once the user select a specific category.
 * 
 * @author Massimo Rabbi (mrabbi@users.sourceforge.net)
 * 
 */
public class JavaExpressionEditorComposite extends ExpressionEditorComposite {

	public static final int MAIN_SASH_WEIGHT_EDITAREA = 20;
	public static final int MAIN_SASH_WEIGHT_SELECTIONAREA = 80;
	public static final String MAIN_SASH_WEIGHT_EDITAREA_KEY = "mainSashEditArea"; //$NON-NLS-1$
	public static final String MAIN_SASH_WEIGHT_SELECTIONAREA_KEY = "mainSashSelectionArea"; //$NON-NLS-1$

	// Expression stuff
	private JRDesignExpression expression;
	private ExpressionContext exprContext;

	// Widgets stuff
	private StyledText editorArea;
	private StyledTextXtextAdapter2 xtextAdapter;
	private TreeViewer objectsNavigator;
	private Composite objectCategoryDetailsCmp;
	private StackLayout detailsPanelStackLayout;
	private List<IExpressionStatusChangeListener> statusChangeListeners;
	private ClassType valueType;
	private SashForm mainSashForm;
	private boolean hasFocus;
	private boolean dragActive;

	// Support data structures and classes
	private static final int UPDATE_DELAY = 300;
	private UpdatePanelJob updatePanelJob;
	private EditingAreaHelper editingAreaInfo;
	private String currentWidgetText;
	private String valueClassName;
	// Cache map of the detail panels
	private Map<String, ObjectCategoryDetailsPanel> detailPanels;
	private ObjectCategoryItem builtinFunctionsItem;
	private ObjectCategoryItem parametersCategoryItem;
	private ObjectCategoryItem fieldsCategoryItem;
	private ObjectCategoryItem variablesCategoryItem;
	private List<ObjectCategoryItem> rootCategories;

	/**
	 * Creates the expression editor composite.
	 * 
	 * @param parent the parent of the newly created composite
	 * @param style  style information of the newly created composite
	 */
	public JavaExpressionEditorComposite(Composite parent, int style) {
		super(parent, style);
		detailPanels = new HashMap<>();
		statusChangeListeners = new ArrayList<>();

		GridLayout gdl = new GridLayout(1, true);
		this.setLayout(gdl);

		mainSashForm = new SashForm(this, SWT.VERTICAL);
		GridData gdMainSash = new GridData(SWT.FILL, SWT.FILL, true, true);
		mainSashForm.setLayoutData(gdMainSash);
		mainSashForm.addDisposeListener(e -> saveMainSashWeights());

		createEditorArea(mainSashForm);

		final SashForm subSashForm = new SashForm(mainSashForm, SWT.HORIZONTAL);

		createObjectsNavigator(subSashForm);
		createCustomPanel(subSashForm);

		createBackCompatibilitySection();

		subSashForm.setWeights(new int[] { 24, 75 });
		mainSashForm.setWeights(getMainSashWeights());

		// FunctionsLibraryUtil.reloadLibraryIfNeeded();
		// FIXME - Releading everytime the library,
		// we should improved this. It could be expensive.
		FunctionsLibraryUtil.reloadLibrary();

		this.updatePanelJob = new UpdatePanelJob();
		UIUtils.getDisplay().asyncExec(() -> {
			subSashForm.setWeights(new int[] { 25, 75 });
			mainSashForm.layout(true);
			mainSashForm.update();
		});
	}

	/*
	 * Reads the details about the main sash area.
	 */
	private int[] getMainSashWeights() {
		if (ExpressionEditorSupportUtil.shouldRememberExpEditorDialogSize()) {
			IDialogSettings settings = JasperReportsPlugin.getDefault().getDialogSettings()
					.getSection(ExpressionPersistentWizardDialog.WIZARD_ID);
			if (settings != null) {
				try {
					int w1 = settings.getInt(MAIN_SASH_WEIGHT_EDITAREA_KEY);
					int w2 = settings.getInt(MAIN_SASH_WEIGHT_SELECTIONAREA_KEY);
					return new int[] { w1, w2 };
				} catch (NumberFormatException e) {
				}
			}
		}
		return new int[] { MAIN_SASH_WEIGHT_EDITAREA, MAIN_SASH_WEIGHT_SELECTIONAREA };
	}

	/*
	 * Stores information about the main sash area.
	 */
	private void saveMainSashWeights() {
		IDialogSettings settings = JasperReportsPlugin.getDefault().getDialogSettings()
				.getSection(ExpressionPersistentWizardDialog.WIZARD_ID);
		if (ExpressionEditorSupportUtil.shouldRememberExpEditorDialogSize()) {
			if (settings == null) {
				settings = JasperReportsPlugin.getDefault().getDialogSettings()
						.addNewSection(ExpressionPersistentWizardDialog.WIZARD_ID);
			}
			settings.put(MAIN_SASH_WEIGHT_EDITAREA_KEY, mainSashForm.getWeights()[0]);
			settings.put(MAIN_SASH_WEIGHT_SELECTIONAREA_KEY, mainSashForm.getWeights()[1]);
		}
	}

	/*
	 * Creates an expandable section with some back-compatibility option. Right now
	 * only the ability to set ValueClassName information (for old JR versions)
	 * makes sense.
	 */
	private void createBackCompatibilitySection() {
		Section backCompatibilitySection = new Section(this, ExpandableComposite.TREE_NODE);
		GridData backCompSectionGD = new GridData(SWT.FILL, SWT.FILL, true, false);
		backCompSectionGD.verticalIndent = 10;
		backCompatibilitySection.setLayoutData(backCompSectionGD);
		backCompatibilitySection.setLayout(new FillLayout());
		backCompatibilitySection.setText(Messages.JavaExpressionEditorComposite_BackCompatibilitySection);
		Composite composite = new Composite(backCompatibilitySection, SWT.NONE);
		composite.setLayout(new GridLayout(2, false));
		Label lbl1 = new Label(composite, SWT.NONE);
		lbl1.setText(Messages.JavaExpressionEditorComposite_ValueClassMessage);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		lbl1.setLayoutData(gd);

		valueType = new ClassType(composite, Messages.JavaExpressionEditorComposite_ClassTypeDialogTitle);
		valueType.addListener(e -> valueClassName = valueType.getClassType());
		backCompatibilitySection.setClient(composite);
	}

	/*
	 * Creates the editor area (styled text widget) and support information.
	 */
	private void createEditorArea(Composite parent) {
		Composite editorContainer = new Composite(parent, SWT.NONE);
		GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, false);
		layoutData.heightHint = 100;
		editorContainer.setLayoutData(layoutData);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		editorContainer.setLayout(layout);

		editorArea = new StyledText(editorContainer, SWT.BORDER | SWT.BORDER_SOLID | SWT.WRAP | SWT.V_SCROLL);

		new UndoRedoImpl(editorArea);
		GridData editorAreaGD = new GridData(SWT.FILL, SWT.FILL, true, true);
		editorAreaGD.widthHint = 500;
		editorArea.setLayoutData(editorAreaGD);
		editorArea.addModifyListener(e -> performUpdate());
		editorArea.addCaretListener(event -> performUpdate());
		editorArea.addPaintListener(e -> {
			if (!hasFocus && !dragActive)
				editingAreaInfo.drawFakeCursor();
		});
		DropTarget dropTarget = new DropTarget(editorArea, DND.DROP_DEFAULT | DND.DROP_COPY);
		dropTarget.setTransfer(new Transfer[] { TextTransfer.getInstance() });
		dropTarget.addDropListener(new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetEvent e) {
				dragActive = true;
				if (e.detail == DND.DROP_DEFAULT) {
					e.detail = DND.DROP_COPY;
				}
				// triggering redraw for cleaning dirty cursor
				editorArea.redraw();
			}

			@Override
			public void dragLeave(DropTargetEvent event) {
				dragActive = false;
			}

			@Override
			public void drop(DropTargetEvent event) {
				editorArea.insert((String) event.data);
				dragActive = false;
			}
		});

		xtextAdapter = new StyledTextXtextAdapter2(getInjector());
		xtextAdapter.adapt(editorArea, exprContext);

		editingAreaInfo = new EditingAreaHelper(xtextAdapter, editorArea);
		editingAreaInfo.addCategorySelectionListener(event -> performCategorySelection(event.selectedCategory));
		editorArea.addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				hasFocus = false;
				editingAreaInfo.ignoreAutoEditStrategies(true);
				editingAreaInfo.drawFakeCursor();
			}

			@Override
			public void focusGained(FocusEvent e) {
				hasFocus = true;
				editingAreaInfo.ignoreAutoEditStrategies(false);
				// dirty-trick to avoid painted "fake cursors" left on the widget
				editorArea.redraw();
			}
		});

		// Enable context menu on the styled text
		UIUtil.enableCopyPasteCutContextMenu(editorArea);
		new MenuItem(editorArea.getMenu(), SWT.SEPARATOR);
		final MenuItem addUserDefinedExprItem = new MenuItem(editorArea.getMenu(), SWT.PUSH);
		addUserDefinedExprItem.setText(Messages.JavaExpressionEditorComposite_AddCustomExpressionItemText);
		UIUtil.safeApplyMenuItemTooltip(addUserDefinedExprItem,
				Messages.JavaExpressionEditorComposite_AddCustomExpressionItemTooltip);
		addUserDefinedExprItem.setImage(ResourceManager.getImage(BundleCommonUtils
				.getImageDescriptor(JRExpressionsUIPlugin.PLUGIN_ID, "/resources/icons/expression_obj.gif"))); //$NON-NLS-1$
		addUserDefinedExprItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String selectionText = editorArea.getSelectionText();
				if (!selectionText.isEmpty()) {
					ExpressionEditorPreferencePage.addUserDefinedExpression(selectionText);
					// trigger update
					String tmpKey = Category.USER_DEFINED_EXPRESSIONS.getDisplayName() + "_" //$NON-NLS-1$
							+ Category.USER_DEFINED_EXPRESSIONS.getDisplayName();
					ObjectCategoryDetailsPanel tmpControl = detailPanels.get(tmpKey);
					if (detailsPanelStackLayout.topControl.equals(tmpControl)) {
						tmpControl.refreshPanelUI(new ObjectCategoryItem(Category.USER_DEFINED_EXPRESSIONS), true);
					} else {
						if (tmpControl != null) {
							tmpControl.dispose();
							detailPanels.remove(tmpKey);
						}
					}
				}
			}
		});
		editorArea.getMenu().addMenuListener(new MenuAdapter() {

			@Override
			public void menuShown(MenuEvent e) {
				addUserDefinedExprItem.setEnabled(!editorArea.getSelectionText().isEmpty());
			}

		});
	}

	/*
	 * Creates the categories tree navigator.
	 */
	private void createObjectsNavigator(Composite parent) {
		objectsNavigator = new TreeViewer(parent, SWT.BORDER);
		objectsNavigator.setContentProvider(new ObjectsNavigatorContentProvider());
		objectsNavigator.setLabelProvider(new ObjectsNavigatorLabelProvider());

		objectsNavigator.addSelectionChangedListener(event -> {
			Object selItem = ((IStructuredSelection) event.getSelection()).getFirstElement();
			if (selItem instanceof ObjectCategoryItem)
				updateDetailsPanel((ObjectCategoryItem) selItem);
		});
	}

	/*
	 * Creates the additional panel that will contain details on the selected
	 * category.
	 */
	private void createCustomPanel(Composite parent) {
		objectCategoryDetailsCmp = new Composite(parent, SWT.NONE);
		objectCategoryDetailsCmp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		detailsPanelStackLayout = new StackLayout();
		objectCategoryDetailsCmp.setLayout(detailsPanelStackLayout);
	}

	@Override
	public void setExpressionContext(ExpressionContext exprContext) {
		this.exprContext = exprContext;
		this.xtextAdapter.configureExpressionContext(this.exprContext);
		refreshExpressionContextUI();
	}

	@Override
	public JRExpression getExpression() {
		if ("".equals(currentWidgetText)) { //$NON-NLS-1$
			expression = null;
		} else {
			expression = new JRDesignExpression(currentWidgetText);
			expression.setValueClassName(valueClassName);
		}
		return expression;
	}

	@Override
	public void setExpression(JRExpression expression) {
		this.expression = (JRDesignExpression) expression;
		if (this.expression == null) {
			editorArea.setText(""); //$NON-NLS-1$
			valueType.setClassType(null);
		} else {
			editorArea.setText(expression.getText());
			editorArea.selectAll();
			valueType.setClassType(this.expression.getValueClassName());
		}
		updateExpressionStatus();
	}

	/*
	 * Get the injector for the JavaJRE language.
	 */
	private Injector getInjector() {
		JRExpressionsActivator activator = JRExpressionsActivator.getInstance();
		return activator
				.getInjector(JRExpressionsActivator.COM_JASPERSOFT_STUDIO_EDITOR_JREXPRESSIONS_JAVAJREXPRESSION);
	}

	/*
	 * Update the composite UI once the expression context is set.
	 */
	private void refreshExpressionContextUI() {
		// Builds the list of main categories
		rootCategories = new ArrayList<>();
		if (exprContext != null) {
			List<JRDesignDataset> contextDatasets = exprContext.getDatasets();
			if (contextDatasets.size() == 1) {
				parametersCategoryItem = new ObjectCategoryItem(Category.PARAMETERS);
				parametersCategoryItem.setData(ExpressionContextUtils.getAllDatasetsParameters(exprContext));
				fieldsCategoryItem = new ObjectCategoryItem(Category.FIELDS);
				fieldsCategoryItem.setData(ExpressionContextUtils.getAllDatasetsFields(exprContext));
				variablesCategoryItem = new ObjectCategoryItem(Category.VARIABLES);
				variablesCategoryItem.setData(ExpressionContextUtils.getAllDatasetsVariables(exprContext));

				if (exprContext.canShowParameters()) {
					rootCategories.add(parametersCategoryItem);
				}
				if (exprContext.canShowFields()) {
					rootCategories.add(fieldsCategoryItem);
				}
				if (exprContext.canShowVariables()) {
					rootCategories.add(variablesCategoryItem);
				}
			} else if (contextDatasets.size() > 1) {
				List<ObjectCategoryItem> paramsDatasets = new ArrayList<>();
				List<ObjectCategoryItem> fieldsDatasets = new ArrayList<>();
				List<ObjectCategoryItem> variablesDatasets = new ArrayList<>();
				for (JRDesignDataset ds : contextDatasets) {
					String dsname = ds.getName();
					if (ds.isMainDataset()) {
						dsname = Messages.JavaExpressionEditorComposite_MainDatasetLabel;
					}
					// all parameters for the dataset
					ObjectCategoryItem pItems = new ObjectCategoryItem(Category.PDATASET, dsname);
					pItems.setData(ExpressionContextUtils.getDatasetParameters(exprContext, ds));
					paramsDatasets.add(pItems);
					// all fields for the dataset
					ObjectCategoryItem fItems = new ObjectCategoryItem(Category.FDATASET, dsname);
					fItems.setData(ExpressionContextUtils.getDatasetFields(exprContext, ds));
					fieldsDatasets.add(fItems);
					// all variables for the dataset
					ObjectCategoryItem vItems = new ObjectCategoryItem(Category.VDATASET, dsname);
					vItems.setData(ExpressionContextUtils.getDatasetVariables(exprContext, ds));
					variablesDatasets.add(vItems);
				}
				parametersCategoryItem = new ObjectCategoryItem(Category.PARAMETERS);
				parametersCategoryItem.setData(paramsDatasets.toArray(new ObjectCategoryItem[paramsDatasets.size()]));
				fieldsCategoryItem = new ObjectCategoryItem(Category.FIELDS);
				fieldsCategoryItem.setData(fieldsDatasets.toArray(new ObjectCategoryItem[fieldsDatasets.size()]));
				variablesCategoryItem = new ObjectCategoryItem(Category.VARIABLES);
				variablesCategoryItem
						.setData(variablesDatasets.toArray(new ObjectCategoryItem[variablesDatasets.size()]));
				rootCategories.add(parametersCategoryItem);
				rootCategories.add(fieldsCategoryItem);
				rootCategories.add(variablesCategoryItem);
			}

			// Add resource keys category
			ObjectCategoryItem resourceKeysCategoryItem = new ObjectCategoryItem(Category.RESOURCE_KEYS);
			resourceKeysCategoryItem.setData(ExpressionContextUtils.getResourceBundleKeys(exprContext));
			rootCategories.add(resourceKeysCategoryItem);

			int i = 0;
			for (JRDesignCrosstab crosstab : exprContext.getCrosstabs()) {
				i++;
				String crosstabKey = crosstab.getKey();
				if (crosstabKey == null)
					crosstabKey = ""; //$NON-NLS-1$

				ObjectCategoryItem tmpCrossTabItem = new ObjectCategoryItem(Category.CROSSTAB,
						Category.CROSSTAB.getDisplayName() + " (" + i + ") " + crosstabKey); //$NON-NLS-1$ //$NON-NLS-2$
				tmpCrossTabItem.setData(crosstab);
				rootCategories.add(tmpCrossTabItem);

			}

		}
		builtinFunctionsItem = new ObjectCategoryItem(Category.BUILT_IN_FUNCTIONS);
		// Get all categories for builtin functions
		List<ObjectCategoryItem> functionCategories = new ArrayList<>();
		for (String categoryKey : FunctionsLibraryUtil.getCategories()) {
			JRExprFunctionCategoryBean category = FunctionsLibraryUtil.getCategory(categoryKey);
			ObjectCategoryItem objectCategoryItem = new ObjectCategoryItem(Category.FUNCTION_CATEGORY,
					category.getName());
			objectCategoryItem.setData(categoryKey);
			functionCategories.add(objectCategoryItem);
		}

		// Get all additional static functions
		functionCategories.addAll(getAdditionalStaticFunctions());

		// Let's order alphabetically the categories
		Collections.sort(functionCategories, new Comparator<ObjectCategoryItem>() {
			@Override
			public int compare(ObjectCategoryItem arg0, ObjectCategoryItem arg1) {
				return arg0.getDisplayName().compareTo(arg1.getDisplayName());
			}
		});

		if (!functionCategories.isEmpty()) {
			builtinFunctionsItem.setData(functionCategories.toArray(new ObjectCategoryItem[functionCategories.size()]));
		}

		rootCategories.add(builtinFunctionsItem);

		rootCategories.add(new ObjectCategoryItem(Category.USER_DEFINED_EXPRESSIONS));
		rootCategories.add(new ObjectCategoryItem(Category.RECENT_EXPRESSIONS));

		objectsNavigator.setInput(rootCategories.toArray(new ObjectCategoryItem[rootCategories.size()]));
		objectsNavigator.expandToLevel(builtinFunctionsItem, 2);
		performCategorySelection(null);
	}

	private Collection<? extends ObjectCategoryItem> getAdditionalStaticFunctions() {
		ObjectCategoryItem i18nFunctions = new ObjectCategoryItem(Category.STATIC_FUNCTION_CATEGORY,
				Messages.JavaExpressionEditorComposite_MessageBundlesCategory);
		i18nFunctions.setData(AdditionalStaticFunctions.getMessageBundleFuntions());
		return Arrays.asList(i18nFunctions);
	}

	public void addExpressionStatusChangeListener(IExpressionStatusChangeListener listener) {
		statusChangeListeners.add(listener);
	}

	public void removeExpressionStatusChangeListener(IExpressionStatusChangeListener listener) {
		statusChangeListeners.remove(listener);
	}

	public void notifyExpressionStatusChanged(ExpressionStatus status) {
		for (IExpressionStatusChangeListener l : statusChangeListeners) {
			l.statusChanged(status);
		}
	}

	/*
	 * Job to update the panel UI when expression text changes or when caret is
	 * moved. This job is supposed to be delayed in order not to call UI-update
	 * events too often (avoiding flickering effects).
	 */
	private class UpdatePanelJob extends WorkbenchJob {

		public UpdatePanelJob() {
			super(Messages.JavaExpressionEditorComposite_JobName);
			setSystem(true);
		}

		@Override
		public IStatus runInUIThread(IProgressMonitor monitor) {
			if (!isDisposed()) {
				monitor.beginTask(Messages.JavaExpressionEditorComposite_TaskName, IProgressMonitor.UNKNOWN);
				synchCurrentFunctionDetails();
				updateExpressionStatus();
				monitor.done();
				return Status.OK_STATUS;
			} else {
				return Status.CANCEL_STATUS;
			}
		}
	}

	/* Listeners utility methods */

	/*
	 * Updates the details panel once the selected tree category item changes. A
	 * StackLayout is used in order to cache the details composites. If needed the
	 * control is created, otherwise it is simply set as top control.
	 */
	private void updateDetailsPanel(ObjectCategoryItem selItem) {
		String key = selItem.getCategory().getDisplayName() + "_" //$NON-NLS-1$
				+ selItem.getDisplayName();
		ObjectCategoryDetailsPanel currentControl = detailPanels.get(key);
		if (currentControl == null) {
			// First time, must create control
			currentControl = new ObjectCategoryDetailsPanel(objectCategoryDetailsCmp, SWT.NONE);
			currentControl.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			currentControl.setExpressionContext(exprContext);
			currentControl.setEditingAreaInfo(editingAreaInfo);

			detailPanels.put(key, currentControl);
		}

		// Ensure all other controls are not visible
		Control[] children = objectCategoryDetailsCmp.getChildren();
		for (int i = 0; i < children.length; i++) {
			if (children[i] != currentControl) {
				children[i].setVisible(false);
			}
		}
		// Make the current selected one visible
		currentControl.setVisible(true);
		currentControl.refreshPanelUI(selItem);
		detailsPanelStackLayout.topControl = currentControl;
		objectCategoryDetailsCmp.layout();
	}

	/*
	 * Forces the category selection in the related tree. If no category is
	 * specified, the following selection order is applied: 1) Fields, if any 2)
	 * Variables, if any 3) Parameters
	 */
	private void performCategorySelection(Category category) {
		if (category == null) {
			if (!ExpressionContextUtils.getAllDatasetsFields(exprContext).isEmpty()) {
				objectsNavigator.expandToLevel(fieldsCategoryItem, 1);
				objectsNavigator.setSelection(new StructuredSelection(fieldsCategoryItem), true);
			} else if (!ExpressionContextUtils.getAllDatasetsVariables(exprContext).isEmpty()) {
				objectsNavigator.expandToLevel(variablesCategoryItem, 1);
				objectsNavigator.setSelection(new StructuredSelection(variablesCategoryItem), true);
			} else if (!ExpressionContextUtils.getAllDatasetsParameters(exprContext).isEmpty()) {
				objectsNavigator.expandToLevel(parametersCategoryItem, 1);
				objectsNavigator.setSelection(new StructuredSelection(parametersCategoryItem), true);
			} else {
				objectsNavigator.expandToLevel(rootCategories.get(0), 1);
				objectsNavigator.setSelection(new StructuredSelection(rootCategories.get(0)), true);
			}
			return;
		}
		// Choose the right category
		for (TreeItem item : objectsNavigator.getTree().getItems()) {
			Object cat = item.getData();
			if (cat instanceof ObjectCategoryItem && ((ObjectCategoryItem) cat).getCategory().equals(category)) {
				objectsNavigator.setSelection(null);
				objectsNavigator.setSelection(new StructuredSelection(cat), true);
				return;
			}
		}
	}

	/*
	 * Tries to select a specific function, depending on the currently "selected"
	 * one in the editing area (i.e: based on cursor position). This involves also
	 * the update of the details panel area.
	 */
	private void synchCurrentFunctionDetails() {
		if (editingAreaInfo.isUpdate())
			return;
		final String functName = editingAreaInfo.getCurrentLibraryFunctionName();
		if (functName != null) {
			objectsNavigator.setSelection(new StructuredSelection(builtinFunctionsItem), true);
		} else {
			Object selElement = ((IStructuredSelection) objectsNavigator.getSelection()).getFirstElement();
			objectsNavigator.setSelection(new StructuredSelection(selElement), true);
		}
	}

	/*
	 * Check and update the status of the current expression being edited.
	 */
	private void updateExpressionStatus() {
		if (editorArea.getText().equals("")) { //$NON-NLS-1$
			// Do not care about empty expression(s)
			ExpressionStatus exprStatus = ExpressionStatus.INFO;
			exprStatus.setShortDescription(Messages.JavaExpressionEditorComposite_NoValidationIssuesInfo);
			notifyExpressionStatusChanged(exprStatus);
			return;
		}

		List<Issue> validationIssues = xtextAdapter.getXtextValidationIssues();
		if (validationIssues != null && !validationIssues.isEmpty()) {
			// let's relax the message and use a warning
			ExpressionStatus exprStatus = ExpressionStatus.WARNING;
			for (Issue vi : validationIssues) {
				exprStatus.getMessages().add(vi.getMessage());
			}
			exprStatus.setShortDescription(Messages.JavaExpressionEditorComposite_ExpressionInvalidError);
			notifyExpressionStatusChanged(exprStatus);
		} else {
			ExpressionStatus exprStatus = ExpressionStatus.INFO;
			exprStatus.setShortDescription(Messages.JavaExpressionEditorComposite_NoValidationIssuesInfo);
			notifyExpressionStatusChanged(exprStatus);
		}
	}

	/*
	 * Update UI when editing area is modified (text modification/caret movement).
	 */
	private void performUpdate() {
		currentWidgetText = editorArea.getText();
		if (editorArea.isFocusControl()) {
			updatePanelJob.cancel();
			updatePanelJob.schedule(UPDATE_DELAY);
		} else {
			synchCurrentFunctionDetails();
			updateExpressionStatus();
		}
	}

	@Override
	public void dispose() {
		if (updatePanelJob != null) {
			updatePanelJob.cancel();
			updatePanelJob = null;
		}
		super.dispose();
	}

}
