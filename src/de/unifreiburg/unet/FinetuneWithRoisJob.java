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
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.gui.PointRoi;
import ij.gui.ImageRoi;

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

public class FinetuneWithRoisJob extends FinetuneJob implements PlugIn {

  private final ImagePlusListView _trainFileList = new ImagePlusListView();
  private final ImagePlusListView _validFileList = new ImagePlusListView();

  private final JPanel _trainImagesPanel = new JPanel(new BorderLayout());
  private final JPanel _validImagesPanel = new JPanel(new BorderLayout());
  private final JSplitPane _trainValidPane = new JSplitPane(
      JSplitPane.HORIZONTAL_SPLIT, _trainImagesPanel, _validImagesPanel);

  private final JCheckBox _treatRoiNamesAsClassesCheckBox =
      new JCheckBox(
          "ROI names are classes",
          Prefs.get("unet.finetuning.roiNamesAreClasses", true));

  public FinetuneWithRoisJob() {
    super();
  }

  public FinetuneWithRoisJob(JobTableModel model) {
    super(model);
  }

  @Override
  protected void createDialogElements() {

    super.createDialogElements();

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
    trainScroller.setMinimumSize(new Dimension(100, 50));
    _trainImagesPanel.add(trainScroller, BorderLayout.CENTER);

    JLabel validImagesLabel = new JLabel("Validation images");
    _validImagesPanel.add(validImagesLabel, BorderLayout.NORTH);
    JScrollPane validScroller = new JScrollPane(_validFileList);
    validScroller.setMinimumSize(new Dimension(100, 50));
    _validImagesPanel.add(validScroller, BorderLayout.CENTER);

    _horizontalDialogLayoutGroup.addComponent(_trainValidPane);
    _verticalDialogLayoutGroup.addComponent(_trainValidPane);

    _treatRoiNamesAsClassesCheckBox.setToolTipText(
        "Check if your ROI labels indicate class labels");
    _configPanel.add(_treatRoiNamesAsClassesCheckBox);
  }

  @Override
  protected void finalizeDialog() {
    _trainValidPane.setDividerLocation(0.5);

    _fromImageButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (model() == null) return;
            if (_trainFileList.getModel().getSize() == 0) return;
            ImagePlus imp = _trainFileList.getModel().getElementAt(0);
            model().setElementSizeUm(
                Tools.getElementSizeUmFromCalibration(
                    imp.getCalibration(), model().nDims()));
          }});

    super.finalizeDialog();
  }

  @Override
  protected boolean checkParameters() throws InterruptedException {

    progressMonitor().count("Checking parameters", 0);

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
          _treatRoiNamesAsClassesCheckBox.isSelected()) {
        for (Roi roi : imp.getOverlay().toArray()) {
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

    Prefs.set("unet.finetuning.roiNamesAreClasses",
              _treatRoiNamesAsClassesCheckBox.isSelected());

    return super.checkParameters();
  }

  @Override
  public void run(String arg) {
    start();
  }

  @Override
  public void run() {
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
      abort();
      return;
    }
    try
    {
      prepareParametersDialog();
      if (isInteractive() && !getParameters()) return;

      int nTrainImages = _trainFileList.getModel().getSize();
      int nValidImages = _validFileList.getModel().getSize();
      int nImages = nTrainImages + nValidImages;

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
      if (model().nDims() == 3 ||
          _treatRoiNamesAsClassesCheckBox.isSelected()) {
        Vector<String> classNames = new Vector<String>();
        classNames.add("Background");
        for (Object o : ((DefaultListModel<ImagePlus>)
                         _trainFileList.getModel()).toArray()) {
          ImagePlus imp = (ImagePlus)o;
          for (Roi roi: imp.getOverlay().toArray()) {
            String roiName = roi.getName();
            if (roiName.matches("[iI][gG][nN][oO][rR][eE](-[0-9]+)*")) continue;
            String className = roiName.replaceFirst(
                "(#[0-9]+)?(-[0-9]+)*$", "");
            if (classNames.contains(className)) continue;
            classNames.add(className);
            IJ.log("  Adding class " + className);
          }
        }
        for (Object o : ((DefaultListModel<ImagePlus>)
                         _validFileList.getModel()).toArray()) {
          ImagePlus imp = (ImagePlus)o;
          for (Roi roi: imp.getOverlay().toArray()) {
            String roiName = roi.getName();
            if (roiName.matches("[iI][gG][nN][oO][rR][eE](-[0-9]+)*")) continue;
            String className = roiName.replaceFirst(
                "(#[0-9]+)?(-[0-9]+)*$", "");
            if (classNames.contains(className)) continue;
            classNames.add(className);
            IJ.log("  Adding class " + className);
            String msg = "WARNING: Training set does not contain instances " +
                "of class " + className + ". This class will not be learnt!";
            IJ.log(msg);
            IJ.showMessage(msg);
          }
        }
        _finetunedModel.classNames = new String[classNames.size()];
        for (int i = 0; i < classNames.size(); ++i)
            _finetunedModel.classNames[i] = classNames.get(i);
      }
      else _finetunedModel.classNames = new String[] {
              "Background", "Foreground" };

      // Process train files
      for (int i = 0; i < nTrainImages; i++) {
        ImagePlus imp =
            ((DefaultListModel<ImagePlus>)_trainFileList.getModel()).get(i);
        progressMonitor().initNewTask(
            "Converting " + imp.getTitle(),
            0.05f * ((float)i + ((sshSession() == null) ? 1.0f : 0.5f)) /
            (float)nImages, 0);
        trainBlobFileNames[i] = processFolder() + id() + "_train_" + i + ".h5";
        if (sshSession() == null) outfile = new File(trainBlobFileNames[i]);
        Tools.saveHDF5Blob(
            imp, outfile, _finetunedModel, progressMonitor(), true, true,
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
            processFolder() + id() + "_valid_" + i : null;
        File[] generatedFiles = Tools.saveHDF5TiledBlob(
            imp, fileNameStub, _finetunedModel, progressMonitor());

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
                processFolder() + id() + "_valid_" + i + "_" + j + ".h5";
            _createdRemoteFolders.addAll(
                new SftpFileIO(sshSession(), progressMonitor()).put(
                    generatedFiles[j], outFileName));
            _createdRemoteFiles.add(outFileName);
            validBlobFileNames.add(outFileName);
            if (interrupted()) throw new InterruptedException();
          }
        }
      }

      prepareFinetuning(trainBlobFileNames, validBlobFileNames);

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
    catch (BlobException e) {
      IJ.error(id(), "Blob conversion failed:\n" + e);
      abort();
    }
  }

};
