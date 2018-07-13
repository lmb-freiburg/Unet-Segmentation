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
import ij.plugin.PlugIn;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.Roi;

import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.GroupLayout;

import java.io.IOException;

import java.util.Vector;

public class CreateBlobsWithImagePairsJob extends CreateBlobsJob
    implements PlugIn {

  private TrainImagePair _imagePair = null;
  private JLabel _trainImagePairText = new JLabel("not selected");
  private JButton _setImagePairButton = new JButton("Choose image pair");
  private final JCheckBox _labelsAreClassesCheckBox =
      new JCheckBox(
          "Labels are classes",
          Prefs.get("unet.finetuning.labelsAreClasses", true));

  public CreateBlobsWithImagePairsJob() {
    super();
  }

  public CreateBlobsWithImagePairsJob(JobTableModel model) {
    super(model);
  }

  @Override
  protected void createDialogElements() {

    super.createDialogElements();

    JLabel trainImagePairLabel = new JLabel("Image Pair:");

    _horizontalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createSequentialGroup()
            .addGroup(
                _dialogLayout.createParallelGroup(
                    GroupLayout.Alignment.TRAILING)
                .addComponent(trainImagePairLabel))
            .addGroup(
                _dialogLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(_trainImagePairText)
                .addComponent(_setImagePairButton)));
    _verticalDialogLayoutGroup
        .addGroup(
            _dialogLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(trainImagePairLabel)
            .addComponent(_trainImagePairText))
        .addComponent(_setImagePairButton);

    _labelsAreClassesCheckBox.setToolTipText(
        "Check if labels indicate classes");
    _configPanel.add(_labelsAreClassesCheckBox);
  }

  @Override
  protected void finalizeDialog() {
    _fromImageButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (model() == null || _imagePair == null) return;
            model().setElementSizeUm(
                Tools.getElementSizeUmFromCalibration(
                    _imagePair.rawdata().getCalibration(), model().nDims()));
          }});

    _setImagePairButton.addActionListener(
        new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          _imagePair = TrainImagePair.selectImagePair();
          if (_imagePair != null)
              _trainImagePairText.setText(_imagePair.toString());
          else _trainImagePairText.setText("not selected");
          _parametersDialog.invalidate();
          _parametersDialog.setMinimumSize(
              _parametersDialog.getPreferredSize());
          _parametersDialog.setMaximumSize(
              new Dimension(
                  Integer.MAX_VALUE,
                  _parametersDialog.getPreferredSize().height));
          _parametersDialog.validate();
        }});

    super.finalizeDialog();
  }

  @Override
  protected boolean checkParameters() throws InterruptedException {

    if (_imagePair == null) {
      showMessage("Please select data and label images.");
      return false;
    }

    return super.checkParameters();
  }

  private boolean getParameters() throws InterruptedException {

    if (WindowManager.getIDList().length < 2) {
      showMessage(
          "Blob creation from image pairs requires two opened images.");
      return false;
    }

    do {
      progressMonitor().initNewTask("Waiting for user input", 0.0f, 0);
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
    start();
  }

  @Override
  public void run() {
    try
    {
      prepareParametersDialog();
      if (isInteractive() && !getParameters()) return;

      // Get class label information from annotations
      progressMonitor().initNewTask(
          "Searching class labels", progressMonitor().taskProgressMax(), 0);
      int maxClassLabel = 0;
      boolean labelsAreClasses = _labelsAreClassesCheckBox.isSelected();
      if (labelsAreClasses) {
        ImagePlus imp = _imagePair.rawlabels();
        if (imp.getBitDepth() == 8) {
          Byte[][] data = (Byte[][])imp.getStack().getImageArray();
          for (int i = 0; i < imp.getStack().getSize(); ++i)
              for (int j = 0; j < data[i].length; j++)
                  if (data[i][j] > maxClassLabel)
                      maxClassLabel = data[i][j];
        }
        else if (imp.getBitDepth() == 16)
        {
          Short[][] data = (Short[][])imp.getStack().getImageArray();
          for (int i = 0; i < imp.getStack().getSize(); ++i)
              for (int j = 0; j < data[i].length; j++)
                  if (data[i][j] > maxClassLabel)
                      maxClassLabel = data[i][j];
        }
        if (maxClassLabel < 2) {
          IJ.error("Label images contain no valid annotations.\n" +
                   "Please make sure your labeling has the following " +
                   "format:\n" +
                   "0 - ignore, 1 - background, >1 foreground classes");
          throw new InterruptedException();
        }
      }
      else maxClassLabel = 1;

      model().classNames = new String[maxClassLabel + 1];
      model().classNames[0] = "Background";
      for (int i = 1; i <= maxClassLabel; ++i)
          model().classNames[i] = "Class " + i;

      _imagePair.createCaffeBlobs(
          labelsAreClasses ? model().classNames : null, model(),
          progressMonitor());

      _imagePair.data().show();
      _imagePair.data().updateAndDraw();
      _imagePair.labels().show();
      _imagePair.weights().show();
      _imagePair.samplePdf().show();
      _imagePair.labels().updateAndDraw();
      _imagePair.weights().updateAndDraw();
      _imagePair.samplePdf().updateAndDraw();

      if (interrupted()) throw new InterruptedException();

      setReady(true);
    }
    catch (NotImplementedException e) {
      IJ.error(id(), "Sorry, requested feature not implemented:\n" + e);
      abort();
      return;
    }
    catch (IOException e) {
      IJ.error(id(), "Input/Output error:\n" + e);
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

}
