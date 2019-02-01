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
import javax.swing.JComponent;
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
import javax.swing.JOptionPane;

import java.io.File;
import java.io.IOException;
import java.io.FileFilter;
import java.util.Vector;
import java.util.UUID;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

public abstract class CaffeJob extends Job {

  // Parameters for training/finetuning
  private final HostConfigurationPanel _hostConfiguration =
      new HostConfigurationPanel();
  private final JTextField _weightsFileTextField = new JTextField("", 20);
  private final JButton _weightsFileChooseButton =
      _hostConfiguration.weightsFileChooseButton();
  private final JTextField _processFolderTextField = new JTextField(
      Prefs.get("unet.processfolder", ""), 20);
  private final JButton _processFolderChooseButton =
      _hostConfiguration.processFolderChooseButton();
  private final String[] gpuList = {
      "none", "all available", "GPU 0", "GPU 1", "GPU 2", "GPU 3",
      "GPU 4", "GPU 5", "GPU 6", "GPU 7" };

  private final JComboBox<String> _useGPUComboBox = new JComboBox<>(gpuList);

  protected final Vector<String> _createdRemoteFolders = new Vector<String>();
  protected final Vector<String> _createdRemoteFiles = new Vector<String>();

  private JComponent _shownTileModeLabel = new JPanel();
  private JComponent _shownTileModePanel = new JPanel();
  private JComponent _shownMemoryLabel = new JPanel();
  private JComponent _shownMemoryPanel = new JPanel();

  public CaffeJob() {
    super();
  }

  public CaffeJob(JobTableModel model) {
    super(model);
  }

  @Override
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

  @Override
  public String hostname() {
    return _hostConfiguration.useRemoteHost() ?
        _hostConfiguration.hostname() : "localhost";
  }

