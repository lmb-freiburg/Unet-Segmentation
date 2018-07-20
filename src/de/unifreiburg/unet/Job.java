/**************************************************************************
 *
 * Copyright (C) 2018 Thorsten Falk
 *
 *        Image Analysis Lab, University of Freiburg, Germany
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 **************************************************************************/

package de.unifreiburg.unet;

import ij.Prefs;
import ij.IJ;
import ij.WindowManager;
import ij.plugin.frame.Recorder;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.BorderLayout;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.JDialog;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.UIManager;
import javax.swing.BorderFactory;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.io.File;
import java.io.FileFilter;
import java.util.UUID;

import com.jcraft.jsch.Session;

import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

public abstract class Job extends Thread {

  private final String _jobId = "unet-" + UUID.randomUUID().toString();
  private boolean _isInteractive = true;

  // Progress related variables
  private final ProgressMonitor _progressMonitor;

  // JobTableModel related variables
  private JobTableModel _jobTableModel;
  private final JButton _readyCancelButton = new JButton("Cancel");

  // Parameters Dialog related variables
  protected JDialog _parametersDialog = null;
  protected GroupLayout _dialogLayout = null;
  protected Group _horizontalDialogLayoutGroup = null;
  protected Group _verticalDialogLayoutGroup = null;
  protected final JPanel _configPanel = new JPanel();
  private final JButton _okButton = new JButton("OK");
  private final JButton _cancelButton = new JButton("Cancel");

  // Model parameters
  private final JComboBox<ModelDefinition> _modelComboBox =
      new JComboBox<ModelDefinition>();
  private final JButton _modelFolderChooseButton =
      (UIManager.get("FileView.directoryIcon") instanceof Icon) ? new JButton(
          (Icon)UIManager.get("FileView.directoryIcon")) : new JButton("...");
  private final JPanel _dialogPanel = new JPanel();

  public Job() {
    _jobTableModel = null;
    _progressMonitor = new ProgressMonitor(this);
    wireReadyCancelButton();
  }

  public Job(JobTableModel model) {
    _jobTableModel = model;
    _progressMonitor = new ProgressMonitor(this);
    wireReadyCancelButton();
  }

  public void setJobTableModel(JobTableModel model) {
    if (_jobTableModel == model) return;
    if (_jobTableModel != null) {
      _jobTableModel = null;
      _jobTableModel.remove(this);
    }
    _jobTableModel = model;
    if (_jobTableModel != null) _jobTableModel.add(this);
  }

  private void wireReadyCancelButton() {
    _readyCancelButton.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (ready()) finish();
            else if (isAlive()) interrupt();
          }});
  }

  public String weightsFileName() {
    return "N/A";
  }

  public Session sshSession() {
    return null;
  }

  public final String id() {
    return _jobId;
  }

  protected final JobTableModel jobTable() {
    return _jobTableModel;
  }

  public final ProgressMonitor progressMonitor() {
    return _progressMonitor;
  }

  protected final ModelDefinition model() {
    return (ModelDefinition)_modelComboBox.getSelectedItem();
  }

  protected final void setModel(ModelDefinition model) {
    _modelComboBox.setSelectedItem(model);
    if (_modelComboBox.getSelectedItem() == model) return;
    _modelComboBox.addItem(model);
    _modelComboBox.setSelectedItem(model);
  }

  protected final boolean isInteractive() {
    return _isInteractive;
  }

  protected final void setInteractive(boolean interactive) {
    _isInteractive = interactive;
  }

  public final JButton readyCancelButton() {
    return _readyCancelButton;
  }

  public boolean ready() {
    return _readyCancelButton.getText().equals("Ready");
  }

/*======================================================================*/
/*!
 *   After successful processing, call this function with parameter true.
 *   Override this method if you want to add user interaction.
 */
