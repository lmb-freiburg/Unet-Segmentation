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
import javax.swing.JPanel;
import javax.swing.JDialog;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.JFileChooser;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.BorderFactory;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.io.File;
import java.io.IOException;
import java.io.FileFilter;
import java.util.Vector;
import java.util.UUID;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

public abstract class Job extends Thread {

  private final String _jobId = "unet-" + UUID.randomUUID().toString();
  private boolean _isInteractive = true;

  // Progress related variables
  private final ProgressMonitor _progressMonitor;

  // JobTableModel related variables
  private final JobTableModel _jobTableModel;
  private final JButton _readyCancelButton = new JButton("Cancel");

  // Parameters Dialog related variables
  protected JDialog _parametersDialog = null;
  protected GroupLayout _dialogLayout = null;
  protected Group _horizontalDialogLayoutGroup = null;
  protected Group _verticalDialogLayoutGroup = null;
  protected final JPanel _configPanel = new JPanel();

  private final JPanel _dialogPanel = new JPanel();
  private final JPanel _tilingModeSelectorPanel =
      new JPanel(new BorderLayout());
  private final JPanel _tilingParametersPanel = new JPanel(new BorderLayout());

  private final JComboBox<ModelDefinition> _modelComboBox =
      new JComboBox<ModelDefinition>();
  private final JTextField _weightsFileTextField = new JTextField("", 20);
  private final JTextField _processFolderTextField = new JTextField(
      Prefs.get("unet.processfolder", ""), 20);
  private final String[] gpuList = {
      "none", "all available", "GPU 0", "GPU 1", "GPU 2", "GPU 3",
      "GPU 4", "GPU 5", "GPU 6", "GPU 7" };
  private final JComboBox<String> _useGPUComboBox = new JComboBox<>(gpuList);
  private final HostConfigurationPanel _hostConfiguration =
      new HostConfigurationPanel();
  private final JButton _modelFolderChooseButton =
      (UIManager.get("FileView.directoryIcon") instanceof Icon) ? new JButton(
          (Icon)UIManager.get("FileView.directoryIcon")) : new JButton("...");
  private final JButton _weightsFileChooseButton =
      _hostConfiguration.weightsFileChooseButton();
  private final JButton _processFolderChooseButton =
      _hostConfiguration.processFolderChooseButton();
  private final JButton _okButton = new JButton("OK");
  private final JButton _cancelButton = new JButton("Cancel");

  private Session _sshSession = null;

  protected final Vector<String> _createdRemoteFolders = new Vector<String>();
  protected final Vector<String> _createdRemoteFiles = new Vector<String>();

  public Job() {
    _jobTableModel = null;
    _progressMonitor = new ProgressMonitor(this);
  }