  @Override
  public final Session sshSession() throws JSchException, InterruptedException {
    return _hostConfiguration.sshSession();
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

  @Override
  protected void processModelSelectionChange() {
    if (model() == null || !model().isValid()) {
      JPanel panel = new JPanel();
      _dialogLayout.replace(_shownTileModeLabel, panel);
      _shownTileModeLabel = panel;
      panel = new JPanel();
      _dialogLayout.replace(_shownTileModePanel, panel);
      _shownTileModePanel = panel;
      panel = new JPanel();
      _dialogLayout.replace(_shownMemoryLabel, panel);
      _shownMemoryLabel = panel;
      panel = new JPanel();
      _dialogLayout.replace(_shownMemoryPanel, panel);
      _shownMemoryPanel = panel;
      _weightsFileTextField.setText("");

      super.processModelSelectionChange();

      return;
    }

    if (_shownTileModeLabel != model().tileModeSelector()) {
      _dialogLayout.replace(_shownTileModeLabel, model().tileModeSelector());
      _shownTileModeLabel = model().tileModeSelector();
    }
    if (_shownTileModePanel != model().tileModePanel()) {
      _dialogLayout.replace(_shownTileModePanel, model().tileModePanel());
      _shownTileModePanel = model().tileModePanel();
    }
    if (_shownMemoryLabel != model().memoryRequiredLabel()) {
      _dialogLayout.replace(_shownMemoryLabel, model().memoryRequiredLabel());
      _shownMemoryLabel = model().memoryRequiredLabel();
    }
    if (_shownMemoryPanel != model().memoryRequiredPanel()) {
      _dialogLayout.replace(_shownMemoryPanel, model().memoryRequiredPanel());
      _shownMemoryPanel = model().memoryRequiredPanel();
    }
    _weightsFileTextField.setText(model().weightFile);

    super.processModelSelectionChange();
  }

  @Override
  protected void createDialogElements() {
    super.createDialogElements();

    /*******************************************************************
     * Generate the GUI layout without logic
     *******************************************************************/

    // Weights
    final JLabel weightsFileLabel = new JLabel("Weight file:");
    _weightsFileTextField.setToolTipText(
        "Location of the file containing the trained network weights " +
        "on the backend server.\nIf not yet on the server, on-the-fly " +
        "file upload will be offered.");
    int marginTop = (int) Math.ceil(
        (_weightsFileChooseButton.getPreferredSize().getHeight() -
         _weightsFileTextField.getPreferredSize().getHeight()) / 2.0);
    int marginBottom = (int) Math.floor(
        (_weightsFileChooseButton.getPreferredSize().getHeight() -
         _weightsFileTextField.getPreferredSize().getHeight()) / 2.0);
    Insets insets = _weightsFileChooseButton.getMargin();
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
    _horizontalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createSequentialGroup()
            .addGroup(
                _dialogLayout.createParallelGroup(
                    GroupLayout.Alignment.TRAILING)
                .addComponent(weightsFileLabel)
                .addComponent(processFolderLabel)
                .addComponent(useGPULabel)
                .addComponent(_shownTileModeLabel)
                .addComponent(_shownMemoryLabel))
            .addGroup(
                _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_weightsFileTextField)
                    .addComponent(_weightsFileChooseButton))
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_processFolderTextField)
                    .addComponent(_processFolderChooseButton))
                .addComponent(_useGPUComboBox)
                .addComponent(_shownTileModePanel)
                .addComponent(_shownMemoryPanel)))
        .addComponent(_hostConfiguration);

    _verticalDialogLayoutGroup
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
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(_shownTileModeLabel)
            .addComponent(_shownTileModePanel))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(_shownMemoryLabel)
            .addComponent(_shownMemoryPanel))
        .addComponent(_hostConfiguration);
  }

  @Override
  protected void finalizeDialog() {
    /*******************************************************************
     * Wire controls inner to outer before setting values so that
     * value changes trigger all required updates
     *******************************************************************/

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
                    "HDF5 and caffemodel files", "h5", "H5",
                    "caffemodel", "CAFFEMODEL"));
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

    // Set uncritical fields
    _useGPUComboBox.setSelectedItem(Prefs.get("unet.gpuId", "none"));

    super.finalizeDialog();
  }

  @Override
  protected boolean checkParameters() throws InterruptedException {

    if (!super.checkParameters()) return false;

    if (!weightsFileName().isEmpty() &&
        !weightsFileName().endsWith(".caffemodel.h5")) {
      int selectedOption = JOptionPane.showConfirmDialog(
          WindowManager.getActiveWindow(),
          "The selected weight file does not end in .caffemodel.h5.\n" +
          "Are you sure that it contains pretrained model weights?",
          "Unusual weight file name",
          JOptionPane.YES_NO_CANCEL_OPTION,
          JOptionPane.QUESTION_MESSAGE);
      switch (selectedOption) {
      case JOptionPane.YES_OPTION:
        break;
      case JOptionPane.NO_OPTION:
        return false;
      case JOptionPane.CANCEL_OPTION:
      case JOptionPane.CLOSED_OPTION:
        throw new InterruptedException("Aborted by user");
      }
    }

    if (selectedGPUString().equals("none") &&
        model().getCaffeTilingParameter().contains("gpu_mem_available_MB")) {
      showMessage(
          "The 'Memory (MB)' option can only be used in GPU mode.\n" +
          "Please select a different tiling mode.");
      return false;
    }

    Prefs.set("unet.processfolder", processFolder());
    Prefs.set("unet.gpuId", selectedGPUString());

    if (_hostConfiguration.useRemoteHost() &&
        _hostConfiguration.hostname() == null) {
      showMessage(
          "No hostname specified\n" +
          "Please enter the server name for remote processing.");
      return false;
    }

    try {
      sshSession();
    }
    catch (JSchException e) {
      showError("Could not connect to " + _hostConfiguration.hostname() +
                ".\nPlease check your login credentials.", e);
      return false;
    }

    try
    {
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
          showError(
              "Cannot create files in the temporary folder of your " +
              "local file system.\n" +
              "Check write permissions and avaliable disk space.", e);
          return false;
        }
        catch (SftpException e) {
          showError("File upload to " + processFolder() + " failed.\n" +
                    "Please select a folder with write permissions.", e);
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
          showError("Cannot write to " + processFolder() + ".\n" +
                    "Check write permissions and avaliable disk space.", e);
          return false;
        }
      }

      // Check whether caffe binary exists and is executable
      ProcessResult res = null;
      String caffeBinaryPath = Prefs.get("unet.caffeBinary", "caffe");
      String caffeBaseDir = caffeBinaryPath.replaceFirst("[^/]*$", "");
      while (res == null)
      {
        if (sshSession() == null) {
          try {
            Vector<String> cmd = new Vector<String>();
            cmd.add(caffeBinaryPath);
            res = Tools.execute(cmd, progressMonitor());
          }
          catch (IOException e) {
            res = new ProcessResult();
            res.exitStatus = 1;
            res.cause = e;
          }
        }
        else {
          try {
            String cmd = caffeBinaryPath;
            res = Tools.execute(cmd, sshSession(), progressMonitor());
          }
          catch (JSchException e) {
            res.exitStatus = 1;
            res.cause = e;
          }
          catch (IOException e) {
            res.exitStatus = 1;
            res.cause = e;
          }
        }
        if (res.exitStatus != 0) {
          IJ.log("caffe not found");
          if (res.cause != null) IJ.log("Error message: " + res.cause);
          String caffePath = JOptionPane.showInputDialog(
              WindowManager.getActiveWindow(), "caffe was not found.\n" +
              "Please specify your caffe binary\n",
              Prefs.get("unet.caffeBinary", caffeBaseDir + "caffe"));
          if (caffePath == null)
              throw new InterruptedException("Dialog canceled");
          if (caffePath.equals(""))
              Prefs.set("unet.caffeBinary", caffeBaseDir + "caffe");
          else Prefs.set("unet.caffeBinary", caffePath);
          res = null;
        }
      }

      // Check whether caffe_unet binary exists and is executable
      res = null;
      while (res == null)
      {
        if (sshSession() == null) {
          try {
            Vector<String> cmd = new Vector<String>();
            cmd.add(Prefs.get(
                        "unet.caffe_unetBinary", caffeBaseDir + "caffe_unet"));
            res = Tools.execute(cmd, progressMonitor());
          }
          catch (IOException e) {
            res = new ProcessResult();
            res.exitStatus = 1;
            res.cause = e;
          }
        }
        else {
          try {
            String cmd = Prefs.get(
                "unet.caffe_unetBinary", caffeBaseDir + "caffe_unet");
            res = Tools.execute(cmd, sshSession(), progressMonitor());
          }
          catch (JSchException e) {
            res.exitStatus = 1;
            res.cause = e;
          }
          catch (IOException e) {
            res.exitStatus = 1;
            res.cause = e;
          }
        }
        if (res.exitStatus != 0) {
          IJ.log("caffe_unet not found");
          if (res.cause != null) IJ.log("Error message: " + res.cause);
          String caffePath = JOptionPane.showInputDialog(
              WindowManager.getActiveWindow(), "caffe_unet was not found.\n" +
              "Please specify your caffe_unet binary\n",
              Prefs.get("unet.caffe_unetBinary", caffeBaseDir + "caffe_unet"));
          if (caffePath == null)
              throw new InterruptedException("Dialog canceled");
          if (caffePath.equals(""))
              Prefs.set("unet.caffe_unetBinary", caffeBaseDir + "caffe_unet");
          else Prefs.set("unet.caffe_unetBinary", caffePath);
          res = null;
        }
      }
    }
    catch (JSchException e) {
      showError("SSH connection failed", e);
      return false;
    }

    return true;
  }

  @Override
  public void cleanUp(boolean keepFiles) {
    if (!keepFiles) {
      for (int i = 0; i < _createdRemoteFiles.size(); i++) {
        try {
          new SftpFileIO(sshSession(), progressMonitor()).removeFile(
              _createdRemoteFiles.get(i));
        }
        catch (Exception e) {
          IJ.log("Could not remove temporary file " +
                 _createdRemoteFiles.get(i) + ": " + e);
        }
      }
      for (int i = 0; i < _createdRemoteFolders.size(); i++) {
        try {
          new SftpFileIO(sshSession(), progressMonitor()).removeFolder(
              _createdRemoteFolders.get(i));
        }
        catch (Exception e) {
          IJ.log("Could not remove temporary folder " +
                 _createdRemoteFolders.get(i) + ": " + e);
        }
      }
    }

    // super.cleanUp checks SSH connection, so call it before disconnect!
    super.cleanUp(keepFiles);

    // Now we can safely disconnect
    try {
      if (sshSession() != null) sshSession().disconnect();
    }
    catch (JSchException|InterruptedException e) {}
  }

};
