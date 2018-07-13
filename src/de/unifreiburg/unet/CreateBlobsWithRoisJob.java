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

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JCheckBox;

import java.util.Vector;

public class CreateBlobsWithRoisJob extends CreateBlobsJob
    implements PlugIn {

  private ImagePlus _imp = null;
  private final JCheckBox _treatRoiNamesAsClassesCheckBox =
      new JCheckBox(
          "ROI names are classes",
          Prefs.get("unet.finetuning.roiNamesAreClasses", true));

  public CreateBlobsWithRoisJob() {
    super();
  }

  public CreateBlobsWithRoisJob(JobTableModel model) {
    super(model);
  }

  @Override
  protected void createDialogElements() {

    super.createDialogElements();

    _treatRoiNamesAsClassesCheckBox.setToolTipText(
        "Check if your ROI labels indicate class labels");
    _configPanel.add(_treatRoiNamesAsClassesCheckBox);
  }

  @Override
  protected void finalizeDialog() {
    _fromImageButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (model() == null) return;
            model().setElementSizeUm(
                Tools.getElementSizeUmFromCalibration(
                    _imp.getCalibration(), model().nDims()));
          }});

    super.finalizeDialog();
  }

  private boolean getParameters() throws InterruptedException {

    if (WindowManager.getCurrentImage() == null) {
      IJ.noImage();
      return false;
    }
    else _imp = WindowManager.getCurrentImage();

    if (_imp.getOverlay() == null) {
      showMessage(
          "The selected image does not contain an overlay with annotations.");
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
      if (model().nDims() == 3 ||
          _treatRoiNamesAsClassesCheckBox.isSelected()) {
        Vector<String> classNames = new Vector<String>();
        classNames.add("Background");
        for (Roi roi: _imp.getOverlay().toArray()) {
          String roiName = roi.getName();
          if (roiName.matches("[iI][gG][nN][oO][rR][eE](-[0-9]+)*")) continue;
          String className = roiName.replaceFirst("(#[0-9]+)?(-[0-9]+)*$", "");
          if (classNames.contains(className)) continue;
          classNames.add(className);
          IJ.log("  Adding class " + className);
        }
        model().classNames = new String[classNames.size()];
        for (int i = 0; i < classNames.size(); ++i)
            model().classNames[i] = classNames.get(i);
      }
      else model().classNames = new String[] { "Background", "Foreground" };

      progressMonitor().initNewTask(
          "Converting " + _imp.getTitle(), 100.0f, 0);
      ImagePlus dataBlob =
          Tools.convertToUnetFormat(
              _imp, model(), progressMonitor(), true, false);
      dataBlob.show();
      dataBlob.updateAndDraw();
      ImagePlus[] blobs = Tools.convertAnnotationsToLabelsAndWeights(
          _imp, model(), progressMonitor());
      blobs[0].show();
      blobs[1].show();
      blobs[2].show();
      blobs[0].updateAndDraw();
      blobs[1].updateAndDraw();
      blobs[2].updateAndDraw();

      if (interrupted()) throw new InterruptedException();

      setReady(true);
    }
    catch (NotImplementedException e) {
      IJ.error(id(), "Sorry, requested feature not implemented:\n" + e);
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
