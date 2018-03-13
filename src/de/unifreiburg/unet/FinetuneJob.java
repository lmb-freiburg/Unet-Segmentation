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
import ij.ImagePlus;
import ij.plugin.PlugIn;
import ij.gui.Plot;
import ij.gui.Roi;

import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.Color;
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

public class FinetuneJob extends Job implements PlugIn {

  private final ImagePlusListView _trainFileList = new ImagePlusListView();
  private final ImagePlusListView _validFileList = new ImagePlusListView();
  private final JPanel _trainImagesPanel = new JPanel(new BorderLayout());
  private final JPanel _validImagesPanel = new JPanel(new BorderLayout());
  private final JSplitPane _trainValidPane = new JSplitPane(
      JSplitPane.HORIZONTAL_SPLIT, _trainImagesPanel, _validImagesPanel);
  private final JPanel _elSizePanel = new JPanel(new BorderLayout());
  private final JButton _fromImageButton = new JButton("from Image");
  private final JFormattedTextField _learningRateTextField =
      new JFormattedTextField(
          new NumberFormatter(new DecimalFormat("0.###E0")));
  private final JTextField _outModeldefTextField = new JTextField(
      Prefs.get("unet_finetuning.outModeldef", "finetuned-modeldef.h5"));
  private final JButton _outModeldefChooseButton =
      (UIManager.get("FileView.directoryIcon") instanceof Icon) ?
      new JButton((Icon)UIManager.get("FileView.directoryIcon")) :
      new JButton("...");
  private final JTextField _outweightsTextField = new JTextField(
      Prefs.get("unet_finetuning.outweights", "finetuned.caffemodel.h5"));
  private final JButton _outweightsChooseButton =
      hostConfiguration().finetunedFileChooseButton();
  private final JSpinner _iterationsSpinner =
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet_finetuning.iterations", 1000),
              1, (int)Integer.MAX_VALUE, 1));
  private final JSpinner _validationStepSpinner =
      new JSpinner(
          new SpinnerNumberModel(
              (int)Prefs.get("unet_finetuning.validation_step", 1),
              1, (int)Integer.MAX_VALUE, 1));
  private final JCheckBox _treatRoiNamesAsClassesCheckBox =
      new JCheckBox(
          "ROI names are classes",
          Prefs.get("unet_finetuning.roiNamesAreClasses", false));
  private final JTextField _modelNameTextField = new JTextField(
      Prefs.get("unet_finetuning.modelName", "finetuned model"));

  private boolean _trainFromScratch = false;
  private int _currentTestIteration = 0;

  private ModelDefinition _finetunedModel = null;

  public FinetuneJob() {
    super();
  }

  public FinetuneJob(JobTableModel model) {
    super(model);
  }

  @Override
  public ModelDefinition model() {
    if (_finetunedModel == null && originalModel() != null)
        _finetunedModel = originalModel().duplicate();
    return _finetunedModel;
  }

  @Override
  public void finish() {
    if (progressMonitor().finished()) return;
    if (sshSession() != null)
        IJ.showMessage(
            "Finetuning finished!\nThe finetuned model has been saved to " +
            processFolder() + "/" + _outweightsTextField.getText() +
            " on the remote host");
    else
        IJ.showMessage(
            "Finetuning finished!\nThe finetuned model has been saved to " +
            processFolder() + "/" + _outweightsTextField.getText());
    super.finish();
  }

  @Override
  protected void processModelSelectionChange() {
    _finetunedModel = null;
    _elSizePanel.removeAll();
    if (originalModel() != null) {
      _elSizePanel.add(originalModel().elementSizeUmPanel());
      _elSizePanel.setMinimumSize(
          originalModel().elementSizeUmPanel().getMinimumSize());
      _elSizePanel.setMaximumSize(
          new Dimension(
              Integer.MAX_VALUE,
              originalModel().elementSizeUmPanel().getPreferredSize().height));
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
        if (WindowManager.getImage(ids[i]).getOverlay() != null)
            ((DefaultListModel<ImagePlus>)_trainFileList.getModel()).addElement(
                WindowManager.getImage(ids[i]));

    JLabel trainImagesLabel = new JLabel("Train images");
    _trainImagesPanel.add(trainImagesLabel, BorderLayout.NORTH);
    JScrollPane trainScroller = new JScrollPane(_trainFileList);
    trainScroller.setMinimumSize(new Dimension(100, 50));
    _trainImagesPanel.add(trainScroller, BorderLayout.CENTER);

    JLabel validImagesLabel = new JLabel("Validation images");
    _validImagesPanel.add(validImagesLabel, BorderLayout.NORTH);
    JScrollPane validScroller = new JScrollPane(_validFileList);
    validScroller.setMinimumSize(new Dimension(100, 50));
    _validImagesPanel.add(validScroller, BorderLayout.CENTER);

    JLabel elSizeLabel = new JLabel("Element Size [µm]:");
    _fromImageButton.setToolTipText(
        "Use native image element size for finetuning");
    JLabel learningRateLabel = new JLabel("Learning rate:");
    _learningRateTextField.setValue(
        (Double)Prefs.get("unet_finetuning.base_learning_rate", 1e-4));
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

    _configPanel.add(_treatRoiNamesAsClassesCheckBox);
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

    _fromImageButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (originalModel() == null) return;
            if (_trainFileList.getModel().getSize() == 0) return;
            ImagePlus imp = _trainFileList.getModel().getElementAt(0);
            float[] elSize = new float[originalModel().nDims()];
            float factor = 1.0f;
            switch (imp.getCalibration().getUnit())
            {
            case "m":
            case "meter":
              factor = 1000000.0f;
            break;
            case "cm":
            case "centimeter":
              factor = 10000.0f;
            break;
            case "mm":
            case "millimeter":
              factor = 1000.0f;
            break;
            case "nm":
            case "nanometer":
              factor = 0.0001f;
            break;
            case "pm":
            case "pikometer":
              factor = 0.0000001f;
            break;
            }
            if (originalModel().nDims() == 2) {
              elSize[0] = (float)imp.getCalibration().pixelHeight * factor;
              elSize[1] = (float)imp.getCalibration().pixelWidth * factor;
            }
            else {
              elSize[0] = (float)imp.getCalibration().pixelDepth * factor;
              elSize[1] = (float)imp.getCalibration().pixelHeight * factor;
              elSize[2] = (float)imp.getCalibration().pixelWidth * factor;
            }
            originalModel().setElementSizeUm(elSize);
          }});

    _trainValidPane.setDividerLocation(0.5);

    super.finalizeDialog();
  }

  public boolean getParameters() throws InterruptedException {

    progressMonitor().count("Checking parameters", 0);

    if (WindowManager.getImageTitles().length == 0) {
      IJ.noImage();
      return false;
    }

    boolean dialogOK = false;
    while (!dialogOK) {

      dialogOK = true;
      _parametersDialog.setVisible(true);

      // Dialog was cancelled
      if (!_parametersDialog.isDisplayable())
          throw new InterruptedException("Dialog canceled");

      dialogOK = checkParameters();
      if (!dialogOK) continue;

      if (_trainFileList.getModel().getSize() == 0) {
        IJ.error("U-Net Finetuning",
                 "U-Net Finetuning requires at least one training image.");
        dialogOK = false;
        continue;
      }

      int nChannels = -1;
      for (Object obj : ((DefaultListModel<ImagePlus>)
                         _trainFileList.getModel()).toArray()) {
        ImagePlus imp = (ImagePlus)obj;
        int nc = (imp.getType() == ImagePlus.COLOR_256 ||
                  imp.getType() == ImagePlus.COLOR_RGB) ? 3 :
            imp.getNChannels();
        if (nChannels == -1) nChannels = nc;
        if (nc != nChannels) {
          IJ.error("U-Net Finetuning",
                   "U-Net Finetuning requires that all training and " +
                   "validation images have the same number of channels.");
          dialogOK = false;
          break;
        }
      }
      if (!dialogOK) continue;

      for (Object obj : ((DefaultListModel<ImagePlus>)
                         _validFileList.getModel()).toArray()) {
        ImagePlus imp = (ImagePlus)obj;
        int nc = (imp.getType() == ImagePlus.COLOR_256 ||
                  imp.getType() == ImagePlus.COLOR_RGB) ? 3 :
            imp.getNChannels();
        if (nc != nChannels) {
          IJ.error("U-Net Finetuning",
                   "U-Net Finetuning requires that all training and " +
                   "validation images have the same number of channels.");
          dialogOK = false;
          break;
        }
      }
      if (!dialogOK) continue;

      // Check whether caffe binary exists and is executable
      ProcessResult res = null;
      while (res == null)
      {
        if (sshSession() == null) {
          try {
            Vector<String> cmd = new Vector<String>();
            cmd.add(Prefs.get("unet_finetuning.caffeBinary", "caffe"));
            res = Tools.execute(cmd, this);
          }
          catch (IOException e) {
            res = new ProcessResult();
            res.exitStatus = 1;
          }
        }
        else {
          try {
            String cmd = Prefs.get("unet_finetuning.caffeBinary", "caffe");
            res = Tools.execute(cmd, sshSession(), this);
          }
          catch (JSchException e) {
            res.exitStatus = 1;
          }
          catch (IOException e) {
            res.exitStatus = 1;
          }
        }
        if (res.exitStatus != 0) {
          String caffePath = JOptionPane.showInputDialog(
              WindowManager.getActiveWindow(), "caffe was not found.\n" +
              "Please specify your caffe binary\n",
              Prefs.get("unet_finetuning.caffeBinary", "caffe"));
          if (caffePath == null)
              throw new InterruptedException("Dialog canceled");
          if (caffePath.equals(""))
              Prefs.set("unet_finetuning.caffeBinary", "caffe");
          else Prefs.set("unet_finetuning.caffeBinary", caffePath);
          res = null;
        }
      }

      // Check for correct model and weight combination
      if (sshSession() != null) {

        model().remoteAbsolutePath =
            processFolder() + "/" + id() + "-modeldef.h5";
        try {
          _createdRemoteFolders.addAll(
              new SftpFileIO(sshSession(), progressMonitor()).put(
                  originalModel().file, model().remoteAbsolutePath));
          _createdRemoteFiles.add(model().remoteAbsolutePath);
          Prefs.set("unet_segmentation.processfolder", processFolder());
        }
        catch (SftpException|JSchException e) {
          IJ.showMessage(
              "Model upload failed.\nDo you have sufficient " +
              "permissions to create the processing folder on " +
              "the remote host?");
          dialogOK = false;
          continue;
        }
        catch (IOException e) {
          IJ.showMessage("Model upload failed. Could not read model file.");
          dialogOK = false;
          continue;
        }

        try {
          String cmd =
              Prefs.get("unet_segmentation.caffeBinary", "caffe_unet") +
              " check_model_and_weights_h5 -model \"" +
              model().remoteAbsolutePath + "\" -weights \"" +
              weightsFileName() + "\" -n_channels " + nChannels + " " +
              caffeGPUParameter();
          res = Tools.execute(cmd, sshSession(), this);
          if (res.exitStatus != 0) {
            int selectedOption = JOptionPane.showConfirmDialog(
                WindowManager.getActiveWindow(),
                "No compatible pre-trained weights found at the given " +
                "location on the backend server.\n" +
                "Do you want to train from scratch?",
                "Start new Training?", JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            switch (selectedOption) {
            case JOptionPane.YES_OPTION:
              _trainFromScratch = true;
              res.exitStatus = 0;
              break;
            case JOptionPane.NO_OPTION: {
              res.exitStatus = 2;
              res.shortErrorString = "Weight file selection required";
              res.cerr = "Weight file " + weightsFileName() + " not found";
              break;
            }
            case JOptionPane.CANCEL_OPTION:
            case JOptionPane.CLOSED_OPTION:
              throw new InterruptedException("Aborted by user");
            }
          }
        }
        catch (JSchException e) {
          res.exitStatus = 1;
          res.shortErrorString = "SSH connection error";
          res.cerr = e.getMessage();
        }
        catch (IOException e) {
          res.exitStatus = 1;
          res.shortErrorString = "Input/Output error";
          res.cerr = e.getMessage();
        }
      }
      else {
        try {
          Vector<String> cmd = new Vector<String>();
          cmd.add(Prefs.get("unet_segmentation.caffeBinary", "caffe_unet"));
          cmd.add("check_model_and_weights_h5");
          cmd.add("-model");
          cmd.add(originalModel().file.getAbsolutePath());
          cmd.add("-weights");
          cmd.add(weightsFileName());
          cmd.add("-n_channels");
          cmd.add(new Integer(nChannels).toString());
          if (!caffeGPUParameter().equals("")) {
            cmd.add(caffeGPUParameter().split(" ")[0]);
            cmd.add(caffeGPUParameter().split(" ")[1]);
          }
          res = Tools.execute(cmd, this);
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
            case JOptionPane.NO_OPTION: {
              res.exitStatus = 2;
              res.shortErrorString = "Weight file selection required";
              res.cerr = "Weight file " + weightsFileName() + " not found";
              break;
            }
            case JOptionPane.CANCEL_OPTION:
            case JOptionPane.CLOSED_OPTION:
              throw new InterruptedException("Aborted by user");
            }
            if (res.exitStatus > 1) break;
          }
        }
        catch (IOException e) {
          res.exitStatus = 1;
          res.shortErrorString = "Input/Output error";
          res.cerr = e.getMessage();
        }
      }
      if (res.exitStatus != 0) {
        // User decided to change weight file, so don't bother him with
        // additional message boxes
        if (res.exitStatus == 2) continue;
        IJ.log(res.cerr);
        IJ.showMessage("Model/Weight check failed:\n" + res.shortErrorString);
        dialogOK = false;
        continue;
      }
    }

    Prefs.set("unet_finetuning.base_learning_rate",
              (Double)_learningRateTextField.getValue());
    Prefs.set("unet_finetuning.modelName", _modelNameTextField.getText());
    Prefs.set("unet_finetuning.outModeldef", _outModeldefTextField.getText());
    Prefs.set("unet_finetuning.outweights", _outweightsTextField.getText());
    Prefs.set("unet_finetuning.iterations",
              (Integer)_iterationsSpinner.getValue());
    Prefs.set("unet_finetuning.validation_step",
              (Integer)_validationStepSpinner.getValue());
    Prefs.set("unet_finetuning.roiNamesAreClasses",
              _treatRoiNamesAsClassesCheckBox.isSelected());

    _parametersDialog.dispose();

    if (jobTable() != null) jobTable().fireTableDataChanged();

    return true;
  }

  private void parseCaffeOutputString(
      String msg, ImagePlus lossImage, double[] xTrain, double[] yTrain,
      double[] xValid, double[] yValid) {
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
      boolean updatePlot = false;
      if (line.matches("^.*Iteration [0-9]+ .* loss = .*$")) {
        int iter = Integer.valueOf(line.split("Iteration ")[1].split(" ")[0]);
        double loss = Double.valueOf(line.split("loss = ")[1]);
        yTrain[iter] = loss;
        updatePlot = true;

        // Update progress
        progressMonitor().count(
            "Finetuning iteration " + iter + "/" + (xTrain.length - 1) +
            " loss = " + loss, 1);
      }
      if (line.matches("^.*Iteration [0-9]+, Testing net.*$")) {
        _currentTestIteration = Integer.valueOf(
            line.split("Iteration ")[1].split(",")[0]);
      }
      if (line.matches("^.*Test net output #0: loss_valid = .*$")) {
        double loss = Double.valueOf(
            line.split("loss_valid = ")[1].split(" ")[0]);
        yValid[_currentTestIteration /
               (Integer)_validationStepSpinner.getValue()] = loss;
        updatePlot = true;
      }

      if (updatePlot) {
        Plot plot = new Plot("Finetuning Evolution", "Iteration", "Loss");
        plot.setColor(Color.black);
        plot.addPoints(xTrain, yTrain, Plot.LINE);
        plot.setColor(Color.red);
        plot.addPoints(xValid, yValid, Plot.LINE);
        plot.setColor(Color.black);
        plot.addLegend("Training\nValidation");
        plot.setLimits(0.0, (double)(xTrain.length - 1), 0.0, Double.NaN);
        ImageProcessor plotProcessor = plot.getProcessor();
        lossImage.setProcessor(null, plotProcessor);
        lossImage.updateAndDraw();
      }
    }
  }

  public void runFinetuning()
      throws JSchException, IOException, InterruptedException {

    progressMonitor().count("Initializing U-Net", 0);

    // Prepare caffe call
    String gpuAttribute = new String();
    String gpuValue = new String();
    String selectedGPU = selectedGPUString();
    if (selectedGPU.contains("GPU ")) {
      gpuAttribute = "-gpu";
      gpuValue = selectedGPU.substring(selectedGPU.length() - 1);
    }
    else if (selectedGPU.contains("all")) {
      gpuAttribute = "-gpu";
      gpuValue = "all";
    }
    String weightsAttribute = "";
    String weightsValue = "";
    if (!_trainFromScratch) {
      weightsAttribute = "-weights";
      weightsValue = weightsFileName();
    }
    String commandString = Prefs.get("unet_finetuning.caffeBinary", "caffe");
    String commandLineString =
        commandString + " train -solver " +
        model().solverPrototxtAbsolutePath + " " +
        weightsAttribute + " " + weightsValue + " " + gpuAttribute + " " +
        gpuValue + " -sigint_effect stop";
    IJ.log(commandLineString);

    int nIter = (Integer)_iterationsSpinner.getValue();
    int nValidations = nIter / (Integer)_validationStepSpinner.getValue();

    progressMonitor().init(0, "", "", nIter);

    // Set up ImagePlus for plotting the loss curves
    ImagePlus plotImage = null;
    double[] xTrain = new double[nIter + 1];
    double[] yTrain = new double[nIter + 1];
    double[] xValid = new double[nValidations + 1];
    double[] yValid = new double[nValidations + 1];
    for (int i = 0; i <= nIter; i++) {
      xTrain[i] = i + 1;
      yTrain[i] = Double.NaN;
    }
    for (int i = 0; i <= nValidations ; i++) {
      xValid[i] = i * (Integer)_validationStepSpinner.getValue();
      yValid[i] = Double.NaN;
    }
    {
      Plot plot = new Plot("Finetuning " + id(), "Iteration", "Loss");
      plot.setLimits(0.0, (double)nIter, 0.0, Double.NaN);
      ImageProcessor plotProcessor = plot.getProcessor();
      plotImage = new ImagePlus("Finetuning " + id(), plotProcessor);
      plotImage.setProcessor(null, plotProcessor);
      plotImage.show();
    }

    Channel channel = null;
    Process p = null;
    BufferedReader stdOutput = null;
    BufferedReader stdError = null;
    if (sshSession() == null)
    {
      ProcessBuilder pb = null;
      if (gpuAttribute.equals("")) {
        if (weightsAttribute.equals(""))
            pb = new ProcessBuilder(
                commandString, "train",
                "-solver", model().solverPrototxtAbsolutePath);
        else
            pb = new ProcessBuilder(
                commandString, "train",
                "-solver", model().solverPrototxtAbsolutePath,
                weightsAttribute, weightsValue);
      }
      else {
        if (weightsAttribute.equals(""))
            pb = new ProcessBuilder(
                commandString, "train",
                "-solver", model().solverPrototxtAbsolutePath,
                gpuAttribute, gpuValue);
        else
            pb = new ProcessBuilder(
                commandString, "train",
                "-solver", model().solverPrototxtAbsolutePath,
                weightsAttribute, weightsValue,
                gpuAttribute, gpuValue);
      }
      p = pb.start();
      stdOutput = new BufferedReader(new InputStreamReader(p.getInputStream()));
      stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
    }
    else {
      channel = sshSession().openChannel("exec");
      ((ChannelExec)channel).setCommand(commandLineString);
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

    try {
      while (true) {

        // Check for ready() to avoid thread blocking, then read
        // all available lines from the buffer and update progress
        while (stdOutput.ready()) {
          line = stdOutput.readLine();
          parseCaffeOutputString(
              line, plotImage, xTrain, yTrain, xValid, yValid);
        }
        // Also read error stream to avoid stream overflow that leads
        // to process stalling
        while (stdError.ready()) {
          line = stdError.readLine();
          errorMsg += line + "\n";
          parseCaffeOutputString(
              line, plotImage, xTrain, yTrain, xValid, yValid);
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
      if (sshSession() != null) channel.disconnect();
      else p.destroy();
      throw e;
    }

    if (exitStatus != 0) {
      IJ.log(errorMsg);
      throw new IOException(
          "Error during finetuning: exit status " + exitStatus +
          "\nSee log for further details");
    }

    if (sshSession() != null)
    {

      SftpFileIO sftp = new SftpFileIO(sshSession(), progressMonitor());

      try {
        // Rename output file name and remove solverstate file
        sftp.renameFile(
            processFolder() + "/" + id() + "-snapshot_iter_" + nIter +
            ".caffemodel.h5", processFolder() + "/" +
            _outweightsTextField.getText());
      }
      catch (SftpException e) {
        IJ.showMessage(
            "Could not rename weightsfile to " + processFolder() + "/" +
            _outweightsTextField.getText() + "\n" +
            "The trained model can be found at " + processFolder() + "/" +
            id() + "-snapshot_iter_" + nIter + ".caffemodel.h5");
      }
      try {
        sftp.removeFile(
            processFolder() + "/" + id() + "-snapshot_iter_" + nIter +
            ".solverstate.h5");
      }
      catch (SftpException e) {
        IJ.showMessage(
            "Could not delete solverstate " + processFolder() + "/" + id() +
            "-snapshot_iter_" + nIter + ".solverstate.h5");
      }
    }
    else {
      // Rename output file and remove solverstate
      File outfile = new File(
          processFolder() + "/" + _outweightsTextField.getText());
      File infile = new File(
          processFolder() + "/" + id() + "-snapshot_iter_" + nIter +
          ".caffemodel.h5");
      File solverstatefile = new File(
          processFolder() + "/" + id() + "-snapshot_iter_" + nIter +
          ".solverstate.h5");
      if (!infile.renameTo(outfile))
          IJ.showMessage(
              "Could not rename weightsfile to " + outfile.getPath() + "\n" +
              "The trained model can be found at " + infile.getPath());
      if (!solverstatefile.delete())
          IJ.showMessage(
              "Could not delete solverstate " + solverstatefile.getPath());
    }
  }

  @Override
  public void run(String arg) {
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
      IJ.error("U-Net Finetuning", "No image with annotations found for " +
               "finetuning.\nThis Plugin requires at least one image with " +
               "overlay containing annotations.");
      return;
    }
    start();
  }

  @Override
  public void run() {
    try
    {
      prepareParametersDialog();
      if (isInteractive() && !getParameters()) return;

      int nTrainImages = _trainFileList.getModel().getSize();
      int nValidImages = _validFileList.getModel().getSize();
      int nImages = nTrainImages + nValidImages;
      int nClasses = 2;

      String[] trainBlobFileNames = new String[nTrainImages];
      Vector<String> validBlobFileNames = new Vector<String>();

      // Convert and upload caffe blobs
      File outfile = null;
      if (sshSession() != null) {
        outfile = File.createTempFile(id(), ".h5");
        outfile.delete();
      }

      // Get class label information from annotations
      progressMonitor().initNewTask(
          "Searching class labels", progressMonitor().taskProgressMax(), 0);
      Vector<String> classes = new Vector<String>();
      boolean roiNamesAreClasses =
          _treatRoiNamesAsClassesCheckBox.isSelected();
      classes.add("background");
      for (int i = 0; i < nTrainImages; i++) {
        ImagePlus imp = ((DefaultListModel<ImagePlus>)
                         _trainFileList.getModel()).get(i);
        for (Roi roi: imp.getOverlay().toArray()) {
          if (roi.getName() == null) roiNamesAreClasses = false;
          if (roi.getName() == null ||
              roi.getName().toLowerCase(Locale.ROOT).contains("ignore") ||
              classes.contains(roi.getName()))
              continue;
          classes.add(roi.getName());
          IJ.log("  Adding class " + roi.getName());
        }
      }
      for (int i = 0; i < nValidImages && roiNamesAreClasses; i++) {
        ImagePlus imp = ((DefaultListModel<ImagePlus>)
                         _validFileList.getModel()).get(i);
        for (Roi roi: imp.getOverlay().toArray()) {
          if (roi.getName() == null) roiNamesAreClasses = false;
          if (roi.getName() == null ||
              roi.getName().toLowerCase(Locale.ROOT).contains("ignore") ||
              classes.contains(roi.getName()))
              continue;
          classes.add(roi.getName());
          IJ.log("  Adding class " + roi.getName());
          IJ.log("  WARNING: Training set does not contain instances of " +
                 "class " + roi.getName() + " switching to instance " +
                 "segmentation mode");
          roiNamesAreClasses = false;
        }
      }

      nClasses = roiNamesAreClasses ? classes.size() : 2;

      // Process train files
      for (int i = 0; i < nTrainImages; i++) {
        ImagePlus imp =
            ((DefaultListModel<ImagePlus>)_trainFileList.getModel()).get(i);
        progressMonitor().initNewTask(
            "Converting " + imp.getTitle(),
            0.05f * ((float)i + ((sshSession() == null) ? 1.0f : 0.5f)) /
            (float)nImages, 0);
        trainBlobFileNames[i] =
            processFolder() + "/" + id() + "_train_" + i + ".h5";
        if (sshSession() == null) outfile = new File(trainBlobFileNames[i]);
        Tools.saveHDF5Blob(
            imp, roiNamesAreClasses ? classes : null, outfile, this, true,
            false);
        if (interrupted()) throw new InterruptedException();
        if (sshSession() != null) {
          progressMonitor().initNewTask(
              "Uploading " + trainBlobFileNames[i],
              0.05f * (float)(i + 1) / (float)nImages, 0);
          _createdRemoteFolders.addAll(
              new SftpFileIO(sshSession(), progressMonitor()).put(
                  outfile, trainBlobFileNames[i]));
          _createdRemoteFiles.add(trainBlobFileNames[i]);
          outfile.delete();
          if (interrupted()) throw new InterruptedException();
        }
      }

      // Process validation files
      for (int i = 0; i < nValidImages; i++) {
        ImagePlus imp =
            ((DefaultListModel<ImagePlus>)_validFileList.getModel()).get(i);
        progressMonitor().initNewTask(
            "Converting " + imp.getTitle(),
            0.05f + 0.05f *
            ((float)i + ((sshSession() == null) ? 1.0f : 0.5f)) /
            (float)nImages, 1);
        String fileNameStub = (sshSession() == null) ?
            processFolder() + "/" + id() + "_valid_" + i : null;
        File[] generatedFiles = Tools.saveHDF5TiledBlob(
            imp, roiNamesAreClasses ? classes : null, fileNameStub, this);

        if (sshSession() == null)
            for (File f : generatedFiles)
                validBlobFileNames.add(f.getAbsolutePath());
        else {
          for (int j = 0; j < generatedFiles.length; j++) {
            progressMonitor().initNewTask(
                "Uploading " + generatedFiles[j],
                0.05f + 0.05f * (float)
                ((i + 0.5f * (1 + (float)(j + 1) / generatedFiles.length))) /
                (float)nImages, 1);
            String outFileName =
                processFolder() + "/" + id() + "_valid_" + i + "_" + j +
                ".h5";
            _createdRemoteFolders.addAll(
                new SftpFileIO(sshSession(), progressMonitor()).put(
                    generatedFiles[j], outFileName));
            _createdRemoteFiles.add(outFileName);
            validBlobFileNames.add(outFileName);
            if (interrupted()) throw new InterruptedException();
          }
        }
      }

      // ---
      // Create train and valid file list files
      String trainFileListAbsolutePath =
          processFolder() + "/" + id() + "-trainfilelist.txt";
      progressMonitor().initNewTask(
          "Create train and valid file lists",
          progressMonitor().taskProgressMax(), 0);
      outfile = (sshSession() != null) ?
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
      else outfile.deleteOnExit();

      String validFileListAbsolutePath =
          processFolder() + "/" + id() + "-validfilelist.txt";
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
        else outfile.deleteOnExit();
      }

      // ---
      // Create prototxt files

      // model.prototxt
      progressMonitor().initNewTask(
          "Create model prototxt", progressMonitor().taskProgressMax(), 0);
      Caffe.NetParameter.Builder nb = Caffe.NetParameter.newBuilder();
      TextFormat.getParser().merge(model().modelPrototxt, nb);

      boolean inputShapeSet = false;
      for (Caffe.LayerParameter.Builder lb : nb.getLayerBuilderList()) {
        if (lb.getType().equals("HDF5Data")) {
          lb.getHdf5DataParamBuilder().setSource(trainFileListAbsolutePath);
        }

        if (lb.getType().equals("CreateDeformation")) {
          if (model().nDims() == 3) {
            lb.getCreateDeformationParamBuilder()
                .setNz(model().getTileShape()[0])
                .setNy(model().getTileShape()[1])
                .setNx(model().getTileShape()[2]);
          }
          else {
            lb.getCreateDeformationParamBuilder()
                .setNy(model().getTileShape()[0])
                .setNx(model().getTileShape()[1]);
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
        abort();
        return;
      }

      // Adapt number of output channels if number of classes differs from
      // number of classes in model. The caffe backend must silently
      // resize the weight blob for the last layer accordingly. If number
      // of classes is decreased from n to k, all weights for classes k+1
      // to n are just ignored, if the number of classes increases new
      // weights will be initialized using the filler for the layer.

      // Get number of classes in model
      for (Caffe.LayerParameter.Builder lb : nb.getLayerBuilderList()) {
        for (int i = 0; i < lb.getTopCount(); ++i) {
          if (!lb.getTop(i).equals("score")) continue;
          if (!lb.hasConvolutionParam()) {
            IJ.error(
                "U-Net Finetuning",
                "The selected model cannot be finetuned using this " +
                "Plugin.\n" +
                "Scores must be generated with a Convolution layer.");
            abort();
            return;
          }
          int nClassesModel = lb.getConvolutionParam().getNumOutput();
          if (nClassesModel != nClasses)
              lb.getConvolutionParamBuilder().setNumOutput(nClasses);
        }
      }

      // Save model definition file before adding validation structures
      model().file = new File(_outModeldefTextField.getText());
      model().id = originalModel().id + "-" + id();
      model().name = _modelNameTextField.getText();
      model().modelPrototxt = TextFormat.printToString(nb);
      model().save();

      // Add test layers if a validation set is given
      if (validBlobFileNames.size() != 0) {
        nb.addLayer(
            0, Caffe.LayerParameter.newBuilder().setType("HDF5Data")
            .addTop(model().inputBlobName).addTop(
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
      }

      model().modelPrototxtAbsolutePath =
          processFolder() + "/" + id() + "-model.prototxt";
      model().modelPrototxt = TextFormat.printToString(nb);
      if (sshSession() != null) {
        File tmpFile = File.createTempFile(id(), "-model.prototxt");
        model().saveModelPrototxt(tmpFile);
        _createdRemoteFolders.addAll(
            new SftpFileIO(sshSession(), progressMonitor()).put(
                tmpFile, model().modelPrototxtAbsolutePath));
        _createdRemoteFiles.add(model().modelPrototxtAbsolutePath);
        tmpFile.delete();
      }
      else {
        File modelFile = new File(model().modelPrototxtAbsolutePath);
        modelFile.createNewFile();
        model().saveModelPrototxt(modelFile);
        modelFile.deleteOnExit();
      }

      // solver.prototxt
      progressMonitor().initNewTask(
          "Create solver prototxt", progressMonitor().taskProgressMax(), 0);
      Caffe.SolverParameter.Builder sb = Caffe.SolverParameter.newBuilder();
      TextFormat.getParser().merge(model().solverPrototxt, sb);
      sb.setNet(model().modelPrototxtAbsolutePath);
      sb.setBaseLr(((Double)_learningRateTextField.getValue()).floatValue());
      sb.setSnapshot((Integer)_iterationsSpinner.getValue());
      sb.setMaxIter((Integer)_iterationsSpinner.getValue());
      sb.setSnapshotPrefix(processFolder() + "/" + id() + "-snapshot");
      sb.setLrPolicy("fixed");
      sb.setType("Adam");
      sb.setSnapshotFormat(Caffe.SolverParameter.SnapshotFormat.HDF5);

      if (validBlobFileNames.size() != 0) {
        sb.addTestIter(validBlobFileNames.size()).setTestInterval(
            (Integer)_validationStepSpinner.getValue());
      }

      model().solverPrototxtAbsolutePath =
          processFolder() + "/" + id() + "-solver.prototxt";
      model().solverPrototxt = TextFormat.printToString(sb);
      if (sshSession() != null) {
        File tmpFile = File.createTempFile(id(), "-solver.prototxt");
        model().saveSolverPrototxt(tmpFile);
        _createdRemoteFolders.addAll(
            new SftpFileIO(sshSession(), progressMonitor()).put(
                tmpFile, model().solverPrototxtAbsolutePath));
        _createdRemoteFiles.add(model().solverPrototxtAbsolutePath);
        tmpFile.delete();
      }
      else {
        File solverFile =
            new File(model().solverPrototxtAbsolutePath);
        solverFile.createNewFile();
        model().saveSolverPrototxt(solverFile);
        solverFile.deleteOnExit();
      }

      // Finetuning
      progressMonitor().initNewTask("U-Net finetuning", 1.0f, 0);
      runFinetuning();
      if (interrupted()) throw new InterruptedException();

      setReady(true);
    }
    catch (IOException e) {
      IJ.error(id(), "Input/Output error:\n" + e);
      abort();
      return;
    }
    catch (NotImplementedException e) {
      IJ.error(id(), "Sorry, requested feature not implemented:\n" + e);
      abort();
      return;
    }
    catch (JSchException e) {
      IJ.error(id(), "SSH connection failed:\n" + e);
      abort();
      return;
    }
    catch (SftpException e) {
      IJ.error(id(), "SFTP file transfer failed:\n" + e);
      abort();
      return;
    }
    catch (InterruptedException e) {
      abort();
    }
  }

};
