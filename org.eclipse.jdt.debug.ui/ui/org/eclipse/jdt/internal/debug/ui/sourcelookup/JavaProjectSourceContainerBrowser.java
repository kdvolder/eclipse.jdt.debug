/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.debug.ui.sourcelookup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector;
import org.eclipse.debug.ui.sourcelookup.AbstractSourceContainerBrowser;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.ui.IJavaDebugUIConstants;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.internal.debug.ui.actions.ProjectSelectionDialog;
import org.eclipse.jdt.internal.launching.ClasspathContainerSourceContainer;
import org.eclipse.jdt.internal.launching.ClasspathVariableSourceContainer;
import org.eclipse.jdt.internal.launching.JavaProjectSourceContainer;
import org.eclipse.jdt.internal.launching.PackageFragmentRootSourceContainer;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;

/**
 * Browser to select Java projects to add to the source lookup path.
 * 
 * @since 3.0
 */
public class JavaProjectSourceContainerBrowser extends AbstractSourceContainerBrowser {
	
	class ContentProvider implements IStructuredContentProvider {
		
		private List fProjects;
		
		public ContentProvider(List projects) {
			fProjects = projects;
		}
		
		/**
		 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
		 */
		public Object[] getElements(Object inputElement) {
			return fProjects.toArray();
		}

		/**
		 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
		 */
		public void dispose() {
		}

		/**
		 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
		 */
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

	}		
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.sourcelookup.ISourceContainerBrowser#createSourceContainers(org.eclipse.swt.widgets.Shell, org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public ISourceContainer[] addSourceContainers(Shell shell, ISourceLookupDirector director) {
		List projects = getPossibleAdditions(director);
		
		ILabelProvider labelProvider= new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT);
		IStructuredContentProvider content = new ContentProvider(projects);
		ProjectSelectionDialog dialog= new ProjectSelectionDialog(shell, projects, content, labelProvider, SourceLookupMessages.getString("JavaProjectSourceContainerBrowser.0")); //$NON-NLS-1$
		dialog.setTitle(SourceLookupMessages.getString("JavaProjectSourceContainerBrowser.1")); //$NON-NLS-1$
		MultiStatus status = new MultiStatus(JDIDebugUIPlugin.getUniqueIdentifier(), IJavaDebugUIConstants.INTERNAL_ERROR, SourceLookupMessages.getString("JavaProjectSourceContainerBrowser.2"), null); //$NON-NLS-1$
				
		List sourceContainers = new ArrayList();
		if (dialog.open() == Window.OK) {			
			Object[] selections = dialog.getResult();
			List additions = new ArrayList(selections.length);
			try {
				for (int i = 0; i < selections.length; i++) {
					IJavaProject jp = (IJavaProject)selections[i];
					if (dialog.isAddRequiredProjects()) {
						collectRequiredProjects(jp, additions);
					} else {
						additions.add(jp);
					}
				}
			} catch (JavaModelException e) {
				status.add(e.getStatus());
			}
			
			Iterator iter = additions.iterator();
			while (iter.hasNext()) {
				IJavaProject jp = (IJavaProject)iter.next();
				sourceContainers.add(new JavaProjectSourceContainer(jp));
				if (dialog.isAddExportedEntries()) {
					try {
						collectExportedEntries(jp, sourceContainers);
					} catch (CoreException e) {
						status.add(e.getStatus());
					}
				}
			}
		}	
		
		content.dispose();
		labelProvider.dispose();			
		
		if (!status.isOK()) {
			JDIDebugUIPlugin.errorDialog(status.getMessage(), status);
		}
		return (ISourceContainer[])sourceContainers.toArray(new ISourceContainer[sourceContainers.size()]);
	}

	/**
	 * Returns the possible projects that can be added
	 * 
	 * @param director the source lookup director currently being edited
	 */
	protected List getPossibleAdditions(ISourceLookupDirector director) {
		IJavaProject[] projects;
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		try {
			projects= JavaCore.create(root).getJavaProjects();
		} catch (JavaModelException e) {
			JDIDebugUIPlugin.log(e);
			projects= new IJavaProject[0];
		}
		List remaining = new ArrayList();
		for (int i = 0; i < projects.length; i++) {
			remaining.add(projects[i]);
		}
		List alreadySelected = new ArrayList();
		ISourceContainer[] containers = director.getSourceContainers();
		for (int i = 0; i < containers.length; i++) {
			ISourceContainer container = containers[i];
			if (container.getType().getId().equals(JavaProjectSourceContainer.TYPE_ID)) {
				alreadySelected.add(((JavaProjectSourceContainer)container).getJavaProject());
			}
		}
		remaining.removeAll(alreadySelected);
		return remaining;		
	}

	/**
	 * Adds all projects required by <code>proj</code> to the list
	 * <code>res</code>
	 * 
	 * @param proj the project for which to compute required
	 *  projects
	 * @param res the list to add all required projects too
	 */
	protected void collectRequiredProjects(IJavaProject proj, List res) throws JavaModelException {
		if (!res.contains(proj)) {
			res.add(proj);
			
			IJavaModel model= proj.getJavaModel();
			
			IClasspathEntry[] entries= proj.getRawClasspath();
			for (int i= 0; i < entries.length; i++) {
				IClasspathEntry curr= entries[i];
				if (curr.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
					IJavaProject ref= model.getJavaProject(curr.getPath().segment(0));
					if (ref.exists()) {
						collectRequiredProjects(ref, res);
					}
				}
			}
		}
	}		
	
	/**
	 * Adds all exported entries defined by <code>proj</code> to the list
	 * <code>list</code>.
	 * 
	 * @param proj
	 * @param list
	 * @throws JavaModelException
	 */
	protected void collectExportedEntries(IJavaProject proj, List list) throws CoreException {
		IClasspathEntry[] entries = proj.getRawClasspath();
		for (int i = 0; i < entries.length; i++) {
			IClasspathEntry entry = entries[i];
			ISourceContainer sourceContainer = null;
			if (entry.isExported()) {
				switch (entry.getEntryKind()) {
					case IClasspathEntry.CPE_CONTAINER:
						IClasspathContainer container = JavaCore.getClasspathContainer(entry.getPath(), proj);
						sourceContainer = new ClasspathContainerSourceContainer(container.getPath());
						break;
					case IClasspathEntry.CPE_LIBRARY:
						IPackageFragmentRoot[] roots = proj.findPackageFragmentRoots(entry);
						if (roots != null) {
							sourceContainer = new PackageFragmentRootSourceContainer(roots[0]);
						}
						break;
					case IClasspathEntry.CPE_PROJECT:
						String name = entry.getPath().segment(0);
						IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
						if (p.exists()) {
							IJavaProject jp = JavaCore.create(p);
							if (jp.exists()) {
								sourceContainer = new JavaProjectSourceContainer(jp);
							}
						}
						break;
					case IClasspathEntry.CPE_VARIABLE:
						sourceContainer = new ClasspathVariableSourceContainer(entry.getPath());
						break;
					default:
						break;
				}
				if (sourceContainer != null) {
					if (!list.contains(sourceContainer)) {
						list.add(sourceContainer);
					}
				}
			}
		}
	}	
}
