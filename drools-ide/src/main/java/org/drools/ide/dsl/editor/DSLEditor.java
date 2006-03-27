package org.drools.ide.dsl.editor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.drools.lang.dsl.template.NLMappingItem;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.FileEditorInput;

/**
 * This is the tablular DSL configuration editor.
 * @author Michael Neale
 */
public class DSLEditor extends EditorPart {

    private Table table;
    private TableViewer tableViewer;
    private NLGrammarModel model; //this is the model that does all the work (from drools-compiler)
    private boolean dirty = false; //editing or deleting will make it dirty
    private Text exprText; //for language expression
    private Text mappingText; //for target rule expression
    private Text descriptionText; //just a comment field
    
    public void doSave(IProgressMonitor monitor) {
        
        FileEditorInput input = (FileEditorInput) getEditorInput();
        File outputFile = input.getFile().getLocation().toFile();
        saveFile( monitor,
                          outputFile, input );
        
    }

    private void saveFile(IProgressMonitor monitor,
                          File outputFile, FileEditorInput input) {
        try {            
            FileWriter writer = new FileWriter(outputFile);
            model.save( writer );
            
            makeClean();
            writer.close();
            input.getFile().getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);            
        } catch ( IOException e ) {
            throw new IllegalStateException("Unable to save DSL configuration file. (IOException: " + e.getMessage() + ")");
        } catch ( CoreException e ) {
            throw new IllegalStateException("Unable to resync workbench after DSL save. (CoreException: " + e.getMessage() + ")");
        }
    }

    void makeClean() {
        this.dirty = false;
        firePropertyChange( PROP_DIRTY );
        
    }

    public void doSaveAs() {
        // TODO Implement this.
    }

    public void init(IEditorSite site,
                     IEditorInput editorInput) throws PartInitException {
        FileEditorInput input = (FileEditorInput)editorInput;
        setSite(site);
        setInput(editorInput);
        setVisibleName( input );
        
        try {
            InputStream stream = input.getFile().getContents();
            model = new NLGrammarModel();
            model.load( new InputStreamReader(stream) );            
            stream.close();
            
        } catch ( CoreException e ) {
            throw new IllegalStateException("Unable to load DSL configuration file. (CoreException: " + e.getMessage() + ")");
        } catch ( IOException e ) {
            throw new IllegalStateException("Unabel to close stream fo DSL config file. (IOException: " + e.getMessage() + ")");
        }
        
    }

    private void setVisibleName(FileEditorInput input) {
        setPartName( input.getFile().getName() );
        setContentDescription( "Editing Domain specific language: [" + input.getFile().getFullPath().toString() + "]");
    }

    public boolean isDirty() {
        return dirty;
    }
    
    /**
     * Sets the dirty flag, and notifies the workbench.
     */
    void makeDirty() {
        dirty = true;
        firePropertyChange( PROP_DIRTY );
    }

    public boolean isSaveAsAllowed() {
        // TODO implement SaveAs
        return false;
    }

    public void createPartControl(Composite parent) {
        
        GridData gridData = new GridData (GridData.HORIZONTAL_ALIGN_FILL | GridData.FILL_BOTH);
        parent.setLayoutData (gridData);

        // Set numColumns to 3 in the overall grid
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 4;
        parent.setLayout (layout);
        
        //create the overall desc field (comments).
        createDescriptionField( parent );
        
        // create the table
        createTable( parent );
        
        // Create and setup the TableViewer
        createTableViewer(); 
                
        //set up the table "binding" with the model
        tableViewer.setContentProvider(new DSLContentProvider(tableViewer, model));
        tableViewer.setLabelProvider(new DSLLabelProvider());      
        refreshModel();
        
        //setup the fields below the table
        createExpressionViewField( parent );    
        createEditButton( parent );
        createMappingViewField( parent );           
        createDeleteButton( parent );
        createAddButton( parent );
        
        //listeners on the table...
        createTableListeners();
        
        
    }

    /**
     * Setup table listeners for GUI events.
     */
    private void createTableListeners() {
        
        //setup views into current selected
        table.addSelectionListener( new SelectionListener() {

            public void widgetSelected(SelectionEvent e) {
                populate();
            }
            public void widgetDefaultSelected(SelectionEvent e) {  
                populate();
            }

            private void populate() {
                NLMappingItem selected = getCurrentSelected();                
                exprText.setText( selected.getNaturalTemplate() );   
                mappingText.setText( selected.getTargetTemplate() );
            }
            
        });
        
        //double click support
        table.addMouseListener( new MouseListener() {

            public void mouseDoubleClick(MouseEvent e) {
                showEditPopup();
            }

            public void mouseDown(MouseEvent e) {}

            public void mouseUp(MouseEvent e) {}
            
        });        
        
    }

    private void createDescriptionField(Composite parent) {
        Label descLbl = new Label(parent, SWT.NONE);
        descLbl.setText( "Description:" );
        GridData gridData = new GridData (GridData.HORIZONTAL_ALIGN_BEGINNING);
        gridData.widthHint = 80;
        descLbl.setLayoutData(gridData);
        
        descriptionText = new Text(parent, SWT.BORDER);
        descriptionText.setLayoutData(new GridData (GridData.FILL_HORIZONTAL));
        descriptionText.setText(  "" + model.getDescription() ); //no nulls !
        descriptionText.addModifyListener( new ModifyListener() {

            public void modifyText(ModifyEvent e) {
                String text = descriptionText.getText();
                if (!text.equals( model.getDescription() )) {
                    model.setDescription( text );
                    makeDirty();
                }                
            }
            
        });
    }
    
    private void createMappingViewField(Composite parent) {        
        Label mapping = new Label(parent, SWT.NONE);
        mapping.setText( "Mapping:" );
        GridData gridData = new GridData (GridData.HORIZONTAL_ALIGN_BEGINNING);
        gridData.widthHint = 80;
        mapping.setLayoutData(gridData);
        
        mappingText = new Text(parent, SWT.BORDER);
        mappingText.setEditable( false );

        mappingText.setLayoutData(new GridData (GridData.FILL_HORIZONTAL));
    }    
    
    private void createExpressionViewField(Composite parent) {

        Label expr = new Label(parent, SWT.NONE);
        expr.setText( "Expression:" );
        GridData gridData = new GridData (GridData.HORIZONTAL_ALIGN_BEGINNING);
        gridData.widthHint = 80;
        expr.setLayoutData(gridData);
        
        exprText = new Text(parent, SWT.BORDER);
        exprText.setEditable( false );
        gridData = new GridData (GridData.FILL_HORIZONTAL);
        
        exprText.setLayoutData(gridData);

    }

    /** Refreshes the table do make sure it is up to date with the model. */
    private void refreshModel() {
        tableViewer.setInput( model );
    }
    
    private void createEditButton(Composite parent) {
        // Create and configure the "Add" button
        Button add = new Button(parent, SWT.PUSH | SWT.CENTER);
        add.setText("Edit");

        GridData gridData = new GridData (GridData.HORIZONTAL_ALIGN_BEGINNING);
        gridData.widthHint = 80;
        add.setLayoutData(gridData);
     
        add.addSelectionListener(new SelectionAdapter() {
        
            // Add a task to the ExampleTaskList and refresh the view
            public void widgetSelected(SelectionEvent e) {                
                showEditPopup();                
            }


        });
    }  
    
    private void showEditPopup() {
        MappingEditor editor = new MappingEditor(getSite().getShell());
        editor.create();
        editor.getShell().setText("Edit language mapping");
        editor.setTitle( "Edit an existing language mapping item." );
        editor.setTitleImage( getTitleImage() );
        
        editor.setNLMappingItem( getCurrentSelected() );
        
        editor.open();
        if (!editor.isCancelled()) {
            refreshModel();
            makeDirty();
        }
    }    
    
    private void createDeleteButton(Composite parent) {
        // Create and configure the "Add" button
        Button add = new Button(parent, SWT.PUSH | SWT.CENTER);
        add.setText("Remove");

        GridData gridData = new GridData (GridData.HORIZONTAL_ALIGN_BEGINNING);
        gridData.widthHint = 80;
        add.setLayoutData(gridData);
        add.addSelectionListener(new SelectionAdapter() {        
            // Add a task to the ExampleTaskList and refresh the view
            public void widgetSelected(SelectionEvent e) {
                model.removeMapping( getCurrentSelected() );
                refreshModel();
                makeDirty();
            }


        });
    }    

    
    /**
     * Return the selected item from the table grid thingy.
     */
    private NLMappingItem getCurrentSelected() {
        return (NLMappingItem) ((IStructuredSelection) 
                tableViewer.getSelection()).getFirstElement();
    }
    
    
    private void createAddButton(Composite parent) {
        // Create and configure the "Add" button
        Button add = new Button(parent, SWT.PUSH | SWT.CENTER);
        add.setText("Add");

        GridData gridData = new GridData (GridData.HORIZONTAL_ALIGN_BEGINNING);
        gridData.widthHint = 80;
        add.setLayoutData(gridData);
        
        add.addSelectionListener(new SelectionAdapter() {
        
            // Add an item, should pop up the editor
            public void widgetSelected(SelectionEvent e) {  
                
                NLMappingItem newItem = new NLMappingItem("", "", "*");
                
                MappingEditor editor = new MappingEditor(getSite().getShell());//shell);
                editor.create();
                editor.getShell().setText("New language mapping");
                editor.setTitle( "Create a new language element mapping." );
                editor.setTitleImage( getTitleImage() );
                
                editor.setNLMappingItem( newItem );
                
                editor.open();
                if (!editor.isCancelled()) {
                    model.addNLItem( newItem );
                    refreshModel();
                    makeDirty();
                }                
                
            }
        });
    }

    
    /**
     * Create the viewer.
     */
    private void createTableViewer() {
        tableViewer = new TableViewer(table);
        tableViewer.setUseHashlookup(true);
        //following is if we want default sorting... my thought is no...
        //tableViewer.setSorter(new DSLMappingSorter(DSLMappingSorter.EXPRESSION));
    }

    /**
     * Create the Table
     */
    private void createTable(Composite parent) {
        int style = SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | 
                    SWT.FULL_SELECTION | SWT.HIDE_SELECTION;

        table = new Table(parent, style);
        
        GridData gridData = new GridData(GridData.FILL_BOTH);
        gridData.grabExcessVerticalSpace = true;
        gridData.horizontalSpan = 3;
        table.setLayoutData(gridData);      
                    
        table.setLinesVisible(true);
        table.setHeaderVisible(true);

        TableColumn column;
        
        //Expression col
        column = new TableColumn(table, SWT.LEFT, 0);
        column.setText("Language Expression");
        column.setWidth(350);
        // Add listener to column so sorted when clicked 
        column.addSelectionListener(new SelectionAdapter() {
        
            public void widgetSelected(SelectionEvent e) {
                tableViewer.setSorter(new DSLMappingSorter(DSLMappingSorter.EXPRESSION));
            }
        });
        

        // 3rd column with task Owner
        column = new TableColumn(table, SWT.LEFT, 1);
        column.setText("Rule language mapping");
        column.setWidth(200);
        // Add listener to column so sorted when clicked
        column.addSelectionListener(new SelectionAdapter() {
        
            public void widgetSelected(SelectionEvent e) {
                tableViewer.setSorter(new DSLMappingSorter(DSLMappingSorter.MAPPING));
            }
        });

        // 4th column with task PercentComplete 
        column = new TableColumn(table, SWT.LEFT, 2);
        column.setText("Scope");
        column.setWidth(80);
        
        //  Add listener to column so tasks are sorted when clicked
        column.addSelectionListener(new SelectionAdapter() {
        
            public void widgetSelected(SelectionEvent e) {
                tableViewer.setSorter(new DSLMappingSorter(DSLMappingSorter.SCOPE));
            }
        });
        

    }
    
    
    public void setFocus() {
    }
    
    public void dispose() {
        super.dispose();
    }
    

}