/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.debug.tests.refactoring;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.debug.core.IJavaClassPrepareBreakpoint;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.IJavaMethodBreakpoint;
import org.eclipse.jdt.debug.core.IJavaWatchpoint;
import org.eclipse.jdt.internal.corext.refactoring.code.ConvertAnonymousToNestedRefactoring;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class ConvertPublicAnonymousTypeToNestedUnitTests extends AbstractRefactoringDebugTest {

	public ConvertPublicAnonymousTypeToNestedUnitTests(String name) {
		super(name);
	}

	
	public void testLineBreakpoint() throws Exception {
		cleanTestFiles();
				
		try {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$childsMethod()V$1",
					targetParentType = "MoveeChild$childsMethod()V$1",
					newAnonTypeName = "a.b.c.MoveeChild$NewAnonymousClass";
			int lineNumber = 26,
				newLineNumber = 30;
			
			//create breakpoint to test
			IJavaLineBreakpoint breakpoint = createLineBreakpoint(lineNumber, src, pack, cunit, fullTargetName);
			//refactor
			Refactoring ref = setupRefactor(src, pack, cunit,targetParentType);
			performRefactor(ref);
			//test breakpoints
			IBreakpoint[] breakpoints = getBreakpointManager().getBreakpoints();
			assertEquals("wrong number of breakpoints", 1, breakpoints.length);
			breakpoint = (IJavaLineBreakpoint) breakpoints[0];
			assertTrue("Breakpoint Marker has ceased existing",breakpoint.getMarker().exists());
			assertEquals("breakpoint attached to wrong type", newAnonTypeName, breakpoint.getTypeName());
			assertEquals("breakpoint on wrong line", newLineNumber, breakpoint.getLineNumber());
		} catch (Exception e) {
			throw e;
		} finally {
			removeAllBreakpoints();
		}
	}//end testBreakPoint	

	public void testMethodBreakpoint() throws Exception {
		cleanTestFiles();
				
		try {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$childsMethod()V$1$anonTypeMethod()QString",
					targetParentType = "MoveeChild$childsMethod()V$1",
					methodName = "anonTypeMethod",					
					newAnonTypeName = "a.b.c.MoveeChild$NewAnonymousClass";
			//create breakpoint to test
			IJavaMethodBreakpoint breakpoint = createMethodBreakpoint(src, pack, cunit,fullTargetName, true, false);
			//refactor
			Refactoring ref = setupRefactor(src, pack, cunit,targetParentType);
			performRefactor(ref);
			//test breakpoints
			IBreakpoint[] breakpoints = getBreakpointManager().getBreakpoints();
			assertEquals("wrong number of breakpoints", 1, breakpoints.length);
			breakpoint = (IJavaMethodBreakpoint) breakpoints[0];
			assertTrue("Breakpoint Marker has ceased existing",breakpoint.getMarker().exists());
			assertEquals("wrong type name", newAnonTypeName, breakpoint.getTypeName());
			assertEquals("breakpoint attached to wrong method",methodName,breakpoint.getMethodName());
		} catch (Exception e) {
			throw e;
		} finally {
			removeAllBreakpoints();
		}
	}//end testBreakPoint
	
	public void testWatchpoint() throws Exception {
		cleanTestFiles();
				
		try {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$childsMethod()V$1$anAnonInt",
					targetParentType = "MoveeChild$childsMethod()V$1",
					fieldName = "anAnonInt",					
					newAnonTypeName = "a.b.c.MoveeChild$NewAnonymousClass";
						
			//create breakpoint to test
			IJavaWatchpoint breakpoint = createNestedTypeWatchPoint(src, pack, cunit, fullTargetName, true, true);
			//refactor
			Refactoring ref = setupRefactor(src, pack, cunit,targetParentType);
			performRefactor(ref);
			//test breakpoints
			IBreakpoint[] breakpoints = getBreakpointManager().getBreakpoints();
			assertEquals("wrong number of breakpoints", 1, breakpoints.length);
			breakpoint = (IJavaWatchpoint) breakpoints[0];
			assertTrue("Breakpoint Marker has ceased existing",breakpoint.getMarker().exists());
			assertEquals("breakpoint attached to wrong type", newAnonTypeName, breakpoint.getTypeName());
			assertEquals("breakpoint attached to wrong field", fieldName, breakpoint.getFieldName());
		} catch (Exception e) {
			throw e;
		} finally {
			removeAllBreakpoints();
		}
	}//end testBreakPoint	
		
	public void testClassLoadpoint() throws Exception {
		cleanTestFiles();
				
		try {
			String 	src = "src", 
					pack = "a.b.c",
					cunit = "MoveeChild.java",
					fullTargetName = "MoveeChild$childsMethod()V$1",
					targetParentType = "MoveeChild$childsMethod()V$1",
					newAnonTypeName = "a.b.c.MoveeChild$NewAnonymousClass";
			
			//create breakpoint to test
			IJavaClassPrepareBreakpoint breakpoint = createClassPrepareBreakpoint(src, pack, cunit, fullTargetName);
			//refactor
			Refactoring ref = setupRefactor(src, pack, cunit,targetParentType);
			performRefactor(ref);
			//test breakpoints
			IBreakpoint[] breakpoints = getBreakpointManager().getBreakpoints();
			assertEquals("wrong number of breakpoints", 1, breakpoints.length);
			breakpoint = (IJavaClassPrepareBreakpoint) breakpoints[0];
			assertTrue("Breakpoint Marker has ceased existing",breakpoint.getMarker().exists());
			assertEquals("breakpoint attached to wrong type", newAnonTypeName, breakpoint.getTypeName());
		} catch (Exception e) {
			throw e;
		} finally {
			removeAllBreakpoints();
		}
	}//end testBreakPoint	
			
//////////////////////////////////////////////////////////////////////////////////////	
	private Refactoring setupRefactor(String root, String packageName, String cuName, String targetName) throws Exception {
		
		IJavaProject javaProject = getJavaProject();
		ICompilationUnit cunit = getCompilationUnit(javaProject, root, packageName, cuName);
		IType type = (IType)getMember(cunit,targetName);
		
		//IDocument compUnitSource = new Document(cunit.getSource());
				
		ISourceRange typeInfo = type.getSourceRange();
		int target = typeInfo.getOffset(); 
		
		ConvertAnonymousToNestedRefactoring ref= new ConvertAnonymousToNestedRefactoring(cunit, null, target, 0);
		//configure the ref a little more here!
		ref.setClassName("NewAnonymousClass");			
		
		RefactoringStatus preconditionResult= ref.checkInitialConditions(new NullProgressMonitor());
		if(!preconditionResult.isOK())
		{
			System.out.println(preconditionResult.getMessageMatchingSeverity(preconditionResult.getSeverity()));
			return null;
		}
		//configure the ref a little more here!
		ref.setClassName("NewAnonymousClass");		
		return ref;
	}
	
	protected final void performRefactor(final Refactoring refactoring) throws Exception {
		if(refactoring==null)
			return;
		CreateChangeOperation create= new CreateChangeOperation(refactoring);
		refactoring.checkFinalConditions(new NullProgressMonitor());
		PerformChangeOperation perform= new PerformChangeOperation(create);
		ResourcesPlugin.getWorkspace().run(perform, new NullProgressMonitor());//maybe SubPM?
		waitForBuild();
	}	
	
}
