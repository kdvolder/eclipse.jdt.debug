package org.eclipse.jdt.internal.debug.ui.actions;/* * (c) Copyright IBM Corp. 2000, 2001. * All Rights Reserved. */ import java.lang.reflect.InvocationTargetException;import java.util.List;import org.eclipse.core.resources.ResourcesPlugin;import org.eclipse.core.runtime.CoreException;import org.eclipse.core.runtime.IProgressMonitor;import org.eclipse.debug.core.DebugPlugin;import org.eclipse.debug.core.IBreakpointManager;import org.eclipse.debug.core.model.IBreakpoint;import org.eclipse.jdt.core.IType;import org.eclipse.jdt.core.ITypeHierarchy;import org.eclipse.jdt.core.JavaModelException;import org.eclipse.jdt.core.search.SearchEngine;import org.eclipse.jdt.debug.core.IJavaExceptionBreakpoint;import org.eclipse.jdt.debug.core.JDIDebugModel;import org.eclipse.jdt.internal.corext.util.JavaModelUtil;import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;import org.eclipse.jdt.internal.ui.dialogs.FilteredList;import org.eclipse.jdt.internal.ui.dialogs.StatusDialog;import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;import org.eclipse.jdt.internal.ui.util.AllTypesSearchEngine;import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;import org.eclipse.jdt.internal.ui.util.TypeInfo;import org.eclipse.jdt.internal.ui.util.TypeInfoLabelProvider;import org.eclipse.jdt.ui.IJavaElementSearchConstants;import org.eclipse.jface.dialogs.IDialogSettings;import org.eclipse.jface.dialogs.ProgressMonitorDialog;import org.eclipse.jface.operation.IRunnableWithProgress;import org.eclipse.swt.SWT;import org.eclipse.swt.events.SelectionAdapter;import org.eclipse.swt.events.SelectionEvent;import org.eclipse.swt.events.SelectionListener;import org.eclipse.swt.layout.GridData;import org.eclipse.swt.layout.GridLayout;import org.eclipse.swt.widgets.Button;import org.eclipse.swt.widgets.Composite;import org.eclipse.swt.widgets.Control;import org.eclipse.swt.widgets.Event;import org.eclipse.swt.widgets.Label;import org.eclipse.swt.widgets.Listener;import org.eclipse.swt.widgets.Shell;import org.eclipse.swt.widgets.Text;/** * A dialog to select an exception type to add as an exception breakpoint. */public class AddExceptionDialog extends StatusDialog {		private static final String DIALOG_SETTINGS= "AddExceptionDialog"; //$NON-NLS-1$	private static final String SETTING_CAUGHT_CHECKED= "caughtChecked"; //$NON-NLS-1$	private static final String SETTING_UNCAUGHT_CHECKED= "uncaughtChecked"; //$NON-NLS-1$	private Text fFilterText;	private FilteredList fTypeList;	private boolean fTypeListInitialized= false;		private Button fCaughtBox;	private Button fUncaughtBox;		public static final int CHECKED_EXCEPTION= 0;	public static final int UNCHECKED_EXCEPTION= 1;	public static final int NO_EXCEPTION= -1;		private SelectionListener fListListener= new SelectionAdapter() {		public void widgetSelected(SelectionEvent evt) {			validateListSelection();		}				public void widgetDefaultSelected(SelectionEvent e) {			validateListSelection();			if (getStatus().isOK()) {				okPressed();			}		}	};			private Object fResult;	private int fExceptionType= NO_EXCEPTION;	private boolean fIsCaughtSelected= true;	private boolean fIsUncaughtSelected= true;		public AddExceptionDialog(Shell parentShell) {		super(parentShell);		setTitle(ActionMessages.getString("AddExceptionDialog.title")); //$NON-NLS-1$	}		protected Control createDialogArea(Composite ancestor) {		initFromDialogSettings();		Composite parent= new Composite(ancestor, SWT.NULL);		GridLayout layout= new GridLayout();		parent.setLayout(layout);				Label l= new Label(parent, SWT.NULL);		l.setLayoutData(new GridData());		l.setText(ActionMessages.getString("AddExceptionDialog.message")); //$NON-NLS-1$		setFilterText(new Text(parent, SWT.BORDER));				GridData data= new GridData();		data.grabExcessVerticalSpace= false;		data.grabExcessHorizontalSpace= true;		data.horizontalAlignment= GridData.FILL;		data.verticalAlignment= GridData.BEGINNING;		getFilterText().setLayoutData(data);						Listener listener= new Listener() {			public void handleEvent(Event e) {				getTypeList().setFilter(getFilterText().getText());			}		};				getFilterText().addListener(SWT.Modify, listener);												setTypeList(new FilteredList(parent, SWT.BORDER | SWT.SINGLE, 				new TypeInfoLabelProvider(TypeInfoLabelProvider.SHOW_PACKAGE_POSTFIX),				true, true, true));		GridData gd= new GridData(GridData.FILL_BOTH);		gd.widthHint= convertWidthInCharsToPixels(65);		gd.heightHint= convertHeightInCharsToPixels(20);		getTypeList().setLayoutData(gd);								setCaughtBox(new Button(parent, SWT.CHECK));		getCaughtBox().setLayoutData(new GridData());		getCaughtBox().setText(ActionMessages.getString("AddExceptionDialog.caught")); //$NON-NLS-1$		getCaughtBox().setSelection(fIsCaughtSelected);				setUncaughtBox(new Button(parent, SWT.CHECK));		getUncaughtBox().setLayoutData(new GridData());		// fix: 1GEUWCI: ITPDUI:Linux - Add Exception box has confusing checkbox		getUncaughtBox().setText(ActionMessages.getString("AddExceptionDialog.uncaught")); //$NON-NLS-1$		// end fix.		getUncaughtBox().setSelection(isUncaughtSelected());				addFromListSelected(true);		return parent;	}			protected void addFromListSelected(boolean selected) {		getTypeList().setEnabled(selected);		if (selected) {			if (!isTypeListInitialized()) {				initializeTypeList();				if (!isTypeListInitialized()) {					return; //cancelled				}			}			getTypeList().addSelectionListener(getListListener());			validateListSelection();		} else {			getTypeList().removeSelectionListener(getListListener());		}	}		protected void okPressed() {		TypeInfo typeRef= (TypeInfo)getTypeList().getSelection()[0];		IType resolvedType= getResolvedType(typeRef);		if (resolvedType == null) {			return;		}				setIsCaughtSelected(getCaughtBox().getSelection());		setIsUncaughtSelected(getUncaughtBox().getSelection());		setResult(resolvedType);				saveDialogSettings();				super.okPressed();	}		protected IType getResolvedType(TypeInfo typeRef) {		IType resolvedType= null;		try {			resolvedType= typeRef.resolveType(SearchEngine.createWorkspaceScope());		} catch (JavaModelException e) {			updateStatus(e.getStatus());					}		return resolvedType;	}	private int getExceptionType(final IType type) {		final int[] result= new int[] { NO_EXCEPTION };			BusyIndicatorRunnableContext context= new BusyIndicatorRunnableContext();		IRunnableWithProgress runnable= new IRunnableWithProgress() {			public void run(IProgressMonitor pm) {				try {					ITypeHierarchy hierarchy= type.newSupertypeHierarchy(pm);					IType curr= type;					while (curr != null) {						String name= JavaModelUtil.getFullyQualifiedName(curr);												if ("java.lang.Throwable".equals(name)) { //$NON-NLS-1$							result[0]= CHECKED_EXCEPTION;							return;						}						if ("java.lang.RuntimeException".equals(name) || "java.lang.Error".equals(name)) { //$NON-NLS-2$ //$NON-NLS-1$							result[0]= UNCHECKED_EXCEPTION;							return;						}						curr= hierarchy.getSuperclass(curr);					}				} catch (JavaModelException e) {					JDIDebugUIPlugin.log(e);				}			}		};		try {					context.run(false, false, runnable);		} catch (InterruptedException e) {		} catch (InvocationTargetException e) {			JDIDebugUIPlugin.log(e);		}		return result[0];	}		public Object getResult() {		return fResult;	}		public int getExceptionType() {		return fExceptionType;	}		public boolean isCaughtSelected() {		return fIsCaughtSelected;	}		public boolean isUncaughtSelected() {		return fIsUncaughtSelected;	}		protected void initializeTypeList() {		AllTypesSearchEngine searchEngine= new AllTypesSearchEngine(ResourcesPlugin.getWorkspace());		int flags= IJavaElementSearchConstants.CONSIDER_BINARIES |					IJavaElementSearchConstants.CONSIDER_CLASSES |					IJavaElementSearchConstants.CONSIDER_EXTERNAL_JARS;		ProgressMonitorDialog dialog= new ProgressMonitorDialog(getShell());		final List result= searchEngine.searchTypes(dialog, SearchEngine.createWorkspaceScope(), flags);		if (dialog.getReturnCode() == dialog.CANCEL) {			getShell().getDisplay().asyncExec( 				new Runnable() {					public void run() {						cancelPressed();					}				}			);			return;		}		getFilterText().setText("*Exception*"); //$NON-NLS-1$				BusyIndicatorRunnableContext context= new BusyIndicatorRunnableContext();		IRunnableWithProgress runnable= new IRunnableWithProgress() {			public void run(IProgressMonitor pm) {				getTypeList().setElements(result.toArray()); // XXX inefficient			}		};		try {					context.run(false, false, runnable);		} catch (InterruptedException e) {		} catch (InvocationTargetException e) {			JDIDebugUIPlugin.log(e);		}				setTypeListInitialized(true);	}		private void validateListSelection() {		StatusInfo status= new StatusInfo();		if (fTypeList.getSelection().length != 1) {			status.setError(ActionMessages.getString("AddExceptionDialog.error.noSelection"));  //$NON-NLS-1$			updateStatus(status);			return;		}		TypeInfo typeRef= (TypeInfo)getTypeList().getSelection()[0];		IType type = getResolvedType(typeRef);		String modelId= JDIDebugModel.getPluginIdentifier();		IBreakpointManager manager= DebugPlugin.getDefault().getBreakpointManager();		IBreakpoint[] breakpoints= manager.getBreakpoints(modelId);		for (int i = 0; i < breakpoints.length; i++) {			if (!(breakpoints[i] instanceof IJavaExceptionBreakpoint)) {				continue;			}			IJavaExceptionBreakpoint breakpoint = (IJavaExceptionBreakpoint) breakpoints[i];			try {				if (breakpoint.getType().equals(type)) {					status.setError(ActionMessages.getString("AddExceptionDialog.The_selected_class_has_an_existing_exception_breakpoint_1")); //$NON-NLS-1$				}			} catch (CoreException ce) {				updateStatus(ce.getStatus());				return;			}		}				setExceptionType(getExceptionType(type));		if (getExceptionType() == NO_EXCEPTION) {			status.setError(ActionMessages.getString("AddExceptionDialog.error.notThrowable"));  //$NON-NLS-1$		}		updateStatus(status);	}		private void initFromDialogSettings() {		IDialogSettings allSetttings= JDIDebugUIPlugin.getDefault().getDialogSettings();		IDialogSettings section= allSetttings.getSection(DIALOG_SETTINGS);		if (section == null) {			section= allSetttings.addNewSection(DIALOG_SETTINGS);			section.put(SETTING_CAUGHT_CHECKED, true);			section.put(SETTING_UNCAUGHT_CHECKED, true);		}		setIsCaughtSelected(section.getBoolean(SETTING_CAUGHT_CHECKED));		setIsUncaughtSelected(section.getBoolean(SETTING_UNCAUGHT_CHECKED));	}		private void saveDialogSettings() {		IDialogSettings allSetttings= JDIDebugUIPlugin.getDefault().getDialogSettings();		IDialogSettings section= allSetttings.getSection(DIALOG_SETTINGS);		// won't be null since we initialize it in the method above.		section.put(SETTING_CAUGHT_CHECKED, isCaughtSelected());		section.put(SETTING_UNCAUGHT_CHECKED, isUncaughtSelected());	}		public void create() {		super.create();		getFilterText().selectAll();		getFilterText().setFocus();	}		protected Button getCaughtBox() {		return fCaughtBox;	}	protected void setCaughtBox(Button caughtBox) {		fCaughtBox = caughtBox;	}	protected void setExceptionType(int exceptionType) {		fExceptionType = exceptionType;	}	protected Text getFilterText() {		return fFilterText;	}	protected void setFilterText(Text filterText) {		fFilterText = filterText;	}	protected void setIsCaughtSelected(boolean isCaughtSelected) {		fIsCaughtSelected = isCaughtSelected;	}	protected void setIsUncaughtSelected(boolean isUncaughtSelected) {		fIsUncaughtSelected = isUncaughtSelected;	}	protected SelectionListener getListListener() {		return fListListener;	}	protected void setListListener(SelectionListener listListener) {		fListListener = listListener;	}	protected void setResult(Object result) {		fResult = result;	}	protected FilteredList getTypeList() {		return fTypeList;	}	protected void setTypeList(FilteredList typeList) {		fTypeList = typeList;	}	protected boolean isTypeListInitialized() {		return fTypeListInitialized;	}	protected void setTypeListInitialized(boolean typeListInitialized) {		fTypeListInitialized = typeListInitialized;	}	protected Button getUncaughtBox() {		return fUncaughtBox;	}	protected void setUncaughtBox(Button uncaughtBox) {		fUncaughtBox = uncaughtBox;	}}