/*******************************************************************************
 * Copyright (c) 2004 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.debug.core.refactoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.debug.core.IJavaBreakpoint;
import org.eclipse.jdt.debug.core.IJavaClassPrepareBreakpoint;
import org.eclipse.jdt.debug.core.IJavaExceptionBreakpoint;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaMethodBreakpoint;
import org.eclipse.jdt.debug.core.IJavaWatchpoint;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.internal.debug.ui.BreakpointUtils;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;


/**
 * Abtract change to update a breakpoint when a IType is moved or renamed.
 */
public abstract class JavaBreakpointTypeChange extends Change {
	
	public static final int TYPE_RENAME= 1;
	public static final int TYPE_MOVE= 2;
	
	private IJavaBreakpoint fBreakpoint;
	private IType fChangedType;
	private Object fArgument;
	private int fChangeType;
	private IType fDeclaringType;
	private boolean fIsEnable;
	private Map fAttributes;
	private int fHitCount;
	
	/**
	 * Create changes for each breakpoint which need to be updated for this IType rename.
	 */
	public static Change createChangesForTypeRename(IType type, String newName) throws CoreException {
		return createChangesForTypeChange(type, newName, TYPE_RENAME);
	}
	
	/**
	 * Create changes for each breakpoint which need to be updated for this IType move.
	 */
	public static Change createChangesForTypeMove(IType type, Object destination) throws CoreException {
		return createChangesForTypeChange(type, destination, TYPE_MOVE);
	}
	
	/**
	 * Create changes for each breakpoint which need to be updated for this IType change.
	 */
	private static Change createChangesForTypeChange(IType changedType, Object argument, int changeType) throws CoreException {
		List changes= new ArrayList();

		IBreakpoint[] breakpoints= DebugPlugin.getDefault().getBreakpointManager().getBreakpoints(JDIDebugModel.getPluginIdentifier());
		String typeName= changedType.getFullyQualifiedName();
		for (int i= 0; i < breakpoints.length; i++) {
			// for each breakpoint
			IBreakpoint breakpoint= breakpoints[i];
			if (breakpoint instanceof IJavaBreakpoint) {
				IJavaBreakpoint javaBreakpoint= (IJavaBreakpoint) breakpoint;
				// check the name of the type where the breakpoint is installed
				if (javaBreakpoint.getTypeName().startsWith(typeName)) {
					// if it matcheds, check the type
					if (changedType.equals(BreakpointUtils.getType((IJavaBreakpoint)breakpoint))) {
						changes.add(createChange((IJavaBreakpoint)breakpoint, changedType, argument, changeType));
					} else {
						// if it's not the type, check the inner types
						Change change= createChangesForOuterTypeChange(javaBreakpoint, changedType, changedType, argument, changeType);
						if (change != null) {
							changes.add(change);
						}
					}
				}
			}
		}
				
		return createChangeFromList(changes);
	}
	
	private static Change createChangesForOuterTypeChange(IJavaBreakpoint javaBreakpoint, IType type, IType changedType, Object argument, int changeType) throws CoreException {
		IType[] innerTypes= type.getTypes();
		String breakpointTypeName= javaBreakpoint.getTypeName();
		IType breakpointType= BreakpointUtils.getType(javaBreakpoint);
		for (int i= 0; i < innerTypes.length; i++) {
			IType innerType= innerTypes[i];
			// check the name of the type where the breakpoint is installed
			if (breakpointTypeName.startsWith(innerType.getFullyQualifiedName())) {
				// if it matcheds, check the type
				if (innerType.equals(breakpointType)) {
					return createChange(javaBreakpoint, changedType, argument, changeType);
				} else {
					// if it's not the type, check the inner types
					return createChangesForOuterTypeChange(javaBreakpoint, innerType, changedType, argument, changeType);
				}
			}
			
		}
		return null;
	}
	
	/**
	 * Take a list of Changes, and return a unique Change, a CompositeChange, or null.
	 */
	private static Change createChangeFromList(List changes) {
		int nbChanges= changes.size();
		if (nbChanges == 0) {
			return null;
		} else if (nbChanges == 1) {
			return (Change) changes.get(0);
		} else {
			return new CompositeChange("Breakpoint updates", (Change[])changes.toArray(new Change[changes.size()])); //$NON-NLS-1$
		}
	}

	/**
	 * Create a change according to type of the breakpoint.
	 */
	private static Change createChange(IJavaBreakpoint javaBreakpoint, IType changedType, Object argument, int changeType) throws CoreException {
		if (javaBreakpoint instanceof IJavaClassPrepareBreakpoint) {
			return new JavaClassPrepareBreakpointTypeChange((IJavaClassPrepareBreakpoint) javaBreakpoint, changedType, argument, changeType);
		} else if (javaBreakpoint instanceof IJavaExceptionBreakpoint) {
			return new JavaExceptionBreakpointTypeChange((IJavaExceptionBreakpoint) javaBreakpoint, changedType, argument, changeType);
		} else if (javaBreakpoint instanceof IJavaMethodBreakpoint) {
			return new JavaMethodBreakpointTypeChange((IJavaMethodBreakpoint) javaBreakpoint, changedType, argument, changeType);
		} else if (javaBreakpoint instanceof IJavaWatchpoint) {
			return new JavaWatchpointTypeChange((IJavaWatchpoint) javaBreakpoint, changedType, argument, changeType);
		} else if (javaBreakpoint instanceof IJavaLineBreakpoint) {
			return new JavaLineBreakpointTypeChange((IJavaLineBreakpoint) javaBreakpoint, changedType, argument, changeType);
		} else {
			return null;
		}
	}

