package net.java.amateras.uml.synchronizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

import net.java.amateras.uml.classdiagram.ClassDiagramEditor;
import net.java.amateras.uml.classdiagram.model.ClassModel;
import net.java.amateras.uml.classdiagram.model.CommonEntityModel;
import net.java.amateras.uml.classdiagram.model.EnumModel;
import net.java.amateras.uml.classdiagram.model.InterfaceModel;
import net.java.amateras.uml.editpart.AbstractUMLEntityEditPart.DeleteCommand;
import net.java.amateras.uml.java.ImportClassModelCommand;
import net.java.amateras.uml.java.UMLJavaUtils;
import net.java.amateras.uml.model.AbstractUMLEntityModel;
import net.java.amateras.uml.model.RootModel;

/**
 * Manual synchronization between class diagram elements and Java source
 */
public class SyncAction implements IEditorActionDelegate {
	
	private ClassDiagramEditor editor;
	private List<AbstractUMLEntityModel> target = new ArrayList<AbstractUMLEntityModel>();

	@Override
	public void setActiveEditor(IAction action, IEditorPart targetEditor) {
		this.editor = (ClassDiagramEditor)targetEditor;
		action.setEnabled(false);
	}
	
	public void setTargetList(List<AbstractUMLEntityModel> target, IEditorPart targetEditor) {
		this.editor = (ClassDiagramEditor)targetEditor;
		this.target = target;
	}

	@Override
	public void run(IAction action) {
		if(target.isEmpty()){
			return;
		}
		
		for(AbstractUMLEntityModel model: new ArrayList<AbstractUMLEntityModel>(target)){
			
			CommonEntityModel modelComEntity = (CommonEntityModel)model;
			
			IJavaProject javaProject = null;
			
			// Search if the origin source file could be found from path property
			String path = modelComEntity.getPath();
			if (path.isEmpty() == false) {
				String[] pathPart = path.split("/");
				String searchProject = pathPart[1];
				IWorkspace workspace = ResourcesPlugin.getWorkspace();
				IProject project = workspace.getRoot().getProject(searchProject);
				if (project.isOpen()) {
					javaProject = JavaCore.create(project);
				}
			}
			else {
				// Search in current project if class exist
				IEditorInput input = this.editor.getEditorInput();
				if (input instanceof IFileEditorInput) {
					IFile file = ((IFileEditorInput) input).getFile();
					javaProject = JavaCore.create(file.getProject());
				}
			}
			
			if (javaProject != null) {
				String className = modelComEntity.getName();
				className = UMLJavaUtils.stripGenerics(className);
				
				try {
					IType type = javaProject.findType(className);
					if(type != null && type.exists()){
						RootModel root = (RootModel) this.editor.getAdapter(RootModel.class);
						CommandStack stack = (CommandStack) editor.getAdapter(CommandStack.class);
						
						CommandChain commandChain = new CommandChain();
						
						DeleteCommand deleteCommand = new DeleteCommand();
						deleteCommand.setRootModel(root);
						deleteCommand.setTargetModel(model);
						commandChain.add(deleteCommand);
						
						ImportClassModelCommand importCommand = new ImportClassModelCommand(root, type, true);
						Rectangle rect = ((AbstractUMLEntityModel) model).getConstraint();
						importCommand.setLocation(new Point(rect.x, rect.y));
						commandChain.add(importCommand);
						
						stack.execute(commandChain);
					}
				}
				catch (JavaModelException ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		target.clear();
		
		// updates status (enable or disable)
		if(selection != null && selection instanceof IStructuredSelection){
			for(Object obj: ((IStructuredSelection) selection).toArray()){
				if(obj instanceof EditPart){
					Object model = ((EditPart) obj).getModel();
					if(model instanceof ClassModel || model instanceof InterfaceModel || model instanceof EnumModel){
						target.add((AbstractUMLEntityModel) model);
					}
				}
			}
		}
		if(target.isEmpty()){
			action.setEnabled(false);
		} else {
			action.setEnabled(true);
		}
	}
	
	/**
	 * This class is a wrapper to execute multiple commands as one command.
	 * 
	 * @author Naoki Takezoe
	 */
	private static class CommandChain extends Command {
		
		private List<Command> commandList = new ArrayList<Command>();
		
		public void add(Command command){
			commandList.add(command);
		}
		
		@Override
		public void execute(){
			for(Command command: commandList){
				command.execute();
			}
		}
		
		@Override
		public void undo(){
			List<Command> revervseList = new ArrayList<Command>(commandList);
			Collections.reverse(revervseList);
			
			for(Command command: revervseList){
				command.undo();
			}
		}
		
	}

}