/*======================================================================*/
  protected void setReady(boolean ready) {
    if (ready() == ready) return;
    if (ready) {
      _readyCancelButton.setText("Ready");
      finish();
    }
    else _readyCancelButton.setText("Cancel");
  }

  public void finish() {
    if (progressMonitor().finished()) return;
    finishJob();
  }

  public void abort() {
    readyCancelButton().setText("Terminating...");
    readyCancelButton().setEnabled(false);
    progressMonitor().setCanceled(true);
    if (Recorder.record) Recorder.setCommand(null);
    cleanUp();
    IJ.log("U-Net job aborted");
    IJ.showProgress(1.0);
  }

  protected final void finishJob() {
    if (progressMonitor().finished()) return;
    progressMonitor().setFinished(true);
    cleanUp();
    IJ.log("U-Net job finished");
    IJ.showProgress(1.0);
  }

  public String imageName() {
    return "N/A";
  }

  private void searchModels(File folder) {
    if (folder == null || !folder.isDirectory()) return;
    _modelComboBox.removeAllItems();
    File[] files = folder.listFiles(
        new FileFilter() {
          @Override public boolean accept(File file) {
            return file.getName().matches(".*[.]h5$");
          }});
    for (int i = 0; i < files.length; i++) {
      try {
        ModelDefinition model = new ModelDefinition(this);
        model.file = files[i];
        model.load();
        _modelComboBox.addItem(model);
      }
      catch (HDF5Exception e) {}
    }
    if (_modelComboBox.getItemCount() == 0)
        _modelComboBox.addItem(new ModelDefinition());
    else {
      String modelId = Prefs.get("unet.modelId", "");
      for (int i = 0; i < _modelComboBox.getItemCount(); i++) {
        if (_modelComboBox.getItemAt(i).id.equals(modelId)) {
          _modelComboBox.setSelectedIndex(i);
          break;
        }
      }
      Prefs.set("unet.modelDefinitionFolder", folder.getAbsolutePath());
    }
    _parametersDialog.invalidate();
    _parametersDialog.pack();
    _parametersDialog.setMinimumSize(_parametersDialog.getPreferredSize());
    _parametersDialog.setMaximumSize(
        new Dimension(_parametersDialog.getMaximumSize().width,
                      _parametersDialog.getPreferredSize().height));
    _parametersDialog.validate();
  }

  protected void processModelSelectionChange() {
    _dialogPanel.setMaximumSize(
        new Dimension(
            Integer.MAX_VALUE,
            _dialogPanel.getPreferredSize().height));
    _parametersDialog.invalidate();
    _parametersDialog.setMinimumSize(
        _parametersDialog.getPreferredSize());
    _parametersDialog.setMaximumSize(
        new Dimension(
            Integer.MAX_VALUE,
            _parametersDialog.getPreferredSize().height));
    _parametersDialog.validate();
  }

  // Create the parameters dialog elements without logic
  protected void createDialogElements() {

    /*******************************************************************
     * Generate the GUI layout without logic
     *******************************************************************/

    // Model selection
    final JLabel modelLabel = new JLabel("Model:");
    _modelComboBox.setToolTipText("Select a caffe model.");
    int marginTop = (int) Math.ceil(
        (_modelFolderChooseButton.getPreferredSize().getHeight() -
         _modelComboBox.getPreferredSize().getHeight()) / 2.0);
    int marginBottom = (int) Math.floor(
        (_modelFolderChooseButton.getPreferredSize().getHeight() -
         _modelComboBox.getPreferredSize().getHeight()) / 2.0);
    Insets insets = _modelFolderChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    _modelFolderChooseButton.setMargin(insets);
    _modelFolderChooseButton.setToolTipText("Select model definition folder");

    // Create Parameters Panel
    _dialogPanel.setBorder(BorderFactory.createEtchedBorder());
    _dialogLayout = new GroupLayout(_dialogPanel);
    _dialogPanel.setLayout(_dialogLayout);
    _dialogLayout.setAutoCreateGaps(true);
    _dialogLayout.setAutoCreateContainerGaps(true);
    _horizontalDialogLayoutGroup =
        _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING);
    _verticalDialogLayoutGroup = _dialogLayout.createSequentialGroup();

    _dialogLayout.setHorizontalGroup(
        _horizontalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createSequentialGroup()
            .addGroup(
                _dialogLayout.createParallelGroup(
                    GroupLayout.Alignment.TRAILING)
                .addComponent(modelLabel))
            .addGroup(
                _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_modelComboBox)
                    .addComponent(_modelFolderChooseButton)))));

    _dialogLayout.setVerticalGroup(
        _verticalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(modelLabel)
            .addComponent(_modelComboBox)
            .addComponent(_modelFolderChooseButton)));

    // OK/Cancel buttons
    final JPanel okCancelPanel = new JPanel();
    okCancelPanel.add(_okButton);
    okCancelPanel.add(_cancelButton);

    // Assemble button panel
    final JPanel buttonPanel = new JPanel(new BorderLayout());
    buttonPanel.add(_configPanel, BorderLayout.WEST);
    buttonPanel.add(okCancelPanel, BorderLayout.EAST);

    // Assemble Dialog
    _parametersDialog = new JDialog(
        WindowManager.getCurrentWindow(), "U-Net Job", true);
    _parametersDialog.add(_dialogPanel, BorderLayout.CENTER);
    _parametersDialog.add(buttonPanel, BorderLayout.SOUTH);
    _parametersDialog.getRootPane().setDefaultButton(_okButton);
  }

  // Wire the elements and validate the dialog
  protected void finalizeDialog() {

    _dialogPanel.setMaximumSize(
        new Dimension(
            Integer.MAX_VALUE, _dialogPanel.getPreferredSize().height));

    /*******************************************************************
     * Wire controls inner to outer before setting values so that
     * value changes trigger all required updates
     *******************************************************************/
    // Model selection affects weightFileTextField, tileModeSelector and
    // tilingParameters (critical)
    _modelComboBox.addItemListener(
        new ItemListener() {
          @Override
          public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
              processModelSelectionChange();
            }
          }});

    // Model folder selection affects ModelComboBox (critical)
    _modelFolderChooseButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File startFolder =
                (model() == null || model().file == null ||
                 model().file.getParentFile() == null) ?
                new File(".") : model().file.getParentFile();
            JFileChooser f = new JFileChooser(startFolder);
            f.setDialogTitle("Select U-Net model folder");
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            f.setFileFilter(
                new FileNameExtensionFilter("HDF5 files", "h5", "H5"));
            int res = f.showDialog(_parametersDialog, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;

            // This also updates the Prefs if the folder contains valid models
            searchModels(f.getSelectedFile().isDirectory() ?
                          f.getSelectedFile() :
                          f.getSelectedFile().getParentFile());
          }});

    _okButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            // When accepted the dialog is only hidden. Don't
            // dispose it here, because isDisplayable() is used
            // to find out that OK was pressed!
            _parametersDialog.setVisible(false);
          }});

    _cancelButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            // When cancelled the dialog is disposed (which is
            // also done when the dialog is closed). It must be
            // disposed here, because isDisplayable() is used
            // to find out that the Dialog was cancelled!
            _parametersDialog.dispose();
          }});

    // Search models in currently selected model folder. This
    // populates the model combobox and sets _model if a model was
    // found. This should also implicitly update the tiling fields.
    searchModels(
        new File(Prefs.get("unet.modelDefinitionFolder", ".")));

    // Free all resources and make isDisplayable() return false to
    // distinguish dialog close from accept
    _parametersDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    _parametersDialog.pack();
    _parametersDialog.setMinimumSize(_parametersDialog.getPreferredSize());
    _parametersDialog.setMaximumSize(
        new Dimension(
            Integer.MAX_VALUE,
            _parametersDialog.getPreferredSize().height));
    _parametersDialog.setLocationRelativeTo(WindowManager.getActiveWindow());
  }

  protected final void prepareParametersDialog() {
    createDialogElements();
    finalizeDialog();
  }

  protected boolean checkParameters() throws InterruptedException {

    if (!model().isValid()) {
      showMessage("Please select a model. Probably you first need " +
                  "to select a folder containing models.");
      return false;
    }

    Prefs.set("unet.modelId", model().id);
    model().savePreferences();

    return true;
  }

  public void cleanUp() {
    if (_jobTableModel != null) _jobTableModel.deleteJob(this);
  }

  public final void showMessage(String msg) {
    IJ.log(msg);
    IJ.showMessage(msg);
  }

  public final void showError(String msg, Exception e) {
    IJ.log(msg);
    if (e != null) IJ.log("Error message: " + e);
    IJ.error(msg);
  }

};