	/**
	 * JavaBreakpointTypeChange constructor.
	 */
	protected JavaBreakpointTypeChange(IJavaBreakpoint breakpoint, IType modifiedType, Object argument, int changeType) throws CoreException {
		fBreakpoint= breakpoint;
		fChangedType= modifiedType;
		fArgument= argument;
		fChangeType= changeType;
		fDeclaringType= BreakpointUtils.getType(breakpoint);
		fAttributes= breakpoint.getMarker().getAttributes();
		fIsEnable= breakpoint.isEnabled();
		fHitCount= breakpoint.getHitCount();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Change#initializeValidationData(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void initializeValidationData(IProgressMonitor pm) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Change#isValid(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
		RefactoringStatus status= new RefactoringStatus();
		if (!fBreakpoint.isRegistered()) {
			status.addFatalError(getErrorMessageNoMoreExists());
		}
		return status;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Change#perform(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public Change perform(IProgressMonitor pm) throws CoreException {
		switch (fChangeType) {
			case TYPE_RENAME:
				return performTypeRename();
			case TYPE_MOVE:
				return performTypeMove();
		}
		return null;
	}
	
	private Change performTypeRename() throws CoreException {
		// Get the new type and the new 'changed' type then call the code specific to this type
		// of breakpoint.
		String oldChangedTypeName= fChangedType.getFullyQualifiedName();
		String newChangedTypeName;
		IType parent= fChangedType.getDeclaringType();
		if (parent == null) {
			newChangedTypeName= fChangedType.getPackageFragment().getElementName() + '.' + getNewName();
		} else {
			newChangedTypeName= parent.getFullyQualifiedName() + '$' + getNewName();
		}
		
		IType newChangedType;
		IType newType;
		IJavaProject project= fDeclaringType.getJavaProject();
		if (fChangedType == fDeclaringType) {
			newType= project.findType(newChangedTypeName);
			newChangedType= newType;
		} else {
			String typeNameSuffix= fDeclaringType.getFullyQualifiedName().substring(oldChangedTypeName.length());
			String newTypeName= newChangedTypeName + typeNameSuffix;
			newType= project.findType(newTypeName);
			newChangedType= project.findType(newChangedTypeName);
		}
		
		return performChange(newType, newChangedType, fChangedType.getElementName(), TYPE_RENAME);
	}
	
	private Change performTypeMove() throws CoreException {
		// Get the new type and the new 'changed' type then call the code specific to this type
		// of breakpoint.
		Object destination= getDestination();
		String newChangedTypeName;
		if (destination instanceof IPackageFragment) {
			newChangedTypeName= ((IPackageFragment)destination).getElementName() + '.' + fChangedType.getElementName();
		} else {
			newChangedTypeName= ((IType)destination).getFullyQualifiedName() + '$' + fChangedType.getElementName();
		}
		
		IType newChangedType;
		IType newType;
		IJavaProject project= fDeclaringType.getJavaProject();
		if (fChangedType == fDeclaringType) {
			newType= project.findType(newChangedTypeName);
			newChangedType= newType;
		} else {
			String oldChangedTypeName= fChangedType.getFullyQualifiedName();
			String typeNameSuffix= fDeclaringType.getFullyQualifiedName().substring(oldChangedTypeName.length());
			String newTypeName= newChangedTypeName + typeNameSuffix;
			newType= project.findType(newTypeName);
			newChangedType= project.findType(newChangedTypeName);
		}
		
		Object oldDestination= fChangedType.getDeclaringType();
		if (oldDestination == null) {
			oldDestination= fChangedType.getPackageFragment();
		}
		
		return performChange(newType, newChangedType, oldDestination, TYPE_MOVE);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ltk.core.refactoring.Change#getModifiedElement()
	 */
	public Object getModifiedElement() {
		return getBreakpoint();
	}

	/**
	 * Return the breakpoint modified in this change.
	 */
	public IJavaBreakpoint getBreakpoint() {
		return fBreakpoint;
	}
	
	/**
	 * Return the new name of the changed type for a IType rename change.
	 */
	public String getNewName() {
		if (fChangeType == TYPE_RENAME) {
			return (String)fArgument;
		}
		return null;
	}
	
	/**
	 * Return the parent the changed type for a IType move change.
	 */
	private Object getDestination() {
		if (fChangeType == TYPE_MOVE) {
			return fArgument;
		}
		return null;
	}

	/**
	 * Return the original declaring type of the breakpoint.
	 */
	public IType getDeclaringType() {
		return fDeclaringType;
	}
	
	/**
	 * Return the enable state of the breakpoint.
	 */
	public boolean getEnable() {
		return fIsEnable;
	}
	
	/**
	 * Return the attributes map of the breakpoint.
	 */
	public Map getAttributes() {
		return fAttributes;
	}
	
	/**
	 * Return the hit count of the breakpoint.
	 */
	public int getHitCount() {
		return fHitCount;
	}
	
	/**
	 * Return the message to use if the breakpoint no more exists (used in #isValid()).
	 */
	public abstract String getErrorMessageNoMoreExists();
	
	/**
	 * Perform the real modifications.
	 * @return the undo change.
	 */
	public abstract Change performChange(IType newType, IType undoChangedType, Object undoArgument, int changeType) throws CoreException;

}
