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

import ij.plugin.PlugIn;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;
import ij.process.ShortProcessor;
import ij.Prefs;
import ij.gui.Roi;
import ij.gui.ImageRoi;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.GroupLayout;

import java.io.IOException;

import java.util.Vector;

public class CreateBlobsJob extends Job implements PlugIn {

  private final JPanel _elSizePanel = new JPanel(new BorderLayout());
  protected final JButton _fromImageButton = new JButton("from Image");

  private ImagePlus _imp = null;
  private final JCheckBox _labelsAreClassesCheckBox =
      new JCheckBox("Labels are classes",
                    Prefs.get("unet.finetuning.labelsAreClasses", true));

  public CreateBlobsJob() {
    super();
  }

  public CreateBlobsJob(JobTableModel model) {
    super(model);
  }

  @Override
  protected void processModelSelectionChange() {
    _elSizePanel.removeAll();
    if (model() != null) {
      _elSizePanel.add(model().elementSizeUmPanel());
      _elSizePanel.setMinimumSize(
          model().elementSizeUmPanel().getMinimumSize());
      _elSizePanel.setMaximumSize(
          new Dimension(
              Integer.MAX_VALUE,
              model().elementSizeUmPanel().getPreferredSize().height));
    }
    super.processModelSelectionChange();
  }

  @Override
  protected void createDialogElements() {

    super.createDialogElements();

    _parametersDialog.setTitle("U-Net Blob Creation");

    JLabel elSizeLabel = new JLabel("Element Size [µm]:");
    _fromImageButton.setToolTipText(
        "Use native image element size for finetuning");

    _horizontalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createSequentialGroup()
            .addComponent(elSizeLabel)
            .addComponent(_elSizePanel)
            .addComponent(_fromImageButton));
    _verticalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(elSizeLabel)
            .addComponent(_elSizePanel)
            .addComponent(_fromImageButton));

    _labelsAreClassesCheckBox.setToolTipText(
        "Check if your labels indicate class labels");
    _configPanel.add(_labelsAreClassesCheckBox);
  }

  @Override
  protected void finalizeDialog() {
    _fromImageButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (model() == null) return;
            model().setElementSizeUm(Tools.getElementSizeUm(_imp));
          }});
    super.finalizeDialog();
  }

  private boolean getParameters() throws InterruptedException {

    _imp = IJ.getImage();

    if (_imp == null) {
      IJ.noImage();
      return false;
    }

    if (_imp.getOverlay() == null) {
      showMessage(
          "The selected image does not contain an overlay with annotations.");
      return false;
    }

    do {
      progressMonitor().count("Waiting for user input", 0);
      _parametersDialog.setVisible(true);

      // Dialog was cancelled
      if (!_parametersDialog.isDisplayable())
          throw new InterruptedException("Dialog canceled");
    }
    while (!checkParameters());

    _parametersDialog.dispose();

    if (jobTable() != null) jobTable().fireTableDataChanged();

    return true;
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
      prepareParametersDialog();
      if (isInteractive() && !getParameters()) return;

      // Get class label information from annotations
      progressMonitor().push("Searching class labels", 0.0f, 0.1f);

      boolean labelsAreClasses = _labelsAreClassesCheckBox.isSelected();

      int maxClassLabel = 0;
      Vector<String> classNames = new Vector<String>();
      classNames.add("Background");
      for (Roi roi: _imp.getOverlay().toArray()) {
        if (roi instanceof ImageRoi) {
          ImageProcessor ip = ((ImageRoi)roi).getProcessor();
          if (ip instanceof ByteProcessor) {
            byte[] data = (byte[])ip.getPixels();
            for (int i = 0; i < data.length; ++i) {
              if (data[i] > maxClassLabel) {
                maxClassLabel = data[i];
                if (labelsAreClasses) IJ.log("  Adding class " + maxClassLabel);
              }
            }
          }
          else if (ip instanceof ShortProcessor) {
            short[] data = (short[])ip.getPixels();
            for (int i = 0; i < data.length; ++i) {
              if (data[i] > maxClassLabel) {
                maxClassLabel = data[i];
                if (labelsAreClasses) IJ.log("  Adding class " + maxClassLabel);
              }
            }
          }
        }
        else {
          String roiName = roi.getName();
          if (roiName.matches("[iI][gG][nN][oO][rR][eE](-[0-9]+)*")) continue;
          String className =
              roiName.replaceFirst("(#[0-9]+)?(-[0-9]+)*$", "");
          if (classNames.contains(className)) continue;
          classNames.add(className);
          if (labelsAreClasses) IJ.log("  Adding class " + className);
        }
      }

      boolean imagesContainMaskAnnotations = (maxClassLabel > 0);
      boolean imagesContainRoiAnnotations = (classNames.size() > 1);

      // If we have no valid annotations of any type, give up
      if (!imagesContainMaskAnnotations && !imagesContainRoiAnnotations) {
        showMessage(
            "Training image does not contain valid annotations.\n" +
            "Please make sure that your ROI annotations are named " +
            "<classname>[#<instance>] or \n" +
            "that segmentation masks are embedded as Overlay");
        throw new InterruptedException();
      }

      // If we have a mix of annotation types, give up
      if (imagesContainMaskAnnotations && imagesContainRoiAnnotations) {
        showMessage(
            "Training image contains a mix of ROI and mask annotations.\n" +
            "This is currently not supported, please convert either all " +
            "ROIs to masks or vice-versa.");
        throw new InterruptedException();
      }

      if (labelsAreClasses) {
        if (imagesContainRoiAnnotations) {
          model().classNames = new String[classNames.size()];
          for (int i = 0; i < classNames.size(); ++i)
              model().classNames[i] = classNames.get(i);
        }
        else {
          model().classNames = new String[maxClassLabel];
          model().classNames[0] = "Background";
          for (int i = 2; i <= maxClassLabel; ++i)
              model().classNames[i - 1] = "Class " + (i - 1);
        }
      }
      else model().classNames = new String[] { "Background", "Foreground" };

      progressMonitor().pop();

      TrainingSample t = new TrainingSample(_imp);
      progressMonitor().push("Converting data", 0.1f, 0.2f);
      t.createDataBlob(model(), progressMonitor());
      if (interrupted()) throw new InterruptedException();
      progressMonitor().pop();
      progressMonitor().push("Converting labels", 0.2f, 1.0f);
      t.createLabelsAndWeightsBlobs(
          model(), labelsAreClasses, progressMonitor());
      t.dataBlob().show();
      t.dataBlob().updateAndDraw();
      t.labelBlob().show();
      t.labelBlob().updateAndDraw();
      t.weightBlob().show();
      t.weightBlob().updateAndDraw();
      t.samplePdfBlob().show();
      t.samplePdfBlob().updateAndDraw();

      if (interrupted()) throw new InterruptedException();
      progressMonitor().end();
      setReady(true);
    }
    catch (TrainingSampleException e) {
      showError("Invalid Training Sample", e);
      abort();
    }
    catch (BlobException e) {
      showError("Blob conversion failed", e);
      abort();
    }
    catch (InterruptedException e) {
      abort();
    }
  }

};
