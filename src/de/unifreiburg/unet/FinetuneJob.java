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

import ij.IJ;
import ij.Prefs;
import ij.WindowManager;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.gui.PointRoi;
import ij.gui.ImageRoi;
import ij.plugin.frame.Recorder;

import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.Color;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListDataEvent;
import javax.swing.JPanel;
import javax.swing.JCheckBox;
import javax.swing.GroupLayout;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JFormattedTextField;
import javax.swing.text.NumberFormatter;
import javax.swing.JList;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JSplitPane;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import java.util.Vector;
import java.util.Locale;

import java.text.DecimalFormat;

import com.jcraft.jsch.Session;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import caffe.Caffe;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat.Parser;
import com.google.protobuf.TextFormat;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import ch.systemsx.cisd.hdf5.IHDF5WriterConfigurator;

public class FinetuneJob extends CaffeJob implements PlugIn {

  protected int _nChannels = -1;

  private final ImagePlusListView _trainFileList = new ImagePlusListView();
  private final ImagePlusListView _validFileList = new ImagePlusListView();

  private final JPanel _trainImagesPanel = new JPanel(new BorderLayout());
  private final JPanel _validImagesPanel = new JPanel(new BorderLayout());
  private final JSplitPane _trainValidPane = new JSplitPane(
      JSplitPane.HORIZONTAL_SPLIT, _trainImagesPanel, _validImagesPanel);

