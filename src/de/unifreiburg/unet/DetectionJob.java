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
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JDialog;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

public class DetectionJob extends SegmentationJob implements PlugIn {

  public DetectionJob() {
    super();
  }

  public DetectionJob(JobTableModel model) {
    super(model);
  }

  @Override
  public void finish() {
    if (progressMonitor().finished()) return;
    readyCancelButton().setEnabled(false);
    try {
      Thread finishThread = new Thread() {
            @Override
            public void run() {
              try {
                loadSegmentationToImagePlus();
                if (Recorder.record) {
                  Recorder.setCommand(null);
                  String command =
                      "call('de.unifreiburg.unet.DetectionJob." +
                      "processHyperStack', '" +
                      model().getMacroParameterString() +
                      ",weightsFilename=" +
                      weightsFileName().replace("\\", "/") +
                      ",gpuId=" + selectedGPUString() +
                      "," + hostConfiguration().getMacroParameterString() +
                      ",processFolder=" + processFolder() +
                      ",average=" +
                      (String)_averagingComboBox.getSelectedItem() +
                      ",keepOriginal=" + String.valueOf(
                          _keepOriginalCheckBox.isSelected()) +
                      ",outputScores=" + String.valueOf(
                          _outputScoresCheckBox.isSelected()) +
                      ",outputSoftmaxScores=" + String.valueOf(
                          _outputSoftmaxScoresCheckBox.isSelected()) + "');\n";
                  Recorder.recordString(command);
                }
              }
              catch (IOException e) {
                showError("Could not load detection result", e);
              }
              finishJob();
            }
          };
      if (isInteractive()) finishThread.start();
      else finishThread.run();
    }
    catch (IllegalThreadStateException e) {}
  }

  public static void processHyperStack(String params)
      throws InterruptedException {
    final DetectionJob job = new DetectionJob();
    if (job._imp == null)
        job.setImagePlus(WindowManager.getCurrentImage());
    if (job._imp == null) {
      IJ.noImage();
      return;
    }

    String[] parameterStrings = params.split(",");
    Map<String,String> parameters = new HashMap<String,String>();
    for (int i = 0; i < parameterStrings.length; i++) {
      String[] param = parameterStrings[i].split("=");
      parameters.put(param[0], (param.length > 1) ? param[1] : "");
    }
    job.setModel(new ModelDefinition(job, parameters));
    job.setWeightsFileName(parameters.get("weightsFilename"));
    job.setGPUString(parameters.get("gpuId"));
    try
    {
      job.hostConfiguration().connectFromParameterMap(parameters);
    }
    catch (JSchException e) {
      IJ.log("Macro call to SegmentationJob.processHyperStack aborted. " +
             "Could not establish SSH connection.");
      IJ.error("U-Net Segmentation", "Could not establish SSH connection.");
      return;
    }
    job.setProcessFolder(parameters.get("processFolder"));
    job._keepOriginalCheckBox.setSelected(
        Boolean.valueOf(parameters.get("keepOriginal")));
    job._outputScoresCheckBox.setSelected(
        Boolean.valueOf(parameters.get("outputScores")));
    job._outputSoftmaxScoresCheckBox.setSelected(
        Boolean.valueOf(parameters.get("outputSoftmaxScores")));
    job.setInteractive(false);

    // Run blocking on current thread
    job.run();
  }

};