  public Job(JobTableModel model) {
    _jobTableModel = model;
    _progressMonitor = new ProgressMonitor(this);
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

  public final String weightsFileName() {
    return _weightsFileTextField.getText();
  }

  protected final void setWeightsFileName(String name) {
    _weightsFileTextField.setText(name);
  }

/*======================================================================*/
/*!
 *   The process folder to use. This method returns the content of the
 *   corresponding dialog text field with a trailing '/' added if the
 *   field is not empty. The idea is to allow the user to give absolute and
 *   relative path strings. The path must be writable on the computer the
 *   actual work is done, i.e. for local computing, the local machine, for
 *   remote computing the remote machine. When giving a relative path
 *   (or leaving the field empty) temporary output will be written relative
 *   to the current folder (local mode) or to the home directory on the server
 *   (remote mode). Be careful when using relative paths, especially on
 *   Windows the current folder might be not writable by the user!
 *
 *   \return The process folder name as String with trailing slash appended
 *     if not the empty string
 */
/*======================================================================*/
  public final String processFolder() {
    String folder = _processFolderTextField.getText();
    if (folder.equals("") || folder.endsWith("/")) return folder;
    return folder + "/";
  }

  protected final void setProcessFolder(String name) {
    _processFolderTextField.setText(name);
  }

  protected final HostConfigurationPanel hostConfiguration() {
    return _hostConfiguration;
  }

  public final Session sshSession() {
    return _sshSession;
  }

  protected final void setSshSession(Session session) {
    if (_sshSession == session) return;
    if (_sshSession != null) _sshSession.disconnect();
    _sshSession = session;
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

  protected String selectedGPUString() {
    return (String)_useGPUComboBox.getSelectedItem();
  }

  protected void setGPUString(String gpu) {
    _useGPUComboBox.setSelectedItem(gpu);
  }

  public String caffeGPUParameter() {
    String gpuParm = "";
    String selectedGPU = selectedGPUString();
    if (selectedGPU.contains("GPU "))
        gpuParm = "-gpu " + selectedGPU.substring(selectedGPU.length() - 1);
    else if (selectedGPU.contains("all")) gpuParm = "-gpu all";
    return gpuParm;
  }

  protected void processModelSelectionChange() {
    _tilingModeSelectorPanel.removeAll();
    _tilingParametersPanel.removeAll();
    if (model() != null) {
      _weightsFileTextField.setText(model().weightFile);
      _tilingModeSelectorPanel.add(model().tileModeSelector());
      _tilingModeSelectorPanel.setMinimumSize(
          model().tileModeSelector().getPreferredSize());
      _tilingModeSelectorPanel.setMaximumSize(
          model().tileModeSelector().getPreferredSize());
      _tilingParametersPanel.add(model().tileModePanel());
      _tilingParametersPanel.setMinimumSize(
          model().tileModePanel().getMinimumSize());
      _tilingParametersPanel.setMaximumSize(
          new Dimension(
              Integer.MAX_VALUE,
              model().tileModeSelector().getPreferredSize().height));
    }
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

    // Weights
    final JLabel weightsFileLabel = new JLabel("Weight file:");
    _weightsFileTextField.setToolTipText(
        "Location of the file containing the trained network weights " +
        "on the backend server.\nIf not yet on the server, on-the-fly " +
        "file upload will be offered.");
    marginTop = (int) Math.ceil(
        (_weightsFileChooseButton.getPreferredSize().getHeight() -
         _weightsFileTextField.getPreferredSize().getHeight()) / 2.0);
    marginBottom = (int) Math.floor(
        (_weightsFileChooseButton.getPreferredSize().getHeight() -
         _weightsFileTextField.getPreferredSize().getHeight()) / 2.0);
    insets = _weightsFileChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    _weightsFileChooseButton.setMargin(insets);
    _weightsFileChooseButton.setToolTipText(
        "Choose the file containing the trained network weights.");

    // Processing environment
    final JLabel processFolderLabel = new JLabel("Process Folder:");
    _processFolderTextField.setToolTipText(
        "Folder for temporary files on the backend server. If left empty, " +
        "temporary files are written directly to the user's home directory.");
    marginTop = (int) Math.ceil(
        (_processFolderChooseButton.getPreferredSize().getHeight() -
         _processFolderTextField.getPreferredSize().getHeight()) / 2.0);
    marginBottom = (int) Math.floor(
        (_processFolderChooseButton.getPreferredSize().getHeight() -
         _processFolderTextField.getPreferredSize().getHeight()) / 2.0);
    insets = _processFolderChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    _processFolderChooseButton.setMargin(insets);
    _processFolderChooseButton.setToolTipText(
        "Select the folder to store temporary files.");

    // GPU parameters
    final JLabel useGPULabel = new JLabel("Use GPU:");
    _useGPUComboBox.setToolTipText(
        "Select the GPU id to use. Select CPU if you don't have any " +
        "CUDA capable GPU available on the compute host. Select " +
        "<autodetect> to leave the choice to caffe.");

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
                .addComponent(modelLabel)
                .addComponent(weightsFileLabel)
                .addComponent(processFolderLabel)
                .addComponent(useGPULabel)
                .addComponent(_tilingModeSelectorPanel))
            .addGroup(
                _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_modelComboBox)
                    .addComponent(_modelFolderChooseButton))
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_weightsFileTextField)
                    .addComponent(_weightsFileChooseButton))
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_processFolderTextField)
                    .addComponent(_processFolderChooseButton))
                .addComponent(_useGPUComboBox)
                .addComponent(_tilingParametersPanel)))
        .addComponent(_hostConfiguration));

    _dialogLayout.setVerticalGroup(
        _verticalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(modelLabel)
            .addComponent(_modelComboBox)
            .addComponent(_modelFolderChooseButton))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(weightsFileLabel)
            .addComponent(_weightsFileTextField)
            .addComponent(_weightsFileChooseButton))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(processFolderLabel)
            .addComponent(_processFolderTextField)
            .addComponent(_processFolderChooseButton))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(useGPULabel)
            .addComponent(_useGPUComboBox))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
            .addComponent(
                _tilingModeSelectorPanel, GroupLayout.PREFERRED_SIZE,
                GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(
                _tilingParametersPanel, GroupLayout.PREFERRED_SIZE,
                GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
        .addComponent(_hostConfiguration));
    _dialogPanel.setMaximumSize(
        new Dimension(
            Integer.MAX_VALUE, _dialogPanel.getPreferredSize().height));

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

    // WeightsFileTextField affects model.weightFile (not critical)
    _weightsFileTextField.getDocument().addDocumentListener(
        new DocumentListener() {
          @Override
          public void insertUpdate(DocumentEvent e) {
            if (model() != null)
                model().weightFile = _weightsFileTextField.getText();
          }
          @Override
          public void removeUpdate(DocumentEvent e) {
            if (model() != null)
                model().weightFile = _weightsFileTextField.getText();
          }
          @Override
          public void changedUpdate(DocumentEvent e) {
            if (model() != null)
                model().weightFile = _weightsFileTextField.getText();
          }
        });

    // WeightsFileChooser affects WeightsFileTextField (not critical)
    _weightsFileChooseButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File startFile = (!_weightsFileTextField.getText().equals("")) ?
                new File(_weightsFileTextField.getText()) :
                model().file;
            JFileChooser f = new JFileChooser(startFile);
            f.setDialogTitle("Select trained U-Net weights");
            f.setFileFilter(
                new FileNameExtensionFilter(
                    "HDF5 and prototxt files", "h5", "H5",
                    "prototxt", "PROTOTXT", "caffemodel",
                    "CAFFEMODEL"));
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int res = f.showDialog(_parametersDialog, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;
            _weightsFileTextField.setText(
                f.getSelectedFile().getAbsolutePath());
          }});

    // ProcessFolderChooser affects ProcessFolderTextField (not critical)
    _processFolderChooseButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File startFolder = new File(_processFolderTextField.getText());
            JFileChooser f = new JFileChooser(startFolder);
            f.setDialogTitle("Select (remote) processing folder");
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int res = f.showDialog(_parametersDialog, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;
            _processFolderTextField.setText(
                f.getSelectedFile().getAbsolutePath());
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

    // Set uncritical fields
    _useGPUComboBox.setSelectedItem(Prefs.get("unet.gpuId", "none"));

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

  public boolean checkParameters() throws InterruptedException {

    Prefs.set("unet.processfolder", processFolder());
    Prefs.set("unet.gpuId", selectedGPUString());

    if (!model().isValid()) {
      IJ.showMessage("Please select a model. Probably you first need " +
                     "to select a folder containing models.");
      return false;
    }

    Prefs.set("unet.modelId", model().id);
    model().savePreferences();

    if (_weightsFileTextField.getText() == "") {
      IJ.showMessage("Please enter the (remote) path to the weights file.");
      return false;
    }

    try {
      _sshSession = _hostConfiguration.sshSession();
    }
    catch (JSchException e) {
      if (_hostConfiguration.hostname() == null) {
        IJ.log("No hostname specified");
        IJ.showMessage("Please enter the server name for remote processing.");
      }
      else
      {
        IJ.log("SSH connection to '" + _hostConfiguration.hostname() +
               "' failed: " + e);
        IJ.showMessage(
            "Could not connect to remote host '" +
            _hostConfiguration.hostname() +
            "'\nPlease check your login credentials.\n" + e);
      }
      return false;
    }

    if (sshSession() != null) {
      try {
        File tmpFile = File.createTempFile(id(), null);
        SftpFileIO sftp = new SftpFileIO(sshSession(), progressMonitor());
        _createdRemoteFolders.addAll(
            sftp.put(tmpFile, processFolder() + tmpFile.getName()));
        sftp.removeFile(processFolder() + tmpFile.getName());
        tmpFile.delete();
      }
      catch (IOException e) {
        IJ.log("Creation of local temporary file failed.");
        IJ.error("Cannot create files in the temporary folder of your " +
                 "local file system.\n" +
                 "Check write permissions and avaliable disk space.");
        return false;
      }
      catch (JSchException e) {
        IJ.log("SSH connection failed.");
        IJ.error("The SSH session has been prematurely disconnected.\n" +
                 "This should not happen and indicates general network " +
                 "problems.");
        return false;
      }
      catch (SftpException e) {
        IJ.log("Sftp transfer failed.");
        IJ.error("File upload to " + processFolder() + " failed.\n" +
                 "Please select a folder with write permissions.");
        return false;
      }
    }
    else {
      try {
        File tmpFile = new File(processFolder() + id() + ".tmp");
        tmpFile.createNewFile();
        tmpFile.delete();
      }
      catch (IOException e) {
        IJ.log("Could not write to " + processFolder());
        IJ.error("Cannot write to " + processFolder() + ".\n" +
                 "Check write permissions and avaliable disk space.");
        return false;
      }
    }

    return true;
  }

  public void cleanUp() {
    for (int i = 0; i < _createdRemoteFiles.size(); i++) {
      try {
        new SftpFileIO(_sshSession, progressMonitor()).removeFile(
            _createdRemoteFiles.get(i));
      }
      catch (Exception e) {
        IJ.log("Could not remove temporary file " +
               _createdRemoteFiles.get(i) + ": " + e);
      }
    }
    for (int i = 0; i < _createdRemoteFolders.size(); i++) {
      try {
        new SftpFileIO(_sshSession, progressMonitor()).removeFolder(
            _createdRemoteFolders.get(i));
      }
      catch (Exception e) {
        IJ.log("Could not remove temporary folder " +
               _createdRemoteFolders.get(i) + ": " + e);
      }
    }
    if (_sshSession != null) _sshSession.disconnect();
    if (_jobTableModel != null) _jobTableModel.deleteJob(this);
  }

};