  private final JPanel _elSizePanel = new JPanel(new BorderLayout());
  protected final JButton _fromImageButton = new JButton("from Image");
  private final JFormattedTextField _learningRateTextField =
      new JFormattedTextField(
          new NumberFormatter(new DecimalFormat("0.###E0")));
  private final JTextField _outModeldefTextField = new JTextField(
      "finetuned.modeldef.h5");
  private final JButton _outModeldefChooseButton =
      (UIManager.get("FileView.directoryIcon") instanceof Icon) ?
      new JButton((Icon)UIManager.get("FileView.directoryIcon")) :
      new JButton("...");
  private final JTextField _outweightsTextField = new JTextField(
      "finetuned.caffemodel.h5");
  private final JButton _outweightsChooseButton =
      hostConfiguration().finetunedFileChooseButton();
  private final JSpinner _iterationsSpinner =
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.finetuning.iterations", 1000),
              1, (int)Integer.MAX_VALUE, 1));
  private final JSpinner _validationStepSpinner =
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet.finetuning.validation_step", 1),
              1, (int)Integer.MAX_VALUE, 1));
  private final JCheckBox _downloadWeightsCheckBox =
      new JCheckBox(
          "Download Weights",
          Prefs.get("unet.finetuning.downloadWeights", false));
  private final JTextField _modelNameTextField = new JTextField(
      "finetuned model");

  private final JCheckBox _labelsAreClassesCheckBox =
      new JCheckBox("Labels are classes",
                    Prefs.get("unet.finetuning.labelsAreClasses", true));

  // Plot-related variables
  private final Color[] colormap = new Color[] {
      new Color(0, 0, 0), new Color(1.0f, 0, 0), new Color(0, 1.0f, 0),
      new Color(0, 0, 1.0f), new Color(0.5f, 0.5f, 0), new Color(1.0f, 0, 1.0f),
      new Color(0, 1.0f, 1.0f) };
  private int _currentTestIterationIdx = 0;
  private double[] _xTrain = null;
  private double[] _xValid = null;
  private PlotWindow _lossPlotWindow = null;
  private double[] _lossTrain = null;
  private double[] _lossValid = null;
  private PlotWindow _iouPlotWindow = null;
  private double[][] _intersection = null;
  private double[][] _union = null;
  private double[][] _iou = null;
  private PlotWindow _f1DetectionPlotWindow = null;
  private double[][] _nTPDetection = null;
  private double[][] _nPredDetection = null;
  private double[][] _nObjDetection = null;
  private double[][] _f1Detection = null;
  private PlotWindow _f1SegmentationPlotWindow = null;
  private double[][] _nTPSegmentation = null;
  private double[][] _nPredSegmentation = null;
  private double[][] _nObjSegmentation = null;
  private double[][] _f1Segmentation = null;

  private boolean _trainFromScratch = false;
  private boolean _isResuming = false;
  private String _solverstate = null;
  private int _nIter = 0;
  private Vector<String> _cmd = new Vector<String>();

  protected ModelDefinition _finetunedModel = null;

  public FinetuneJob() {
    super();
  }

  public FinetuneJob(JobTableModel model) {
    super(model);
  }

  public ImagePlusListView trainingList() {
    return _trainFileList;
  }

  public ImagePlusListView validationList() {
    return _validFileList;
  }

  @Override
  public void finish() {
    if (progressMonitor().finished()) return;
    try {
      showMessage(
          "Finetuned weights have been saved to " +
          _finetunedModel.weightFile +
          ((sshSession() != null) ? " on the remote host." : ""));
    }
    catch (JSchException|InterruptedException e) {}
    IJ.log("Finetuning finished");
    super.finish();
  }

  @Override
  public void abort() {
    readyCancelButton().setText("Terminating...");
    readyCancelButton().setEnabled(false);
    progressMonitor().setCanceled(true);
    if (Recorder.record) Recorder.setCommand(null);
    boolean keepFiles = false;
    if (_solverstate != null) {
      try {
        String msg =
            "Finetuned weights have been saved to " +
            _solverstate.replaceFirst(".solverstate.h5$", ".caffemodel.h5") +
            ((sshSession() != null) ? " on the remote host." : "") +
            "\nDo you want to keep generated files to resume finetuning later?";
        int res = JOptionPane.showConfirmDialog(
            WindowManager.getActiveWindow(), msg, "Save snapshot?",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (res == JOptionPane.YES_OPTION) {
          File startFile = (_finetunedModel != null &&
                            _finetunedModel.file != null &&
                            _finetunedModel.file.getParentFile() != null) ?
              new File(_finetunedModel.file.getParentFile(), "snapshot.h5") :
              new File("snapshot.h5");
          JFileChooser dlg = new JFileChooser(startFile);
          dlg.setDialogTitle("Save Snapshot to");
          dlg.setFileFilter(new FileNameExtensionFilter("*.h5", "h5", "H5"));
          dlg.setMultiSelectionEnabled(false);
          dlg.setFileSelectionMode(JFileChooser.FILES_ONLY);
          dlg.setSelectedFile(startFile);
          int res2 = dlg.showSaveDialog(WindowManager.getActiveWindow());
          if (res2 == JFileChooser.APPROVE_OPTION) {
            File outfile = dlg.getSelectedFile();
            if (!outfile.exists() ||
                (outfile.exists() &&
                 JOptionPane.showConfirmDialog(
                     WindowManager.getActiveWindow(),
                     "The selected file already exists.\nDo you want to " +
                     "overwrite it?", "Overwrite existing file?",
                     JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) ==
                 JOptionPane.YES_OPTION)) {
              saveSnapshot(dlg.getSelectedFile());
              keepFiles = true;
            }
          }
        }
      }
      catch (JSchException e) {
        showError("SSH connection failed", e);
      }
      catch (InterruptedException e) {}
    }
    cleanUp(keepFiles);
    IJ.log("U-Net job aborted");
    IJ.showProgress(1.0);
  }

  @Override
  protected void processModelSelectionChange() {
    _finetunedModel = null;
    _elSizePanel.removeAll();
    if (model() != null) {
      _elSizePanel.add(model().elementSizeUmPanel());
      _elSizePanel.setMinimumSize(
          model().elementSizeUmPanel().getMinimumSize());
      _elSizePanel.setMaximumSize(
          new Dimension(
              Integer.MAX_VALUE,
              model().elementSizeUmPanel().getPreferredSize().height));
      _modelNameTextField.setText(
          Prefs.get("unet.finetuning." + model().id + ".modelName",
                    model().name + " - finetuned"));
      _outweightsTextField.setText(
          Prefs.get("unet.finetuning." + model().id + ".outweights",
                    (model().file != null) ?
                    (model().file.getPath().replaceFirst(
                        "[.]h5$", "").replaceFirst("[.-]modeldef$", "") +
                     "-finetuned.caffemodel.h5") : "finetuned.caffemodel.h5"));
      _outModeldefTextField.setText(
          Prefs.get("unet.finetuning." + model().id + ".modeldef",
                    (model().file != null) ?
                    (model().file.getAbsolutePath()
                     .replaceFirst(".h5$", "").replaceFirst(
                         "[.-]modeldef$", "") +
                     "-finetuned.modeldef.h5") : "finetuned.modeldef.h5"));
    }
    super.processModelSelectionChange();
  }

  @Override
  protected void createDialogElements() {

    super.createDialogElements();

    _parametersDialog.setTitle("U-Net Finetuning");

    // Create Train/Test split Configurator
    int[] ids = WindowManager.getIDList();
    for (int i = 0; i < ids.length; i++)
        if (WindowManager.getImage(ids[i]).getRoi() != null ||
            WindowManager.getImage(ids[i]).getOverlay() != null)
            ((DefaultListModel<ImagePlus>)_trainFileList.getModel()).addElement(
                WindowManager.getImage(ids[i]));

    JLabel trainImagesLabel = new JLabel("Train images");
    _trainImagesPanel.add(trainImagesLabel, BorderLayout.NORTH);
    JScrollPane trainScroller = new JScrollPane(_trainFileList);
    _trainFileList.getModel().addListDataListener(new ListDataListener() {
      @Override
      public void contentsChanged(ListDataEvent e) {
        if (model() != null) model().updateMemoryConsumptionDisplay();
      }
      @Override
      public void intervalAdded(ListDataEvent e) {
        if (model() != null) model().updateMemoryConsumptionDisplay();
      }
      @Override
      public void intervalRemoved(ListDataEvent e) {
        if (model() != null) model().updateMemoryConsumptionDisplay();
      }
    });
    trainScroller.setMinimumSize(new Dimension(100, 50));
    _trainImagesPanel.add(trainScroller, BorderLayout.CENTER);

    JLabel validImagesLabel = new JLabel("Validation images");
    _validImagesPanel.add(validImagesLabel, BorderLayout.NORTH);
    JScrollPane validScroller = new JScrollPane(_validFileList);
    _validFileList.getModel().addListDataListener(new ListDataListener() {
      @Override
      public void contentsChanged(ListDataEvent e) {
        if (model() != null) model().updateMemoryConsumptionDisplay();
      }
      @Override
      public void intervalAdded(ListDataEvent e) {
        if (model() != null) model().updateMemoryConsumptionDisplay();
      }
      @Override
      public void intervalRemoved(ListDataEvent e) {
        if (model() != null) model().updateMemoryConsumptionDisplay();
      }
    });
    validScroller.setMinimumSize(new Dimension(100, 50));
    _validImagesPanel.add(validScroller, BorderLayout.CENTER);

    JLabel elSizeLabel = new JLabel("Element Size [µm]:");
    _fromImageButton.setToolTipText(
        "Use native image element size for finetuning");
    JLabel learningRateLabel = new JLabel("Learning rate:");
    _learningRateTextField.setValue(
        (Double)Prefs.get("unet.finetuning.base_learning_rate", 1e-4));
    _learningRateTextField.setToolTipText(
        "Learning rate of the optimizer. You may use scientific notation, " +
        "(e.g. 1E-4 = 0.0001, note the capital E)");

    JLabel iterationsLabel = new JLabel("Iterations:");
    _iterationsSpinner.setToolTipText("The number of training iterations");

    JLabel validationStepLabel = new JLabel("Validation interval:");
    _validationStepSpinner.setToolTipText(
        "Model performance will be evaluated on the validation set every " +
        "given number of iterations");

    JLabel idLabel = new JLabel("Network ID");
    _modelNameTextField.setToolTipText(
        "The Network ID that will be shown in the model selection combo box.");
    JLabel outModeldefLabel = new JLabel("Model definition");
    _outModeldefTextField.setToolTipText(
        "The local path the updated model definition for this finetuning " +
        "job will be stored to");
    int marginTop = (int) Math.ceil(
        (_outModeldefChooseButton.getPreferredSize().getHeight() -
         _outModeldefTextField.getPreferredSize().getHeight()) / 2.0);
    int marginBottom = (int) Math.floor(
        (_outModeldefChooseButton.getPreferredSize().getHeight() -
         _outModeldefTextField.getPreferredSize().getHeight()) / 2.0);
    Insets insets = _outModeldefChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    _outModeldefChooseButton.setMargin(insets);
    _outModeldefChooseButton.setToolTipText(
        "Select output model definition file name");

    JLabel outweightsLabel = new JLabel("Weights:");
    _outweightsTextField.setToolTipText(
        "Finetuned weights will be stored to this file");
    marginTop = (int) Math.ceil(
        (_outweightsChooseButton.getPreferredSize().getHeight() -
         _outweightsTextField.getPreferredSize().getHeight()) / 2.0);
    marginBottom = (int) Math.floor(
        (_outweightsChooseButton.getPreferredSize().getHeight() -
         _outweightsTextField.getPreferredSize().getHeight()) / 2.0);
    insets = _outweightsChooseButton.getMargin();
    insets.top -= marginTop;
    insets.left = 1;
    insets.bottom -= marginBottom;
    insets.right = 1;
    _outweightsChooseButton.setMargin(insets);
    _outweightsChooseButton.setToolTipText("Select output file name");

    _horizontalDialogLayoutGroup
        .addComponent(_trainValidPane)
        .addGroup(
            _dialogLayout.createSequentialGroup()
            .addGroup(
                _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(elSizeLabel)
                .addComponent(learningRateLabel)
                .addComponent(iterationsLabel)
                .addComponent(validationStepLabel)
                .addComponent(idLabel)
                .addComponent(outModeldefLabel)
                .addComponent(outweightsLabel))
            .addGroup(
                _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_elSizePanel)
                    .addComponent(_fromImageButton))
                .addComponent(_learningRateTextField)
                .addComponent(_iterationsSpinner)
                .addComponent(_validationStepSpinner)
                .addComponent(_modelNameTextField)
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_outModeldefTextField)
                    .addComponent(_outModeldefChooseButton))
                .addGroup(
                    _dialogLayout.createSequentialGroup()
                    .addComponent(_outweightsTextField)
                    .addComponent(_outweightsChooseButton))));
    _verticalDialogLayoutGroup
        .addComponent(_trainValidPane)
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(elSizeLabel)
            .addComponent(_elSizePanel)
            .addComponent(_fromImageButton))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(learningRateLabel)
            .addComponent(_learningRateTextField))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(iterationsLabel)
            .addComponent(_iterationsSpinner))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(validationStepLabel)
            .addComponent(_validationStepSpinner))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(idLabel)
            .addComponent(_modelNameTextField))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(outModeldefLabel)
            .addComponent(_outModeldefTextField)
            .addComponent(_outModeldefChooseButton))
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(outweightsLabel)
            .addComponent(_outweightsTextField)
            .addComponent(_outweightsChooseButton));

    _labelsAreClassesCheckBox.setToolTipText(
        "Check if your labels indicate class labels, otherwise the plugin " +
        "assumes that the problem is binary foreground/background " +
        "segmentation and labels are instance labels.");
    _configPanel.add(_labelsAreClassesCheckBox);

    _downloadWeightsCheckBox.setToolTipText(
        "Check if you want to download the weights from the server. " +
        "The weights file will be placed in the same folder as the new " +
        "model definition file. (Ignored for local processing)");
    _configPanel.add(_downloadWeightsCheckBox);
  }

  @Override
  protected void finalizeDialog() {
    _outModeldefChooseButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File startFolder = new File(_outModeldefTextField.getText());
            JFileChooser f = new JFileChooser(startFolder);
            f.setDialogTitle("Select output model definition file name");
            f.setFileFilter(
                new FileNameExtensionFilter("HDF5 files", "h5", "H5"));
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int res = f.showDialog(_parametersDialog, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;
            _outModeldefTextField.setText(
                f.getSelectedFile().getAbsolutePath());
          }});

    _outweightsChooseButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            File startFolder = new File(_outweightsTextField.getText());
            JFileChooser f = new JFileChooser(startFolder);
            f.setDialogTitle("Select output file name");
            f.setFileFilter(
                new FileNameExtensionFilter("HDF5 files", "h5", "H5"));
            f.setMultiSelectionEnabled(false);
            f.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int res = f.showDialog(_parametersDialog, "Select");
            if (res != JFileChooser.APPROVE_OPTION) return;
            _outweightsTextField.setText(
                f.getSelectedFile().getAbsolutePath());
          }});

    _trainValidPane.setDividerLocation(0.5);

    _fromImageButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (model() == null) return;
            if (_trainFileList.getModel().getSize() == 0) return;
            ImagePlus imp = _trainFileList.getModel().getElementAt(0);
            model().setElementSizeUm(Tools.getElementSizeUm(imp));
          }});

    super.finalizeDialog();
  }

  @Override
  protected boolean checkParameters() throws InterruptedException {

    if (!super.checkParameters()) return false;

    if (_trainFileList.getModel().getSize() == 0) {
      showMessage("U-Net Finetuning requires at least one training image.");
      return false;
    }

    String pattern = "(([iI][gG][nN][oO][rR][eE])|(" +
        ((model().nDims() == 2) ?
         "[0-9a-zA-Z]+[^#]*(#[0-9]+)?" :
         "[0-9a-zA-Z]+[^#]*#[0-9]+") + "))(-[0-9]+)*";
    String pointRoiPattern = "[0-9a-zA-Z]+[^#]*(#[0-9]+)?(-[0-9]+)*";

    int nTrain = _trainFileList.getModel().getSize();
    int nValid = _validFileList.getModel().getSize();
    ImagePlus[] allImages = new ImagePlus[nTrain + nValid];
    for (int i = 0; i < nTrain; ++i)
        allImages[i] = ((DefaultListModel<ImagePlus>)
                        _trainFileList.getModel()).get(i);
    for (int i = 0; i < nValid; ++i)
        allImages[nTrain + i] = ((DefaultListModel<ImagePlus>)
                                 _validFileList.getModel()).get(i);

    for (ImagePlus imp : allImages) {
      int nc = (imp.getType() == ImagePlus.COLOR_256 ||
                imp.getType() == ImagePlus.COLOR_RGB) ? 3 :
          imp.getNChannels();
      if (_nChannels == -1) _nChannels = nc;
      if (nc != _nChannels) {
        showMessage("U-Net Finetuning requires that all training and " +
                    "validation images have the same number of channels.");
        return false;
      }

      if (imp.getOverlay() == null) {
        showMessage(
            "Image " + imp.getTitle() + " does not contain " +
            "annotations.\n" +
            "Please add annotations as overlay to training and " +
            "validation images or remove images without annotations from " +
            "the corresponding list.");
        return false;
      }

      if (model().nDims() == 3 ||
          _labelsAreClassesCheckBox.isSelected()) {
        for (Roi roi : imp.getOverlay().toArray()) {
          if (roi instanceof ImageRoi) continue;
          if (roi.getName() == null ||
              (roi instanceof PointRoi &&
               !roi.getName().matches(pointRoiPattern)) ||
              (!(roi instanceof PointRoi) &&
               !roi.getName().matches(pattern))) {
            showMessage(
                "Could not parse name of at least one ROI.\n" +
                "All ROIs must be named <class>" +
                ((model().nDims() == 2) ? "[" : "") + "#<instance number>" +
                ((model().nDims() == 2) ? "]" : "") + " or ignore.\n" +
                "Make sure that all ROIs belonging to the same object have " +
                "the same instance number.");
            return false;
          }
        }
      }
    }

    String caffe_unetBinary =
        Prefs.get("unet.caffe_unetBinary", "caffe_unet");

    ProcessResult res = null;

    try
    {
      if (sshSession() != null) {

        try {
          model().remoteAbsolutePath = processFolder() + id() + ".modeldef.h5";
          _createdRemoteFolders.addAll(
              new SftpFileIO(sshSession(), progressMonitor()).put(
                  model().file, model().remoteAbsolutePath));
          _createdRemoteFiles.add(model().remoteAbsolutePath);
          Prefs.set("unet.processfolder", processFolder());
        }
        catch (SftpException|JSchException e) {
          showError("Model upload failed.\nDo you have sufficient " +
                    "permissions to create the processing folder on " +
                    "the remote host?", e);
          return false;
        }
        catch (IOException e) {
          showError("Model upload failed. Could not read model file.", e);
          return false;
        }

        try {
          boolean weightsUploaded = false;
          do {
            String cmd =
                caffe_unetBinary + " check_model_and_weights_h5 -model \"" +
                model().remoteAbsolutePath + "\" -weights \"" +
                weightsFileName() + "\" -n_channels " + _nChannels + " " +
                caffeGPUParameter();
            res = Tools.execute(cmd, sshSession(), progressMonitor());

            if (res.exitStatus != 0) {
              if (Tools.getCaffeError(res.cerr) > 0) {
                showMessage("Model check failed:\n" +
                            Tools.getCaffeErrorString(res.cerr));
                return false;
              }
              IJ.log(Tools.getCaffeErrorString(res.cerr));
            }

            if (weightsUploaded && res.exitStatus != 0) break;
            if (!weightsUploaded && res.exitStatus != 0) {
              Object[] options = {
                  "Train from scratch", "Upload weights", "Cancel" };
              int selectedOption = JOptionPane.showOptionDialog(
                  WindowManager.getActiveWindow(),
                  "No compatible pre-trained weights found at the given " +
                  "location on the server.\n" +
                  "Please select whether you want to train from scratch or " +
                  "upload weights.", "No weights found",
                  JOptionPane.YES_NO_CANCEL_OPTION,
                  JOptionPane.QUESTION_MESSAGE,
                  null, options, options[0]);
              switch (selectedOption) {
              case JOptionPane.YES_OPTION:
                _trainFromScratch = true;
                res.exitStatus = 0;
                break;
              case JOptionPane.NO_OPTION: {
                File startFile =
                    (model() == null ||
                     model().file == null ||
                     model().file.getParentFile() == null) ?
                    new File(".") : model().file.getParentFile();
                JFileChooser f = new JFileChooser(startFile);
                f.setDialogTitle("Select trained U-Net weights");
                f.setFileFilter(
                    new FileNameExtensionFilter(
                        "*.h5 or *.caffemodel",
                        "h5", "H5", "caffemodel", "CAFFEMODEL"));
                f.setMultiSelectionEnabled(false);
                f.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int res2 = f.showDialog(
                    WindowManager.getActiveWindow(), "Select");
                if (res2 != JFileChooser.APPROVE_OPTION)
                    throw new InterruptedException("Aborted by user");
                try {
                  new SftpFileIO(sshSession(), progressMonitor()).put(
                      f.getSelectedFile(), weightsFileName());
                  weightsUploaded = true;
                }
                catch (SftpException e) {
                  res.exitStatus = 3;
                  res.shortErrorString =
                      "Upload failed.\nDo you have sufficient " +
                      "permissions to create a file at the given " +
                      "backend server path?";
                  res.cerr = e.getMessage();
                  res.cause = e;
                  break;
                }
                break;
              }
              case JOptionPane.CANCEL_OPTION:
              case JOptionPane.CLOSED_OPTION:
                throw new InterruptedException("Aborted by user");
              }
            }
          }
          while (res.exitStatus != 0);
        }
        catch (JSchException e) {
          res.exitStatus = 1;
          res.shortErrorString = "SSH connection error";
          res.cerr = e.getMessage();
          res.cause = e;
        }
        catch (IOException e) {
          res.exitStatus = 1;
          res.shortErrorString = "Input/Output error";
          res.cerr = e.getMessage();
          res.cause = e;
        }
      }
      else {
        try {
          Vector<String> cmd = new Vector<String>();
          cmd.add(caffe_unetBinary);
          cmd.add("check_model_and_weights_h5");
          cmd.add("-model");
          cmd.add(model().file.getAbsolutePath());
          cmd.add("-weights");
          cmd.add(weightsFileName());
          cmd.add("-n_channels");
          cmd.add(new Integer(_nChannels).toString());
          if (!caffeGPUParameter().equals("")) {
            cmd.add(caffeGPUParameter().split(" ")[0]);
            cmd.add(caffeGPUParameter().split(" ")[1]);
          }
          res = Tools.execute(cmd, progressMonitor());
          if (res.exitStatus != 0) {
            int selectedOption = JOptionPane.showConfirmDialog(
                WindowManager.getActiveWindow(),
                "No compatible pre-trained weights found at the given " +
                "location.\n" +
                "Do you want to train from scratch?", "Start new Training?",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            switch (selectedOption) {
            case JOptionPane.YES_OPTION:
              _trainFromScratch = true;
              res.exitStatus = 0;
              break;
            case JOptionPane.NO_OPTION:
              res.exitStatus = 2;
              res.shortErrorString = "Weight file selection required";
              res.cerr = "Weight file " + weightsFileName() + " not found";
              break;
            case JOptionPane.CANCEL_OPTION:
            case JOptionPane.CLOSED_OPTION:
              throw new InterruptedException("Aborted by user");
            }
          }
        }
        catch (IOException e) {
          res.exitStatus = 1;
          res.shortErrorString = "Input/Output error";
          res.cerr = e.getMessage();
          res.cause = e;
        }
      }
      if (res.exitStatus != 0) {
        // User decided to change weight file, so don't bother him with
        // additional message boxes
        if (res.exitStatus == 2) return false;
        showError(
            "Model/Weight check failed:\n" + res.shortErrorString, res.cause);
        return false;
      }

      Prefs.set("unet.finetuning.base_learning_rate",
                (Double)_learningRateTextField.getValue());
      Prefs.set("unet.finetuning.iterations",
                (Integer)_iterationsSpinner.getValue());
      Prefs.set("unet.finetuning.validation_step",
                (Integer)_validationStepSpinner.getValue());
      Prefs.set("unet.finetuning.downloadWeights",
                _downloadWeightsCheckBox.isSelected());
      Prefs.set("unet.finetuning." + model().id + ".modelName",
                _modelNameTextField.getText());
      Prefs.set("unet.finetuning." + model().id + ".outweights",
                _outweightsTextField.getText());
      Prefs.set("unet.finetuning." + model().id + ".modeldef",
                _outModeldefTextField.getText());
      Prefs.set("unet.finetuning.labelsAreClasses",
                _labelsAreClassesCheckBox.isSelected());

      _finetunedModel = model().duplicate();
      _finetunedModel.id += "-" + id();
      _finetunedModel.file = new File(_outModeldefTextField.getText());
      _finetunedModel.name = _modelNameTextField.getText();
      _finetunedModel.weightFile = _outweightsTextField.getText();
    }
    catch (JSchException e) {
      showError("SSH connection failed", e);
      return false;
    }

    return true;
  }

  public boolean getParameters() throws InterruptedException {

    if (WindowManager.getImageTitles().length == 0) {
      IJ.noImage();
      return false;
    }

    progressMonitor().push("Waiting for user input", 0.0f, 0.0f);

    do {
      _parametersDialog.setVisible(true);

      // Dialog was cancelled
      if (!_parametersDialog.isDisplayable())
          throw new InterruptedException("Dialog canceled");
    }
    while (!checkParameters());

    _parametersDialog.dispose();

    if (jobTable() != null) jobTable().fireTableDataChanged();

    progressMonitor().pop();

    return true;
  }

  private void parseCaffeOutputString(String msg) {
    int idx = -1;
    String line = null;
    while (msg.length() > 0) {
      if ((idx = msg.indexOf('\n')) != -1) {
        line = msg.substring(0, idx);
        msg = msg.substring(idx + 1);
      }
      else {
        line = msg;
        msg = "";
      }

      // Increment test iteration counter and reset class counters
      if (line.matches("^.*Iteration [0-9]+, Testing net.*$")) {
        _currentTestIterationIdx = Integer.valueOf(
            line.split("Iteration ")[1].split(",")[0]) /
            (Integer)_validationStepSpinner.getValue();
      }

      // Accumulate nTP Detection
      if (line.matches(
              "^.*F1_detection - Per class true positive count: .*$")) {
        String[] values =
            line.split(
                "F1_detection - Per class true positive count: ")[1].split(" ");
        for (int c = 0; c < values.length; ++c)
            _nTPDetection[c][_currentTestIterationIdx] +=
                Double.valueOf(values[c]);
      }

      // Accumulate nPredictions Detection
      if (line.matches(
              "^.*F1_detection - Per class prediction count: .*$")) {
        String[] values =
            line.split(
                "F1_detection - Per class prediction count: ")[1].split(" ");
        for (int c = 0; c < values.length; ++c)
            _nPredDetection[c][_currentTestIterationIdx] +=
                Double.valueOf(values[c]);
      }

      // Accumulate nObjects Detection
      if (line.matches(
              "^.*F1_detection - Per class object count: .*$")) {
        String[] values =
            line.split("F1_detection - Per class object count: ")[1].split(" ");
        for (int c = 0; c < values.length; ++c)
            _nObjDetection[c][_currentTestIterationIdx] +=
                Double.valueOf(values[c]);
      }

      // Accumulate nTP Segmentation
      if (line.matches(
              "^.*F1_segmentation - Per class true positive count: .*$")) {
        String[] values =
            line.split(
                "F1_segmentation - Per class true positive count: ")[1].split(
                    " ");
        for (int c = 0; c < values.length; ++c)
            _nTPSegmentation[c][_currentTestIterationIdx] +=
                Double.valueOf(values[c]);
      }

      // Accumulate nPredictions Segmentation
      if (line.matches(
              "^.*F1_segmentation - Per class prediction count: .*$")) {
        String[] values =
            line.split(
                "F1_segmentation - Per class prediction count: ")[1].split(" ");
        for (int c = 0; c < values.length; ++c)
            _nPredSegmentation[c][_currentTestIterationIdx] +=
                Double.valueOf(values[c]);
      }

      // Accumulate nObjects Segmentation
      if (line.matches(
              "^.*F1_segmentation - Per class object count: .*$")) {
        String[] values =
            line.split(
                "F1_segmentation - Per class object count: ")[1].split(" ");
        for (int c = 0; c < values.length; ++c)
            _nObjSegmentation[c][_currentTestIterationIdx] +=
                Double.valueOf(values[c]);
      }

      // Accumulate intersections
      if (line.matches("^.*IoU - Per class intersection: .*$")) {
        String[] values =
            line.split("IoU - Per class intersection: ")[1].split(" ");
        for (int c = 0; c < values.length; ++c)
            _intersection[c][_currentTestIterationIdx] +=
                Double.valueOf(values[c]);
      }

      // Accumulate unions
      if (line.matches("^.*IoU - Per class union: .*$")) {
        String[] values =
            line.split("IoU - Per class union: ")[1].split(" ");
        for (int c = 0; c < values.length; ++c)
            _union[c][_currentTestIterationIdx] +=
                Double.valueOf(values[c]);
      }

      // Update validation loss
      if (line.matches("^.*Test net output #[0-9]*: loss_valid = .*$")) {
        double loss = Double.valueOf(
            line.split("loss_valid = ")[1].split(" ")[0]);
        _lossValid[_currentTestIterationIdx] = loss;
      }

      // Update loss plot and progress
      if (line.matches("^.*Iteration [0-9]+ .* loss = .*$")) {
        int iter = Integer.valueOf(line.split("Iteration ")[1].split(" ")[0]);
        double loss = Double.valueOf(line.split("loss = ")[1]);
        _lossTrain[iter] = loss;
        Plot plot = new Plot("Finetuning Evolution", "Iteration", "Loss");
        plot.setColor(Color.black);
        plot.addPoints(_xTrain, _lossTrain, Plot.LINE);
        plot.setColor(Color.red);
        plot.addPoints(_xValid, _lossValid, Plot.LINE);
        plot.setColor(Color.black);
        String legendString = "Training\nValidation";
        plot.addLegend(legendString);
        if (_lossPlotWindow == null) {
          plot.setLimits(0.0, (double)(_xTrain.length - 1), 0.0, Double.NaN);
          _lossPlotWindow = plot.show();
        }
        else {
          double[] oldLimits = _lossPlotWindow.getPlot().getLimits();
          plot.setLimits(
              oldLimits[0], oldLimits[1], oldLimits[2], oldLimits[3]);
          plot.useTemplate(_lossPlotWindow.getPlot(), Plot.COPY_SIZE);
          _lossPlotWindow.drawPlot(plot);
        }
        progressMonitor().count(
            "Finetuning iteration " + iter + "/" + (_xTrain.length - 1) +
            " loss = " + loss, 1);
      }

      // Update IoU plot
      if (line.matches("^.*Test net output #[0-9]*: IoU = .*$")) {
        Plot plot = new Plot(
            "Finetuning Evolution", "Iteration", "Intersection over Union");
        String legendString = "";
        for (int c = 0; c < _finetunedModel.classNames.length - 1; ++c) {
          _iou[c][_currentTestIterationIdx] =
              _intersection[c][_currentTestIterationIdx] /
              _union[c][_currentTestIterationIdx];
          plot.setColor(colormap[c % colormap.length]);
          plot.addPoints(_xValid, _iou[c], Plot.LINE);
          legendString += _finetunedModel.classNames[c + 1] +
              ((c < _finetunedModel.classNames.length - 1) ? "\n" : "");
        }
        plot.setColor(Color.black);
        plot.addLegend(legendString);
        if (_iouPlotWindow == null) {
          plot.setLimits(0.0, (double)(_xTrain.length - 1), 0.0, 1.0);
          _iouPlotWindow = plot.show();
        }
        else {
          double[] oldLimits = _iouPlotWindow.getPlot().getLimits();
          plot.setLimits(
              oldLimits[0], oldLimits[1], oldLimits[2], oldLimits[3]);
          plot.useTemplate(_iouPlotWindow.getPlot(), Plot.COPY_SIZE);
          _iouPlotWindow.drawPlot(plot);
        }
      }

      // Update F1 Detection plot
      if (line.matches("^.*Test net output #[0-9]*: F1_detection = .*$")) {
        Plot plot = new Plot(
            "Finetuning Evolution", "Iteration", "F1 (Detection)");
        String legendString = "";
        for (int c = 0; c < _finetunedModel.classNames.length - 1; ++c) {
          double prec = _nTPDetection[c][_currentTestIterationIdx] /
              _nPredDetection[c][_currentTestIterationIdx];
          double rec = _nTPDetection[c][_currentTestIterationIdx] /
              _nObjDetection[c][_currentTestIterationIdx];
          _f1Detection[c][_currentTestIterationIdx] =
              2.0 * prec * rec / (prec + rec);
          plot.setColor(colormap[c % colormap.length]);
          plot.addPoints(_xValid, _f1Detection[c], Plot.LINE);
          legendString += _finetunedModel.classNames[c + 1] +
              ((c < _finetunedModel.classNames.length - 1) ? "\n" : "");
        }
        plot.setColor(Color.black);
        plot.addLegend(legendString);
        if (_f1DetectionPlotWindow == null) {
          plot.setLimits(0.0, (double)(_xTrain.length - 1), 0.0, 1.0);
          _f1DetectionPlotWindow = plot.show();
        }
        else {
          double[] oldLimits = _f1DetectionPlotWindow.getPlot().getLimits();
          plot.setLimits(
              oldLimits[0], oldLimits[1], oldLimits[2], oldLimits[3]);
          plot.useTemplate(_f1DetectionPlotWindow.getPlot(), Plot.COPY_SIZE);
          _f1DetectionPlotWindow.drawPlot(plot);
        }
      }

      // Update F1 Segmentation plot
      if (line.matches("^.*Test net output #[0-9]*: F1_segmentation = .*$")) {
        Plot plot = new Plot(
            "Finetuning Evolution", "Iteration", "F1 (Segmentation)");
        String legendString = "";
        for (int c = 0; c < _finetunedModel.classNames.length - 1; ++c) {
          double prec = _nTPSegmentation[c][_currentTestIterationIdx] /
              _nPredSegmentation[c][_currentTestIterationIdx];
          double rec = _nTPSegmentation[c][_currentTestIterationIdx] /
              _nObjSegmentation[c][_currentTestIterationIdx];
          _f1Segmentation[c][_currentTestIterationIdx] =
              2.0 * prec * rec / (prec + rec);
          plot.setColor(colormap[c % colormap.length]);
          plot.addPoints(_xValid, _f1Segmentation[c], Plot.LINE);
          legendString += _finetunedModel.classNames[c + 1] +
              ((c < _finetunedModel.classNames.length - 1) ? "\n" : "");
        }
        plot.setColor(Color.black);
        plot.addLegend(legendString);
        if (_f1SegmentationPlotWindow == null) {
          plot.setLimits(0.0, (double)(_xTrain.length - 1), 0.0, 1.0);
          _f1SegmentationPlotWindow = plot.show();
        }
        else {
          double[] oldLimits = _f1SegmentationPlotWindow.getPlot().getLimits();
          plot.setLimits(
              oldLimits[0], oldLimits[1], oldLimits[2], oldLimits[3]);
          plot.useTemplate(_f1SegmentationPlotWindow.getPlot(), Plot.COPY_SIZE);
          _f1SegmentationPlotWindow.drawPlot(plot);
        }
      }
    }
  }

  protected final void prepareFinetuning(
      String[] trainBlobFileNames, Vector<String> validBlobFileNames)
      throws InterruptedException, IOException, JSchException, SftpException {

    // Create train and valid file list files
    progressMonitor().push("Creating train file list", 0.0f, 0.2f);
    String trainFileListAbsolutePath =
        processFolder() + id() + "-trainfilelist.txt";
    progressMonitor().count("Create train and valid file lists", 0);
    File outfile = (sshSession() != null) ?
        File.createTempFile(id(), "-trainfilelist.txt") :
        new File(trainFileListAbsolutePath);
    if (sshSession() == null) outfile.createNewFile();
    BufferedWriter out = new BufferedWriter(new FileWriter(outfile));
    for (String fName : trainBlobFileNames) out.write(fName + "\n");
    out.close();
    if (sshSession() != null) {
      _createdRemoteFolders.addAll(
          new SftpFileIO(sshSession(), progressMonitor()).put(
              outfile, trainFileListAbsolutePath));
      _createdRemoteFiles.add(trainFileListAbsolutePath);
      outfile.delete();
    }
    else _createdLocalFiles.add(outfile);

    progressMonitor().pop();
    progressMonitor().push("Creating validation file list", 0.2f, 0.4f);

    String validFileListAbsolutePath =
        processFolder() + id() + "-validfilelist.txt";
    if (validBlobFileNames.size() != 0) {
      outfile = (sshSession() != null) ?
          File.createTempFile(id(), "-validfilelist.txt") :
          new File(validFileListAbsolutePath);
      if (sshSession() == null) outfile.createNewFile();
      out = new BufferedWriter(new FileWriter(outfile));
      for (String fName : validBlobFileNames) out.write(fName + "\n");
      out.close();
      if (sshSession() != null) {
        _createdRemoteFolders.addAll(
            new SftpFileIO(sshSession(), progressMonitor()).put(
                outfile, validFileListAbsolutePath));
        _createdRemoteFiles.add(validFileListAbsolutePath);
        outfile.delete();
      }
      else _createdLocalFiles.add(outfile);
    }

    progressMonitor().pop();
    progressMonitor().push("Creating model prototxt", 0.4f, 0.6f);

    // model.prototxt
    Caffe.NetParameter.Builder nb = Caffe.NetParameter.newBuilder();
    TextFormat.getParser().merge(_finetunedModel.modelPrototxt, nb);

    boolean inputShapeSet = false;
    for (Caffe.LayerParameter.Builder lb : nb.getLayerBuilderList()) {
      if (lb.getType().equals("HDF5Data")) {
        lb.getHdf5DataParamBuilder().setSource(trainFileListAbsolutePath);
      }

      if (lb.getType().equals("CreateDeformation")) {
        if (_finetunedModel.nDims() == 3) {
          lb.getCreateDeformationParamBuilder()
              .setNz(_finetunedModel.getTileShape()[0])
              .setNy(_finetunedModel.getTileShape()[1])
              .setNx(_finetunedModel.getTileShape()[2]);
        }
        else {
          lb.getCreateDeformationParamBuilder()
              .setNy(_finetunedModel.getTileShape()[0])
              .setNx(_finetunedModel.getTileShape()[1]);
        }
        inputShapeSet = true;
      }
    }
    if (!inputShapeSet)
    {
      IJ.error(
          "U-Net Finetuning",
          "The selected model cannot be finetuned using this Plugin.\n" +
          "It must contain a CreateDeformationLayer for data " +
          "augmentation.");
      throw new InterruptedException();
    }

    // Adapt number of output channels if number of classes differs from
    // number of classes in model. The caffe backend must silently
    // resize the weight blob for the last layer accordingly. If number
    // of classes is decreased from n to k, all weights for classes k+1
    // to n are just ignored, if the number of classes increases new
    // weights will be initialized using the filler for the layer.

    // Get number of classes in model
    for (Caffe.LayerParameter.Builder lb : nb.getLayerBuilderList()) {
      int i = 0;
      for (; i < lb.getTopCount() && !lb.getTop(i).equals("score"); ++i);
      if (i == lb.getTopCount()) continue;
      if (!lb.hasConvolutionParam()) {
        IJ.error(
            "U-Net Finetuning",
            "The selected model cannot be finetuned using this Plugin.\n" +
            "Scores must be generated with a Convolution layer.");
        throw new InterruptedException();
      }
      int nClassesModel = lb.getConvolutionParam().getNumOutput();
      if (nClassesModel != _finetunedModel.classNames.length)
          lb.getConvolutionParamBuilder().setNumOutput(
              _finetunedModel.classNames.length);
    }

    // Save model definition file before adding validation structures
    _finetunedModel.modelPrototxt = TextFormat.printToString(nb);
    _finetunedModel.save();

    progressMonitor().pop();
    progressMonitor().push(
        "Adding validation structures to model prototxt", 0.6f, 0.8f);

    // Add test layers if a validation set is given
    if (validBlobFileNames.size() != 0) {
      nb.addLayer(
          0, Caffe.LayerParameter.newBuilder().setType("HDF5Data")
          .addTop(_finetunedModel.inputBlobName).addTop(
              "labels").addTop("weights")
          .setName("loaddata_valid").setHdf5DataParam(
              Caffe.HDF5DataParameter.newBuilder().setBatchSize(1)
              .setShuffle(false).setSource(validFileListAbsolutePath))
          .addInclude(
              Caffe.NetStateRule.newBuilder().setPhase(
                  Caffe.Phase.TEST)));
      nb.addLayer(
          Caffe.LayerParameter.newBuilder().setType("SoftmaxWithLoss")
          .addBottom("score").addBottom("labels")
          .addBottom("weights").setName("loss_valid")
          .addTop("loss_valid")
          .addInclude(
              Caffe.NetStateRule.newBuilder().setPhase(Caffe.Phase.TEST)));
      nb.addLayer(
          Caffe.LayerParameter.newBuilder().setType("IoU")
          .addBottom("score").addBottom("labels")
          .addBottom("weights").setName("IoU")
          .addTop("IoU")
          .addInclude(
              Caffe.NetStateRule.newBuilder().setPhase(Caffe.Phase.TEST)));
      nb.addLayer(
          Caffe.LayerParameter.newBuilder().setType("F1")
          .addBottom("score").addBottom("labels")
          .addBottom("weights").setName("F1_detection")
          .addTop("F1_detection")
          .setF1Param(
              Caffe.F1Parameter.newBuilder().setDistanceMode(
                  Caffe.F1Parameter.DistanceMode.EUCLIDEAN)
              .setDistanceThreshold(3.0f))
          .addInclude(
              Caffe.NetStateRule.newBuilder().setPhase(Caffe.Phase.TEST)));
      nb.addLayer(
          Caffe.LayerParameter.newBuilder().setType("F1")
          .addBottom("score").addBottom("labels")
          .addBottom("weights").setName("F1_segmentation")
          .addTop("F1_segmentation")
          .addInclude(
              Caffe.NetStateRule.newBuilder().setPhase(Caffe.Phase.TEST)));
    }

    _finetunedModel.modelPrototxtAbsolutePath =
        processFolder() + id() + "-model.prototxt";
    _finetunedModel.modelPrototxt = TextFormat.printToString(nb);
    if (sshSession() != null) {
      File tmpFile = File.createTempFile(id(), "-model.prototxt");
      _finetunedModel.saveModelPrototxt(tmpFile);
      _createdRemoteFolders.addAll(
          new SftpFileIO(sshSession(), progressMonitor()).put(
              tmpFile, _finetunedModel.modelPrototxtAbsolutePath));
      _createdRemoteFiles.add(_finetunedModel.modelPrototxtAbsolutePath);
      tmpFile.delete();
    }
    else {
      File modelFile = new File(_finetunedModel.modelPrototxtAbsolutePath);
      modelFile.createNewFile();
      _finetunedModel.saveModelPrototxt(modelFile);
      _createdLocalFiles.add(modelFile);
    }

    progressMonitor().pop();
    progressMonitor().push("Creating solver prototxt", 0.8f, 1.0f);

    // solver.prototxt
    progressMonitor().count("Create solver prototxt", 0);
    Caffe.SolverParameter.Builder sb = Caffe.SolverParameter.newBuilder();
    TextFormat.getParser().merge(_finetunedModel.solverPrototxt, sb);
    sb.setNet(_finetunedModel.modelPrototxtAbsolutePath);
    sb.setBaseLr(((Double)_learningRateTextField.getValue()).floatValue());
    sb.setSnapshot((Integer)_iterationsSpinner.getValue());
    sb.setMaxIter((Integer)_iterationsSpinner.getValue());
    sb.setSnapshotPrefix(processFolder() + id() + "-snapshot");
    sb.setLrPolicy("fixed");
    sb.setType("Adam");
    sb.setSnapshotFormat(Caffe.SolverParameter.SnapshotFormat.HDF5);

    if (validBlobFileNames.size() != 0) {
      sb.addTestIter(validBlobFileNames.size()).setTestInterval(
          (Integer)_validationStepSpinner.getValue());
    }

    _finetunedModel.solverPrototxtAbsolutePath =
        processFolder() + id() + "-solver.prototxt";
    _finetunedModel.solverPrototxt = TextFormat.printToString(sb);
    if (sshSession() != null) {
      File tmpFile = File.createTempFile(id(), "-solver.prototxt");
      _finetunedModel.saveSolverPrototxt(tmpFile);
      _createdRemoteFolders.addAll(
          new SftpFileIO(sshSession(), progressMonitor()).put(
              tmpFile, _finetunedModel.solverPrototxtAbsolutePath));
      _createdRemoteFiles.add(_finetunedModel.solverPrototxtAbsolutePath);
      tmpFile.delete();
    }
    else {
      File solverFile =
          new File(_finetunedModel.solverPrototxtAbsolutePath);
      solverFile.createNewFile();
      _finetunedModel.saveSolverPrototxt(solverFile);
      _createdLocalFiles.add(solverFile);
    }

    progressMonitor().pop();
  }

  protected final void startFinetuning()
      throws JSchException, IOException, InterruptedException {
    _cmd.add(Prefs.get("unet.caffeBinary", "caffe"));
    _cmd.add("train");
    _cmd.add("-solver");
    _cmd.add(_finetunedModel.solverPrototxtAbsolutePath);
    if (!_trainFromScratch) {
      _cmd.add("-weights");
      _cmd.add(weightsFileName());
    }
    if (selectedGPUString().contains("GPU ")) {
      _cmd.add("-gpu");
      _cmd.add(selectedGPUString().substring(selectedGPUString().length() - 1));
    }
    else if (selectedGPUString().contains("all")) {
      _cmd.add("-gpu");
      _cmd.add("all");
    }
    _cmd.add("-sigint_effect");
    _cmd.add("stop");

    _nIter = (Integer)_iterationsSpinner.getValue();
    int nValidations = _nIter / (Integer)_validationStepSpinner.getValue();
    int nClasses = _finetunedModel.classNames.length - 1;

    progressMonitor().push(
        "Finetuning", 0.0f,
        _downloadWeightsCheckBox.isSelected() ? 0.95f : 1.0f);
    progressMonitor().init(_nIter);

    // Set up ImagePlus for plotting the loss curves
    _xTrain = new double[_nIter + 1];
    _xValid = new double[nValidations + 1];
    _lossTrain = new double[_nIter + 1];
    _lossValid = new double[nValidations + 1];
    _intersection = new double[nClasses][nValidations + 1];
    _union = new double[nClasses][nValidations + 1];
    _iou = new double[nClasses][nValidations + 1];
    _nTPDetection = new double[nClasses][nValidations + 1];
    _nPredDetection = new double[nClasses][nValidations + 1];
    _nObjDetection = new double[nClasses][nValidations + 1];
    _f1Detection = new double[nClasses][nValidations + 1];
    _nTPSegmentation = new double[nClasses][nValidations + 1];
    _nPredSegmentation = new double[nClasses][nValidations + 1];
    _nObjSegmentation = new double[nClasses][nValidations + 1];
    _f1Segmentation = new double[nClasses][nValidations + 1];
    for (int i = 0; i <= _nIter; i++) {
      _xTrain[i] = i + 1;
      _lossTrain[i] = Double.NaN;
    }
    for (int i = 0; i <= nValidations ; i++) {
      _xValid[i] = i * (Integer)_validationStepSpinner.getValue();
      _lossValid[i] = Double.NaN;
      for (int k = 0; k < nClasses; ++k) {
        _intersection[k][i] = 0.0;
        _union[k][i] = 0.0;
        _iou[k][i] = Double.NaN;
        _nTPDetection[k][i] = 0.0;
        _nPredDetection[k][i] = 0.0;
        _nObjDetection[k][i] = 0.0;
        _f1Detection[k][i] = Double.NaN;
        _nTPSegmentation[k][i] = 0.0;
        _nPredSegmentation[k][i] = 0.0;
        _nObjSegmentation[k][i] = 0.0;
        _f1Segmentation[k][i] = Double.NaN;
      }
    }

    runFinetuning();
  }

  public void resumeFinetuning(File snapshotFile) {

    _finetunedModel = new ModelDefinition(this);
    _finetunedModel.loadSnapshot(snapshotFile);
    setModel(_finetunedModel);

    IHDF5Reader snapshotReader = HDF5Factory.openForReading(snapshotFile);

    // Setup parameters
    String[] cmdArray = snapshotReader.string().readArray("/commandLine");
    for (String cmd : cmdArray) _cmd.add(cmd);

    // Set required parameters
    setId(snapshotReader.string().getAttr("/", "id"));
    setProcessFolder(snapshotReader.string().getAttr("/", "processFolder"));
    setWeightsFileName(snapshotReader.string().getAttr("/", "weightsFileName"));
    _outweightsTextField.setText(
        snapshotReader.string().getAttr("/", "outputWeightsFile"));
    _downloadWeightsCheckBox.setSelected(
        snapshotReader.int8().getAttr("/", "downloadWeights") == 1);

    // Load current fintuning state
    int currentIter = snapshotReader.int32().getAttr("/", "currentIteration");
    _xTrain = snapshotReader.float64().readArray("/xTrain");
    _xValid = snapshotReader.float64().readArray("/xValid");
    _lossTrain = snapshotReader.float64().readArray("/lossTrain");
    _lossValid = snapshotReader.float64().readArray("/lossValid");
    _intersection = snapshotReader.float64().readMatrix("/intersection");
    _union = snapshotReader.float64().readMatrix("/union");
    _iou = snapshotReader.float64().readMatrix("/IoU");
    _nTPDetection = snapshotReader.float64().readMatrix("/nTPDetection");
    _nPredDetection =
        snapshotReader.float64().readMatrix("/nPredDetection");
    _nObjDetection = snapshotReader.float64().readMatrix("/nObjDetection");
    _f1Detection = snapshotReader.float64().readMatrix("/f1Detection");
    _nTPSegmentation =
        snapshotReader.float64().readMatrix("/nTPSegmentation");
    _nPredSegmentation =
        snapshotReader.float64().readMatrix("/nPredSegmentation");
    _nObjSegmentation =
        snapshotReader.float64().readMatrix("/nObjSegmentation");
    _f1Segmentation = snapshotReader.float64().readMatrix("/f1Segmentation");

    if (snapshotReader.object().isDataSet("/localFiles")) {
      String[] localFiles = snapshotReader.string().readArray("/localFiles");
      for (String f : localFiles) _createdLocalFiles.add(new File(f));
    }

    if (snapshotReader.object().isDataSet("/remoteFolders")) {
      String[] remoteFolders =
          snapshotReader.string().readArray("/remoteFolders");
      for (String f : remoteFolders) _createdRemoteFolders.add(f);
    }

    if (snapshotReader.object().isDataSet("/remoteFiles")) {
      String[] remoteFiles = snapshotReader.string().readArray("/remoteFiles");
      for (String f : remoteFiles) _createdRemoteFiles.add(f);
    }

    progressMonitor().push(
        "Finetuning", 0.0f,
        _downloadWeightsCheckBox.isSelected() ? 0.95f : 1.0f);
    progressMonitor().init(_xTrain.length - 1);
    progressMonitor().count(currentIter);

    // Remote connection
    try {
      hostConfiguration().connectFromSnapshot(snapshotReader);
      JobManager.instance().addJob(this);
      _isResuming = true;
      start();
    }
    catch (JSchException e) {
      showError("SSH connection for " + hostConfiguration().username() + "@" +
                hostConfiguration().hostname() + ":" +
                hostConfiguration().port() + " failed", e);
      abort();
    }
    catch (InterruptedException e) {
      abort();
    }
  }

  protected final void runFinetuning()
      throws JSchException, IOException, InterruptedException {

    progressMonitor().count("Initializing U-Net", 0);

    // Prepare caffe call
    String commandLineString = "";
    for (String cmdItem : _cmd) commandLineString += cmdItem + " ";
    IJ.log(commandLineString);

    _nIter = _xTrain.length - 1;
    int nValidations = _xValid.length - 1;

    Channel channel = null;
    Process p = null;
    int pid = -1;
    BufferedReader stdOutput = null;
    BufferedReader stdError = null;
    if (sshSession() == null)
    {
      ProcessBuilder pb = new ProcessBuilder(_cmd);
      p = pb.start();
      pid = Tools.getPID(p);
      stdOutput = new BufferedReader(new InputStreamReader(p.getInputStream()));
      stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
    }
    else {
      channel = sshSession().openChannel("exec");
      ((ChannelExec)channel).setCommand("echo $$ && exec " + commandLineString);
      stdOutput =
          new BufferedReader(new InputStreamReader(channel.getInputStream()));
      stdError =
          new BufferedReader(
              new InputStreamReader(((ChannelExec)channel).getErrStream()));
      channel.connect();
    }

    int exitStatus = -1;
    String line = null;
    String errorMsg = new String();

    boolean firstLine = true;
    try {
      while (true) {

        // Check for ready() to avoid thread blocking, then read
        // all available lines from the buffer and update progress
        while (stdOutput.ready()) {
          line = stdOutput.readLine();
          if (firstLine) {
            pid = Integer.parseInt(line);
            firstLine = false;
          }
          parseCaffeOutputString(line);
        }
        // Also read error stream to avoid stream overflow that leads
        // to process stalling
        while (stdError.ready()) {
          line = stdError.readLine();
          errorMsg += line + "\n";
          parseCaffeOutputString(line);
        }

        if (sshSession() != null) {
          if (channel.isClosed()) {
            if(stdOutput.ready() || stdError.ready()) continue;
            exitStatus = channel.getExitStatus();
            break;
          }
        }
        else {
          try {
            exitStatus = p.exitValue();
            break;
          }
          catch (IllegalThreadStateException e) {}
        }
        if (interrupted()) throw new InterruptedException();
        Thread.sleep(100);
      }
      if (sshSession() != null) channel.disconnect();
    }
    catch (InterruptedException e) {
      String snapshot = null;
      if (pid != -1) {
        if (sshSession() != null)
            Tools.execute(
                "kill -2 " + pid, sshSession(), progressMonitor());
        else {
          Vector<String> cmd = new Vector<String>();
          cmd.add("kill");
          cmd.add("-2");
          cmd.add(new Integer(pid).toString());
          Tools.execute(cmd, progressMonitor());
        }
        try {
          while (true) {
            while (stdOutput.ready()) {
              String l = stdOutput.readLine();
              String l2 = l.replaceFirst("^.* Snapshotting to HDF5 file ", "");
              if (!l.equals(l2)) snapshot = l2;
            }
            while (stdError.ready()) {
              String l = stdError.readLine();
              String l2 = l.replaceFirst("^.* Snapshotting to HDF5 file ", "");
              if (!l.equals(l2)) snapshot = l2;
            }
            if (sshSession() != null) {
              if (channel.isClosed()) {
                if(stdOutput.ready() || stdError.ready()) continue;
                exitStatus = channel.getExitStatus();
                break;
              }
            }
            else {
              try {
                exitStatus = p.exitValue();
                break;
              }
              catch (IllegalThreadStateException eInner) {}
            }
            Thread.sleep(100);
          }
        }
        catch (InterruptedException eInner) {}
      }
      if (sshSession() != null) channel.disconnect();
      else p.destroy();

      if (snapshot == null) throw e;

      _nIter = Integer.parseInt(
          snapshot.replaceFirst("^.*_iter_", "").replaceFirst(
              ".caffemodel.h5$", ""));
      IJ.log("Snapshot at iteration " + _nIter + " written to " +
             snapshot);

      _solverstate = snapshot.replaceFirst("caffemodel.h5$", "solverstate.h5");

      if (sshSession() != null) _createdRemoteFiles.add(_solverstate);
      else _createdLocalFiles.add(new File(_solverstate));

      throw e;
    }

    if (exitStatus != 0) {
      IJ.log(errorMsg);
      throw new IOException(
          "Error during finetuning: exit status " + exitStatus + "\n" +
          Tools.getCaffeErrorString(errorMsg) + "\n" +
          "See log for further details");
    }

    if (sshSession() != null)
    {
      SftpFileIO sftp = new SftpFileIO(sshSession(), progressMonitor());

      if (_downloadWeightsCheckBox.isSelected()) {
        String[] outweightsComponents =
            _outweightsTextField.getText().split("/");
        File outfile = new File(
            ((_finetunedModel.file.getParentFile() != null) ?
             (_finetunedModel.file.getParent() + File.separator) : "") +
            outweightsComponents[outweightsComponents.length - 1]);
        try {
          progressMonitor().pop();
          progressMonitor().push("Downloading weights", 0.95f, 1.0f);
          // Download weights file
          sftp.get(processFolder() + id() + "-snapshot_iter_" + _nIter +
                   ".caffemodel.h5", outfile);
        }
        catch (SftpException e) {
          showError("Could not download weights " + processFolder() + id() +
                    "-snapshot_iter_" + _nIter + ".caffemodel.h5", e);
        }
      }

      try {
        sftp.renameFile(
            processFolder() + id() + "-snapshot_iter_" + _nIter +
            ".caffemodel.h5", _finetunedModel.weightFile);
      }
      catch (SftpException e) {
        IJ.log("Could not rename weightsfile to " +
               _finetunedModel.weightFile + "\n" +
               "The trained model can be found at " + processFolder() +
               id() + "-snapshot_iter_" + _nIter + ".caffemodel.h5\n" + e);
        _finetunedModel.weightFile = processFolder() +
            id() + "-snapshot_iter_" + _nIter + ".caffemodel.h5";
      }
    }
    else {
      File outfile = new File(_finetunedModel.weightFile);
      File infile = new File(
          processFolder() + id() + "-snapshot_iter_" + _nIter +
          ".caffemodel.h5");
      if (!infile.renameTo(outfile)) {
        IJ.log("Could not rename weightsfile to " +
               outfile.getAbsolutePath() + "\n" +
               "The trained model can be found at " +
               infile.getAbsolutePath());
        _finetunedModel.weightFile = infile.getAbsolutePath();
      }
    }
    progressMonitor().pop();
  }

  public void saveSnapshot(File snapshotFile) {

    try {
      _finetunedModel.saveSnapshot(snapshotFile);

      IHDF5Writer snapshotWriter =
          HDF5Factory.configure(snapshotFile.getAbsolutePath()).syncMode(
              IHDF5WriterConfigurator.SyncMode.SYNC_BLOCK)
          .useSimpleDataSpaceForAttributes().writer();
      String[] cmd = new String[_cmd.size()];
      boolean snapshotParameterSet = false;
      for (int i = 0; i < _cmd.size(); ++i) {
        if (_cmd.get(i).equals("-weights")) {
          cmd[i++] = "-snapshot";
          cmd[i] = _solverstate;
          snapshotParameterSet = true;
        }
        else cmd[i] = _cmd.get(i);
      }
      if (!snapshotParameterSet) {
        String[] cmdNew = new String[cmd.length + 2];
        for (int i = 0; i < cmd.length; ++i) cmdNew[i] = cmd[i];
        cmdNew[cmd.length] = "-snapshot";
        cmdNew[cmd.length + 1] = _solverstate;
        cmd = cmdNew;
      }
      snapshotWriter.string().setAttr("/", "id", id());
      snapshotWriter.string().writeArrayVL("/commandLine", cmd);
      snapshotWriter.string().setAttr("/", "processFolder", processFolder());
      snapshotWriter.string().setAttr(
          "/", "weightsFileName", weightsFileName());
      snapshotWriter.string().setAttr(
          "/", "outputWeightsFile", _outweightsTextField.getText());
      snapshotWriter.int8().setAttr(
          "/", "downloadWeights",
          _downloadWeightsCheckBox.isSelected() ? (byte)1 : (byte)0);

      // Save current finetuning state
      snapshotWriter.int32().setAttr("/", "currentIteration", _nIter);
      snapshotWriter.float64().writeArray("/xTrain", _xTrain);
      snapshotWriter.float64().writeArray("/xValid", _xValid);
      snapshotWriter.float64().writeArray("/lossTrain", _lossTrain);
      snapshotWriter.float64().writeArray("/lossValid", _lossValid);
      snapshotWriter.float64().writeMatrix("/intersection", _intersection);
      snapshotWriter.float64().writeMatrix("/union", _union);
      snapshotWriter.float64().writeMatrix("/IoU", _iou);
      snapshotWriter.float64().writeMatrix("/nTPDetection", _nTPDetection);
      snapshotWriter.float64().writeMatrix("/nPredDetection", _nPredDetection);
      snapshotWriter.float64().writeMatrix("/nObjDetection", _nObjDetection);
      snapshotWriter.float64().writeMatrix("/f1Detection", _f1Detection);
      snapshotWriter.float64().writeMatrix(
          "/nTPSegmentation", _nTPSegmentation);
      snapshotWriter.float64().writeMatrix(
          "/nPredSegmentation", _nPredSegmentation);
      snapshotWriter.float64().writeMatrix(
          "/nObjSegmentation", _nObjSegmentation);
      snapshotWriter.float64().writeMatrix("/f1Segmentation", _f1Segmentation);

      hostConfiguration().save(snapshotWriter);

      try {
        if (_createdLocalFiles.size() > 0 && sshSession() == null) {
          String[] localFiles = new String[_createdLocalFiles.size()];
          for (int i = 0; i < localFiles.length; ++i)
              localFiles[i] = _createdLocalFiles.get(i).getAbsolutePath();
          snapshotWriter.string().writeArrayVL("/localFiles", localFiles);
        }
      }
      catch (JSchException|InterruptedException e) {}

      if (_createdRemoteFolders.size() > 0) {
        String[] remoteFolders = new String[_createdRemoteFolders.size()];
        for (int i = 0; i < remoteFolders.length; ++i)
            remoteFolders[i] = _createdRemoteFolders.get(i);
        snapshotWriter.string().writeArrayVL("/remoteFolders", remoteFolders);
      }

      if (_createdRemoteFiles.size() > 0) {
        String[] remoteFiles = new String[_createdRemoteFiles.size()];
        for (int i = 0; i < remoteFiles.length; ++i)
            remoteFiles[i] = _createdRemoteFiles.get(i);
        snapshotWriter.string().writeArrayVL("/remoteFiles", remoteFiles);
      }

      snapshotWriter.close();
    }
    catch (IOException e) {
      showError(
          "Could not save snapshot to " + snapshotFile.getAbsolutePath(), e);
    }
  }

  @Override
  public void run(String arg) {
    JobManager.instance().addJob(this);
    start();
  }

  @Override
  public void run() {

    try
    {
      if (!_isResuming) {
        boolean trainImageFound = false;
        if (WindowManager.getIDList() != null) {
          for (int id: WindowManager.getIDList()) {
            if (WindowManager.getImage(id).getOverlay() != null) {
              trainImageFound = true;
              break;
            }
          }
        }
        if (!trainImageFound) {
          IJ.error(
              "U-Net Finetuning", "No image with annotations found for " +
              "finetuning.\nThis Plugin requires at least one image with " +
              "overlay containing annotations.");
          abort();
          return;
        }
        progressMonitor().count("Fintuning", 0);

        prepareParametersDialog();
        if (isInteractive() && !getParameters()) return;

        int nTrainImages = _trainFileList.getModel().getSize();
        int nValidImages = _validFileList.getModel().getSize();
        int nImages = nTrainImages + nValidImages;

        String[] trainBlobFileNames = new String[nTrainImages];
        Vector<String> validBlobFileNames = new Vector<String>();

        // Get class label information from annotations
        progressMonitor().push("Searching class labels", 0.0f, 0.01f);

        boolean labelsAreClasses = _labelsAreClassesCheckBox.isSelected();

        int maxClassLabel = 0;
        Vector<String> classNames = new Vector<String>();
        classNames.add("Background");
        for (Object o : ((DefaultListModel<ImagePlus>)
                         _trainFileList.getModel()).toArray()) {
          ImagePlus imp = (ImagePlus)o;
          for (Roi roi: imp.getOverlay().toArray()) {
            if (roi instanceof ImageRoi) {
              ImageProcessor ip = ((ImageRoi)roi).getProcessor();
              if (ip instanceof ByteProcessor) {
                byte[] data = (byte[])ip.getPixels();
                for (int i = 0; i < data.length; ++i)
                    if (data[i] > maxClassLabel) maxClassLabel = data[i];
              }
              else if (ip instanceof ShortProcessor)
              {
                short[] data = (short[])ip.getPixels();
                for (int i = 0; i < data.length; ++i)
                    if (data[i] > maxClassLabel) maxClassLabel = data[i];
              }
            }
            else {
              TrainingSample.RoiLabel roiLabel =
                  TrainingSample.parseRoiName(roi.getName());
              if (roiLabel.isIgnore()) continue;
              if (classNames.contains(roiLabel.className)) continue;
              classNames.add(roiLabel.className);
              if (labelsAreClasses)
                  IJ.log("  Adding class " + roiLabel.className);
            }
          }
        }

        int oldMax = maxClassLabel;
        for (Object o : ((DefaultListModel<ImagePlus>)
                         _validFileList.getModel()).toArray()) {
          ImagePlus imp = (ImagePlus)o;
          for (Roi roi: imp.getOverlay().toArray()) {
            if (roi instanceof ImageRoi) {
              ImageProcessor ip = ((ImageRoi)roi).getProcessor();
              if (ip instanceof ByteProcessor) {
                byte[] data = (byte[])ip.getPixels();
                for (int i = 0; i < data.length; ++i) {
                  if (data[i] > maxClassLabel) {
                    maxClassLabel = data[i];
                    if (labelsAreClasses)
                        showMessage(
                            "WARNING: Training set does not contain " +
                            "instances with label " + maxClassLabel +
                            ". This class will not be learnt!");
                  }
                }
              }
              else if (ip instanceof ShortProcessor)
              {
                short[] data = (short[])ip.getPixels();
                for (int i = 0; i < data.length; ++i) {
                  if (data[i] > maxClassLabel) {
                    maxClassLabel = data[i];
                    if (labelsAreClasses)
                        showMessage(
                            "WARNING: Training set does not contain " +
                            "instances with label " + maxClassLabel +
                            ". This class will not be learnt!");
                  }
                }
              }
            }
            else {
              TrainingSample.RoiLabel roiLabel =
                  TrainingSample.parseRoiName(roi.getName());
              if (roiLabel.isIgnore()) continue;
              if (classNames.contains(roiLabel.className)) continue;
              classNames.add(roiLabel.className);
              if (labelsAreClasses) {
                IJ.log("  Adding class " + roiLabel.className);
                showMessage(
                    "WARNING: Training set does not contain instances of" +
                    "class " + roiLabel.className +
                    ". This class will not be learnt!");
              }
            }
          }
        }

        boolean imagesContainMaskAnnotations = (maxClassLabel > 0);
        boolean imagesContainRoiAnnotations = (classNames.size() > 1);

        // If we have no valid annotations of any type, give up
        if (!imagesContainMaskAnnotations && !imagesContainRoiAnnotations) {
          showMessage(
              "Training images do not contain valid annotations.\n" +
              "Please make sure that your ROI annotations are named " +
              "<classname>[#<instance>] or \n" +
              "that segmentation masks are embedded as Overlay");
          throw new InterruptedException();
        }

        // If we have a mix of annotation types, give up
        if (imagesContainMaskAnnotations && imagesContainRoiAnnotations) {
          showMessage(
              "Training images contain a mix of ROI and mask annotations.\n" +
              "This is currently not supported, please convert either all " +
              "ROIs to masks or vice-versa.");
          throw new InterruptedException();
        }

        if (labelsAreClasses) {
          if (imagesContainRoiAnnotations) {
            _finetunedModel.classNames = new String[classNames.size()];
            for (int i = 0; i < classNames.size(); ++i)
                _finetunedModel.classNames[i] = classNames.get(i);
          }
          else {
            _finetunedModel.classNames = new String[maxClassLabel + 1];
            _finetunedModel.classNames[0] = "Background";
            for (int i = 2; i <= maxClassLabel; ++i)
                _finetunedModel.classNames[i] = "Class " + (i - 1);
          }
        }
        else _finetunedModel.classNames = new String[] {
                "Background", "Foreground" };

        progressMonitor().pop(); // Searching class labels (0.0 - 0.01)
        progressMonitor().push("Data conversion", 0.01f, 0.1f);

        // Convert and upload caffe blobs
        File outfile = null;
        if (sshSession() != null) {
          outfile = File.createTempFile(id(), ".h5");
          outfile.delete();
        }

        // Process train files
        progressMonitor().push(
            "Converting train files", 0.0f,
            (float)nTrainImages / (float)(nTrainImages + nValidImages));

        for (int i = 0; i < nTrainImages; ++i) {
          ImagePlus imp =
              ((DefaultListModel<ImagePlus>)_trainFileList.getModel()).get(i);

          progressMonitor().push(
              "Converting " + imp.getTitle(),
              (float)i / (float)nTrainImages, (float)(i + 1) /
              (float)nTrainImages);

          trainBlobFileNames[i] =
              processFolder() + id() + "_train_" + i + ".h5";
          if (sshSession() == null) {
            outfile = new File(trainBlobFileNames[i]);
            progressMonitor().push("Converting " + imp.getTitle(), 0.0f, 1.0f);
          }
          else progressMonitor().push(
              "Converting " + imp.getTitle(), 0.0f, 0.5f);

          TrainingSample t = new TrainingSample(imp);
          t.createDataBlob(_finetunedModel, progressMonitor());
          t.createLabelsAndWeightsBlobs(
              _finetunedModel, labelsAreClasses, progressMonitor());
          Vector<File> createdFiles =
              t.saveBlobs(outfile, _finetunedModel, progressMonitor());

          progressMonitor().pop(); // Converting image (real)

          if (interrupted()) throw new InterruptedException();
          if (sshSession() != null) {
            progressMonitor().push(
                "Uploading " + trainBlobFileNames[i], 0.5f, 1.0f);
            _createdRemoteFolders.addAll(
                new SftpFileIO(sshSession(), progressMonitor()).put(
                    outfile, trainBlobFileNames[i]));
            _createdRemoteFiles.add(trainBlobFileNames[i]);
            for (int c = createdFiles.size() - 1; c >= 0; --c)
                createdFiles.get(c).delete();
            progressMonitor().pop(); // Uploading image
            if (interrupted()) throw new InterruptedException();
          }
          else _createdLocalFiles.addAll(createdFiles);

          progressMonitor().pop(); // Converting image
        }

        progressMonitor().pop(); // Converting train files
        progressMonitor().push(
            "Converting validation files",
            (float)nTrainImages / (float)(nTrainImages + nValidImages), 1.0f);

        for (int i = 0; i < nValidImages; i++) {
          ImagePlus imp =
              ((DefaultListModel<ImagePlus>)_validFileList.getModel()).get(i);

          progressMonitor().push(
              "Converting " + imp.getTitle(),
              (float)i / (float)nValidImages, (float)(i + 1) /
              (float)nValidImages);

          String fileNameStub = (sshSession() == null) ?
              processFolder() + id() + "_valid_" + i : null;
          if (sshSession() != null)
              progressMonitor().push(
                  "Converting " + imp.getTitle(), 0.0f, 0.5f);
          else progressMonitor().push(
              "Converting " + imp.getTitle(), 0.0f, 1.0f);

          TrainingSample t = new TrainingSample(imp);
          t.createDataBlob(_finetunedModel, progressMonitor());
          t.createLabelsAndWeightsBlobs(
              _finetunedModel, labelsAreClasses, progressMonitor());
          Vector<File> files = t.saveTiledBlobs(
              fileNameStub, _finetunedModel, labelsAreClasses,
              progressMonitor());
          Vector<File> generatedFiles = new Vector<File>();
          for (File f : files) if (f.isFile()) generatedFiles.add(f);

          progressMonitor().pop(); // Converting image (real)

          if (sshSession() == null)
          {
            for (File f : generatedFiles)
                validBlobFileNames.add(f.getAbsolutePath());
            _createdLocalFiles.addAll(files);
          }
          else {

            progressMonitor().push(
                "Uploading " + imp.getTitle(), 0.5f, 1.0f);

            for (int j = 0; j < generatedFiles.size(); j++) {
              progressMonitor().push(
                  "Uploading " + imp.getTitle() + " - tile " + j,
                  (float)j / (float)generatedFiles.size(),
                  (float)(j + 1) / (float)generatedFiles.size());
              String outFileName =
                  processFolder() + id() + "_valid_" + i + "_" + j + ".h5";
              _createdRemoteFolders.addAll(
                  new SftpFileIO(sshSession(), progressMonitor()).put(
                      generatedFiles.get(j), outFileName));
              _createdRemoteFiles.add(outFileName);
              validBlobFileNames.add(outFileName);
              progressMonitor().pop(); // Uploading image tile

              if (interrupted()) throw new InterruptedException();
            }
            for (int c = files.size() - 1; c >= 0; --c) files.get(c).delete();

            progressMonitor().pop(); // Uploading image

          }
          progressMonitor().pop(); // Converting image
        }

        progressMonitor().pop(); // Converting validation files
        progressMonitor().pop(); // Data conversion (0.01 - 0.1)
        progressMonitor().push("Creating prototxt files", 0.1f, 0.11f);

        prepareFinetuning(trainBlobFileNames, validBlobFileNames);

        progressMonitor().pop(); // Prototxt generation
        progressMonitor().push("U-Net finetuning", 0.11f, 1.0f);

        startFinetuning();
        if (interrupted()) throw new InterruptedException();
      }
      else runFinetuning();

      setReady(true);
    }
    catch (TrainingSampleException e) {
      showError("Invalid Training Sample", e);
      abort();
    }
    catch (IOException e) {
      IJ.error(id(), "Input/Output error:\n" + e);
      abort();
    }
    catch (JSchException e) {
      IJ.error(id(), "SSH connection failed:\n" + e);
      abort();
    }
    catch (SftpException e) {
      IJ.error(id(), "SFTP file transfer failed:\n" + e);
      abort();
    }
    catch (InterruptedException e) {
      abort();
    }
    catch (BlobException e) {
      IJ.error(id(), "Blob conversion failed:\n" + e);
      abort();
    }
  }

}
