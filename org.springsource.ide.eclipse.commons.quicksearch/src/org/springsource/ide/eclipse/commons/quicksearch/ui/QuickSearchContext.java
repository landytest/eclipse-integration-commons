/*******************************************************************************
 * Copyright (c) 2013 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.quicksearch.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.springsource.ide.eclipse.commons.quicksearch.core.priority.DefaultPriorityFunction;
import org.springsource.ide.eclipse.commons.quicksearch.core.priority.PrioriTree;
import org.springsource.ide.eclipse.commons.quicksearch.core.priority.PriorityFunction;

/**
 * An instance of this class groups together some logic to inform the 
 * quicksearch dialog about 'search context'. I.e. things such as the
 * current selection and open editors when the dialog is opened.
 * <p>
 * This contextual information is used to inform the PriorityFunction
 * for the search process.
 * 
 * @author Kris De Volder
 */
public class QuickSearchContext {
	
	/**
	 * We remember the last result of getOpenFiles in here. This is so that we can return this
	 * if we are having trouble to compute the open files. Sometimes we may not be able to
	 * access the active workbench page etc. In this case it is probably better to return
	 * a stale list of files than nothing at all.
	 */
	private static Collection<IFile> lastOpenFiles = Arrays.asList(); //Empty list to start with.
	
	private IWorkbenchWindow window;
	
	public QuickSearchContext(IWorkbenchWindow window) {
		this.window = window;
	}
	
	/**
	 * Create a walker priority function based on the current 'context'.
	 */
	public PriorityFunction createPriorityFun() {
		PrioriTree priorities = PrioriTree.create();
		priorities.configure(QuickSearchActivator.getDefault().getPreferences());
		try {
// TODO: This is not working correctly right now, if the selected resources are containers / folders. 
// The PrioriTree only assigns a priority to the folder itself, but not to its children.
// So open editors will automatically take priority over the children of selected projects.
// To fix, PrioriTree will need a mechanism to assign priorities to children.
// If doing so, care must be taken not to accidentally assign priorities to ignored 
// resources.
			
			Collection<IResource> selectedResources = getSelectedResources();
			for (IResource r : selectedResources) {
				priorities.setPriority(r.getFullPath(), 3*PriorityFunction.PRIORITY_INTERESTING);
			}
			
			IFile currentFile = getActiveFile();
			if (currentFile!=null) {
				//Current file is more interesting than other open files
				priorities.setPriority(currentFile.getFullPath(), 2*PriorityFunction.PRIORITY_INTERESTING);
			}
			Collection<IFile> openFiles = getOpenFiles();
			for (IFile file : openFiles) {
				priorities.setPriority(file.getFullPath(), PriorityFunction.PRIORITY_INTERESTING);
			}
			return priorities;
		} catch (Throwable e) {
			QuickSearchActivator.log(e);
		}
		return new DefaultPriorityFunction();
	}

	private Collection<IFile> getOpenFiles() {
		try {
			IWorkbenchPage page = window.getActivePage();
			if (page!=null) {
				Collection<IFile> files = new ArrayList<IFile>();
				IEditorReference[] editors = page.getEditorReferences();
				if (editors!=null) {
					for (IEditorReference editor : editors) {
						try {
							IEditorInput input = editor.getEditorInput();
							if (input!=null) {
								IFile file = (IFile) input.getAdapter(IFile.class);
								if (file != null) {
								    files.add(file);
								}
							}
						} catch (PartInitException e) {
							QuickSearchActivator.log(e);
						}
					}
					lastOpenFiles = files;
					return files;
				}
			}
			return lastOpenFiles;
		} finally {
		}
	}
	
	/**
	 * Gets the IFile that is currently open in the active editor.
	 * @return IFile or null if there is no current editor or the editor isn't associated to a file.
	 */
	private IFile getActiveFile() {
		IWorkbenchPage page = window.getActivePage();
		if (page!=null) {
			IEditorPart editor = page.getActiveEditor();
			if (editor!=null) {
				IEditorInput input = editor.getEditorInput();
				if (input!=null) {
					return (IFile) input.getAdapter(IFile.class);
				}
			}
		}
		return null;
	}
	
	/**
	 * Get a Collection of selected resources from the active selection if that selection is
	 * a Structured selection (e.g. in navigator or project/package explorer)
	 */
	private Collection<IResource> getSelectedResources() {
		ISelection _s = window.getSelectionService().getSelection();
		if (_s!=null && _s instanceof IStructuredSelection) {
			IStructuredSelection s = (IStructuredSelection) _s;
			if (s!=null && !s.isEmpty()) {
				Object[] elements = s.toArray();
				List<IResource> resources = new ArrayList<IResource>(elements.length);
				for (Object e : elements) {
					if (e instanceof IResource) {
						resources.add((IResource) e);
					} else if (e instanceof IAdaptable) {
						IAdaptable ae = (IAdaptable) e;
						IResource r = (IResource) ae.getAdapter(IResource.class);
						if (r!=null) {
							resources.add(r);
						}
					}
				}
				return resources;
			}
		}
		return Collections.emptyList();
	}

}
